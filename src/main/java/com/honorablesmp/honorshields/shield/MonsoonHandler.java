package com.honorablesmp.honorshields.shield;

import com.honorablesmp.honorshields.classsystem.EffectStacking;
import com.honorablesmp.honorshields.classsystem.TrustManager;
import com.honorablesmp.honorshields.data.HonorPlayerData;
import com.honorablesmp.honorshields.network.HonorShieldsPackets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.phys.Vec3;

/** Server-owned Wet, Whirlpool, and ally-healing behavior for the Monsoon Shield. */
public final class MonsoonHandler {
	private static final long MAELSTROM_COOLDOWN = 6_000L;
	private static final long BLOCK_WAVE_COOLDOWN = 600L;
	private record WetKey(UUID owner, UUID target) { }
	private record Wet(int stacks, long nextDecay) { }
	private static final class Whirlpool {
		final UUID owner;
		final ServerLevel level;
		final UUID trigger;
		final Vec3 center;
		final boolean maelstrom;
		final long endsAt;
		final long fadesAt;
		long nextPulse;
		final Set<UUID> caughtPlayers = new HashSet<>();

		Whirlpool(UUID owner, ServerLevel level, UUID trigger, Vec3 center, boolean maelstrom, long now) {
			this.owner = owner; this.level = level; this.trigger = trigger; this.center = center; this.maelstrom = maelstrom;
			this.endsAt = now + (maelstrom ? 100L : 40L);
			this.fadesAt = this.endsAt + 40L;
			this.nextPulse = now;
		}
	}

	private static final Map<WetKey, Wet> WET = new HashMap<>();
	private static final List<Whirlpool> WHIRLPOOLS = new ArrayList<>();
	private static final Map<UUID, Long> MAELSTROM_READY = new HashMap<>();
	private static final Map<UUID, Long> BLOCK_WAVE_READY = new HashMap<>();
	private static final Map<UUID, Float> NEXT_HEAL_BONUS = new HashMap<>();
	private static long nextHealingSpringTick;

	/** Called after a successful non-ability hit; tridents supply their own three stacks. */
	public static void onSuccessfulAttack(LivingEntity target, DamageSource source) {
		if (AbilityDamage.isAbility(source) || source.getDirectEntity() instanceof ThrownTrident
			|| !(source.getEntity() instanceof ServerPlayer owner) || !active(owner) || !ShieldResourceManager.isEnemy(owner, target)) return;
		addWet(owner, target, 1);
	}

	/** Tidal Trident grants exactly three stacks and drags each pierced enemy toward its owner. */
	public static void onTridentHit(ServerPlayer owner, LivingEntity target) {
		if (!active(owner) || !ShieldResourceManager.isEnemy(owner, target)) return;
		addWet(owner, target, 3);
		pull(owner.position(), target, 0.38D);
		HonorShieldsPackets.abilityEffect(owner, ShieldType.MONSOON, 0, target, 2);
	}

	private static void addWet(ServerPlayer owner, LivingEntity target, int amount) {
		ServerLevel level = (ServerLevel) owner.level();
		long now = level.getGameTime();
		WetKey key = new WetKey(owner.getUUID(), target.getUUID());
		Wet previous = WET.get(key);
		int stacks = Math.min(10, (previous == null ? 0 : previous.stacks()) + amount);
		WET.put(key, new Wet(stacks, now + 100L));
		// Each successful stack has visible confirmation; players no longer have to
		// guess whether ten otherwise silent hits have registered.
		HonorShieldsPackets.abilityEffect(owner, ShieldType.MONSOON, 0, target, 2);
		if (stacks > 8 && stacks < 10) EffectStacking.applyOnce(target, MobEffects.GLOWING, 110, 0);
		if (stacks >= 10) startWhirlpool(owner, target, now);
	}

	private static void startWhirlpool(ServerPlayer owner, LivingEntity trigger, long now) {
		if (WHIRLPOOLS.stream().anyMatch(pool -> pool.owner.equals(owner.getUUID()) && pool.trigger.equals(trigger.getUUID()))) return;
		HonorPlayerData data = (HonorPlayerData) owner;
		boolean maelstrom = data.honorshields$getShieldCondition() == ShieldCondition.EXALTED
			&& now >= MAELSTROM_READY.getOrDefault(owner.getUUID(), 0L);
		if (maelstrom) {
			MAELSTROM_READY.put(owner.getUUID(), now + MAELSTROM_COOLDOWN);
			ShieldResourceManager.exaltedPassiveCooldownStarted(owner);
		}
		Whirlpool pool = new Whirlpool(owner.getUUID(), (ServerLevel) owner.level(), trigger.getUUID(),
			trigger.position(), maelstrom, now);
		if (maelstrom) for (LivingEntity enemy : ShieldResourceManager.enemies(owner, pool.center, 7.0))
			if (enemy instanceof ServerPlayer caught) pool.caughtPlayers.add(caught.getUUID());
		WHIRLPOOLS.add(pool);
		HonorShieldsPackets.abilityEffectFrom(owner, ShieldType.MONSOON, 3, null, 0, pool.center);
	}

	public static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		tickWet(server, now);
		if (now >= nextHealingSpringTick) {
			tickHealingSpring(server);
			nextHealingSpringTick = now + 20L;
		}
		Iterator<Whirlpool> iterator = WHIRLPOOLS.iterator();
		while (iterator.hasNext()) {
			Whirlpool pool = iterator.next();
			ServerPlayer owner = server.getPlayerList().getPlayer(pool.owner);
			if (owner == null || owner.level() != pool.level || now >= pool.fadesAt) {
				finishWhirlpool(pool, owner);
				iterator.remove();
				continue;
			}
			if (now >= pool.endsAt) continue; // visual fade only; no lingering damage or pull.
			if (now >= pool.nextPulse) {
				pulse(pool, owner, now);
				pool.nextPulse = now + 20L;
			}
		}
	}

	/** Healing Spring is intentionally water-bound: it is support utility, not free land regeneration. */
	private static void tickHealingSpring(MinecraftServer server) {
		for (ServerPlayer owner : server.getPlayerList().getPlayers()) {
			if (!active(owner) || !owner.isInWater()) continue;
			for (ServerPlayer ally : trustedPlayers(owner, owner.position(), 5.0, false)) {
				if (ally.isInWater()) EffectStacking.applyContinuous(ally, MobEffects.REGENERATION, 40, 1,
					"monsoon_healing_spring_" + owner.getUUID());
			}
		}
	}

	private static void tickWet(MinecraftServer server, long now) {
		Iterator<Map.Entry<WetKey, Wet>> iterator = WET.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<WetKey, Wet> entry = iterator.next();
			Wet wet = entry.getValue();
			ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey().owner());
			if (owner == null || !active(owner)) { iterator.remove(); continue; }
			var entity = ((ServerLevel) owner.level()).getEntityInAnyDimension(entry.getKey().target());
			if (!(entity instanceof LivingEntity target) || !target.isAlive() || !ShieldResourceManager.isEnemy(owner, target)) { iterator.remove(); continue; }
			if (now < wet.nextDecay()) continue;
			int stacks = wet.stacks() - 1;
			if (stacks <= 0) iterator.remove();
			else {
				entry.setValue(new Wet(stacks, now + 100L));
				if (stacks > 8) EffectStacking.applyOnce(target, MobEffects.GLOWING, 110, 0);
			}
		}
	}

	private static void pulse(Whirlpool pool, ServerPlayer owner, long now) {
		double radius = pool.maelstrom ? 7.0 : 3.0;
		float pulseDamage = pool.maelstrom ? 2.0F : 2.0F; // 5 or 2 true hearts over the active lifetime.
		for (LivingEntity enemy : ShieldResourceManager.enemies(owner, pool.center, radius)) {
			pull(pool.center, enemy, pool.maelstrom ? 0.36D : 0.18D);
			EffectStacking.applyOnce(enemy, MobEffects.SLOWNESS, pool.maelstrom ? 300 : 100, pool.maelstrom ? 1 : 0);
			enemy.hurtServer(pool.level, AbilityDamage.source(pool.level, owner,
				pool.maelstrom ? AbilityDamage.Kind.MONSOON_MAELSTROM : AbilityDamage.Kind.MONSOON_WHIRLPOOL),
				enemy instanceof Mob ? pulseDamage * 2.0F : pulseDamage);
			if (pool.maelstrom && enemy instanceof ServerPlayer caught) pool.caughtPlayers.add(caught.getUUID());
		}
		if (pool.maelstrom) {
			for (ServerPlayer ally : trustedPlayers(owner, pool.center, radius, true)) {
				EffectStacking.applyOnce(ally, MobEffects.REGENERATION, 40, 1);
				cleanseDebuffs(ally);
			}
		}
		HonorShieldsPackets.abilityEffectFrom(owner, ShieldType.MONSOON, 3, null, 1, pool.center);
	}

	private static void finishWhirlpool(Whirlpool pool, ServerPlayer owner) {
		// Wet stacks belong to the Monsoon owner as well as their target. Removing
		// by target alone would erase another player's independent stacks.
		WET.entrySet().removeIf(entry -> entry.getKey().owner().equals(pool.owner)
			&& entry.getKey().target().equals(pool.trigger));
		if (owner == null || !pool.maelstrom) return;
		NEXT_HEAL_BONUS.merge(owner.getUUID(), pool.caughtPlayers.size() * 4.0F, Float::sum);
		for (LivingEntity enemy : ShieldResourceManager.enemies(owner, pool.center, 7.0)) pushAway(pool.center, enemy, 10.0D);
		HonorShieldsPackets.abilityEffectFrom(owner, ShieldType.MONSOON, 3, null, 2, pool.center);
	}

	/** Healing Waters: four hearts to caster, three to nearby trusted allies, then one Life Current bounce per direct heal. */
	public static void healingWaters(ServerPlayer owner) {
		Float storedBonus = NEXT_HEAL_BONUS.remove(owner.getUUID());
		float bonus = storedBonus == null ? 0.0F : storedBonus;
		// The Maelstrom bonus is consumed by this next healing move, exactly once.
		float multiplier = ((HonorPlayerData) owner).honorshields$getShieldCondition().abilityMultiplier();
		owner.heal(8.0F * multiplier + bonus);
		healAllies(owner, 10.0, 6.0F * multiplier + bonus);
		EffectStacking.applyOnce(owner, MobEffects.REGENERATION, 100, 0);
	}

	/** The replacement successful-block effect: never invokes Healing Waters or its cooldown. */
	public static boolean blockTidalWave(ServerPlayer owner) {
		if (!active(owner)) return false;
		long now = ((ServerLevel) owner.level()).getGameTime();
		if (now < BLOCK_WAVE_READY.getOrDefault(owner.getUUID(), 0L)) return true;
		BLOCK_WAVE_READY.put(owner.getUUID(), now + BLOCK_WAVE_COOLDOWN);
		ServerLevel level = (ServerLevel) owner.level();
		for (LivingEntity enemy : ShieldResourceManager.enemies(owner, owner.position(), 6.0)) {
			enemy.hurtServer(level, AbilityDamage.source(level, owner, AbilityDamage.Kind.MONSOON_TIDAL_WAVE),
				enemy instanceof Mob ? 16.0F : 8.0F);
			pushAway(owner.position(), enemy, 2.4D);
		}
		healAllies(owner, 6.0, 2.0F);
		HonorShieldsPackets.abilityEffect(owner, ShieldType.MONSOON, 1, null, 0);
		return true;
	}

	private static void healAllies(ServerPlayer owner, double radius, float amount) {
		Set<UUID> bounced = new HashSet<>();
		for (ServerPlayer ally : trustedPlayers(owner, owner.position(), radius, false)) {
			ally.heal(amount);
			ServerPlayer bounce = trustedPlayers(owner, ally.position(), 10.0, false).stream()
				.filter(candidate -> candidate != ally && bounced.add(candidate.getUUID())).findFirst().orElse(null);
			if (bounce != null) bounce.heal(amount * 0.5F);
		}
	}

	private static List<ServerPlayer> trustedPlayers(ServerPlayer owner, Vec3 center, double radius, boolean includeOwner) {
		return ((ServerLevel) owner.level()).getPlayers(candidate -> candidate.position().distanceToSqr(center) <= radius * radius
			&& (includeOwner && candidate == owner || candidate != owner && TrustManager.isMutualTrust(owner, candidate)));
	}

	private static void cleanseDebuffs(ServerPlayer player) {
		for (MobEffectInstance effect : List.copyOf(player.getActiveEffects()))
			if (!effect.getEffect().value().isBeneficial()) player.removeEffect(effect.getEffect());
	}

	private static void pull(Vec3 center, LivingEntity target, double force) {
		if (ShieldBlockingHandler.blocksKnockback(target)) return;
		Vec3 horizontal = center.subtract(target.position()).multiply(1.0, 0.0, 1.0);
		if (horizontal.lengthSqr() < 1.0E-6) return;
		Vec3 motion = horizontal.normalize().scale(force);
		target.setDeltaMovement(target.getDeltaMovement().add(motion.x, 0.02, motion.z));
		target.hurtMarked = true;
	}

	private static void pushAway(Vec3 center, LivingEntity target, double force) {
		if (ShieldBlockingHandler.blocksKnockback(target)) return;
		Vec3 horizontal = target.position().subtract(center).multiply(1.0, 0.0, 1.0);
		if (horizontal.lengthSqr() < 1.0E-6) horizontal = new Vec3(1.0, 0.0, 0.0);
		Vec3 motion = horizontal.normalize().scale(force);
		target.setDeltaMovement(target.getDeltaMovement().add(motion.x, 0.45, motion.z));
		target.hurtMarked = true;
	}

	private static boolean active(ServerPlayer player) { return ShieldResourceManager.activeShield(player) == ShieldType.MONSOON; }

	public static int maelstromCooldownSeconds(ServerPlayer player) {
		long now = ((ServerLevel) player.level()).getGameTime();
		return (int) Math.ceil(Math.max(0L, MAELSTROM_READY.getOrDefault(player.getUUID(), 0L) - now) / 20.0);
	}

	/** An Exalted transition arms Maelstrom immediately, including after a prior five-minute use. */
	public static void resetMaelstromCooldown(ServerPlayer player) {
		MAELSTROM_READY.remove(player.getUUID());
	}

	public static void resetPlayer(ServerPlayer player) {
		UUID id = player.getUUID();
		MAELSTROM_READY.remove(id); BLOCK_WAVE_READY.remove(id); NEXT_HEAL_BONUS.remove(id);
		WET.entrySet().removeIf(entry -> entry.getKey().owner().equals(id) || entry.getKey().target().equals(id));
		WHIRLPOOLS.removeIf(pool -> pool.owner.equals(id) || pool.trigger.equals(id));
	}

	private MonsoonHandler() { }
}
