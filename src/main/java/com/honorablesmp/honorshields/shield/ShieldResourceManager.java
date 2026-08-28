package com.honorablesmp.honorshields.shield;

import com.honorablesmp.honorshields.classsystem.EffectStacking;
import com.honorablesmp.honorshields.classsystem.TrustManager;
import com.honorablesmp.honorshields.data.HonorPlayerData;
import com.honorablesmp.honorshields.network.HonorShieldsPackets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;

/** Server-owned rechargeable shield meters and their tightly coupled actions. */
public final class ShieldResourceManager {
	private static final long DAWN_LAST_CHANCE_TICKS = 20L * 60L * 20L;
	private static final Map<UUID, Timers> TIMERS = new HashMap<>();
	private static final Map<UUID, Snapshot> LAST_SENT = new HashMap<>();
	private static final Map<UUID, Scream> WARDEN_SCREAMS = new HashMap<>();
	private static final Map<UUID, Long> WARDEN_LAST_DAMAGE = new HashMap<>();
	private static final Map<UUID, Long> WARDEN_NEXT_DECAY = new HashMap<>();
	private static final Set<UUID> DASH_COMBO_HELD = new HashSet<>();

	private static final class Timers {
		long thunder;
		long tempest;
		long dawn;
		long voidCharge;
	}
	private record Snapshot(String kind, int current, int maximum, boolean armed) {}
	private record Scream(UUID target, long firesAt, long completesAt, boolean fired) {}

	public static void registerEvents() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register(ShieldResourceManager::afterDamage);
		ServerLivingEntityEvents.ALLOW_DEATH.register(ShieldResourceManager::allowDeath);
	}

	public static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			clamp(player);
			tickWardenScream(player, now);
			tickShieldDash(player);
			if (now % 20L != 0L) continue;
			Timers timers = TIMERS.computeIfAbsent(player.getUUID(), ignored -> {
				Timers created = new Timers(); created.thunder = created.tempest = created.dawn = created.voidCharge = now; return created;
			});
			ShieldType shield = activeShield(player);
			if (shield == ShieldType.THUNDER) tickThunder(player, timers, now);
			else timers.thunder = now;
			if (shield == ShieldType.TEMPEST) tickTempest(player, timers, now);
			else timers.tempest = now;
			if (shield == ShieldType.DAWN) tickDawn(player, timers, now);
			else timers.dawn = now;
			if (shield == ShieldType.VOID) tickVoid(player, timers, now);
			else timers.voidCharge = now;
			tickWardenDecay(player, now);
			sync(player, false);
		}
		TIMERS.keySet().retainAll(server.getPlayerList().getPlayers().stream().map(ServerPlayer::getUUID).toList());
		LAST_SENT.keySet().retainAll(TIMERS.keySet());
		DASH_COMBO_HELD.retainAll(TIMERS.keySet());
	}

	private static void tickThunder(ServerPlayer player, Timers timers, long now) {
		HonorPlayerData data = (HonorPlayerData) player;
		int maximum = thunderMaximum(player);
		if (data.honorshields$getThunderCharge() >= maximum) { timers.thunder = now; return; }
		// Static Charge is deliberately independent of weather or exposure: the
		// current authored rule is exactly one charge every ten seconds.
		long interval = 10L * 20L;
		if (now - timers.thunder >= interval) {
			data.honorshields$setThunderCharge(Math.min(maximum, data.honorshields$getThunderCharge() + 1));
			timers.thunder = now;
			HonorShieldsPackets.abilityEffect(player, ShieldType.THUNDER, 0, null, 1);
		}
	}

	private static void tickVoid(ServerPlayer player, Timers timers, long now) {
		HonorPlayerData data = (HonorPlayerData) player;
		if (data.honorshields$getVoidCharge() >= 3) { timers.voidCharge = now; return; }
		ServerLevel level = (ServerLevel) player.level();
		long dayTime = level.getOverworldClockTime() % 24_000L;
		boolean night = dayTime >= 13_000L && dayTime < 23_000L;
		boolean accelerated = night || !level.canSeeSky(player.blockPosition());
		long interval = accelerated ? 5L * 20L : 10L * 20L;
		if (now - timers.voidCharge >= interval) {
			data.honorshields$setVoidCharge(data.honorshields$getVoidCharge() + 1);
			timers.voidCharge = now;
			HonorShieldsPackets.abilityEffect(player, ShieldType.VOID, 0, null, 1);
		}
	}

	private static boolean darkness(ServerPlayer player) {
		return player.level().getMaxLocalRawBrightness(player.blockPosition()) < 8;
	}

	private static void tickTempest(ServerPlayer player, Timers timers, long now) {
		HonorPlayerData data = (HonorPlayerData) player;
		int maximum = tempestMaximum(player);
		if (data.honorshields$getTempestCharge() >= maximum) { timers.tempest = now; return; }
		if (now - timers.tempest >= 5L * 20L) {
			data.honorshields$setTempestCharge(Math.min(maximum, data.honorshields$getTempestCharge() + 1));
			timers.tempest = now;
		}
	}

	private static void tickDawn(ServerPlayer player, Timers timers, long now) {
		HonorPlayerData data = (HonorPlayerData) player;
		int maximum = dawnMaximum(player);
		if (data.honorshields$getDawnSunCharge() < maximum && sunlight(player) && now - timers.dawn >= 10L * 20L) {
			data.honorshields$setDawnSunCharge(data.honorshields$getDawnSunCharge() + 1);
			timers.dawn = now;
			if (data.honorshields$getDawnSunCharge() >= maximum) data.honorshields$setDawnFullSunArmed(true);
		} else if (!sunlight(player) || data.honorshields$getDawnSunCharge() >= maximum) {
			timers.dawn = now;
		}
		if (((HonorPlayerData) player).honorshields$getShieldCondition() == ShieldCondition.EXALTED
			&& !data.honorshields$hasDawnLastChance()) {
			if (now >= data.honorshields$getDawnLastChanceProgress()) {
				data.honorshields$setDawnLastChance(true);
				data.honorshields$setDawnLastChanceProgress(0L);
				HonorShieldsPackets.abilityEffect(player, ShieldType.DAWN, 3, null, 1);
				HonorShieldsPackets.cooldown(player, 4, "Second Sunrise", 0);
			}
		}
	}

	private static boolean sunlight(ServerPlayer player) {
		ServerLevel level = (ServerLevel) player.level();
		return level.getSkyDarken() < 4 && level.canSeeSky(player.blockPosition())
			&& level.getMaxLocalRawBrightness(player.blockPosition()) >= 15 && !level.isRainingAt(player.blockPosition());
	}

	private static void afterDamage(LivingEntity damaged, DamageSource source, float baseDamage, float damageTaken, boolean blocked) {
		if (damageTaken <= 0.0F) return;
		if (damaged instanceof ServerPlayer warden && activeShield(warden) == ShieldType.WARDEN
			&& hostileSource(warden, source)) addWardenDamage(warden, damageTaken);
		SeasonTwoGameplay.onSuccessfulDamage(damaged, source, damageTaken);
		SeasonTwoGameplay.applyCrystalThorns(damaged, source);
	}

	private static boolean allowDeath(LivingEntity entity, DamageSource source, float amount) {
		if (!(entity instanceof ServerPlayer player)) return true;
		if (PlowHandler.preventLethal(player)) return false;
		HonorPlayerData data = (HonorPlayerData) player;
		if (activeShield(player) != ShieldType.DAWN || data.honorshields$getShieldCondition() != ShieldCondition.EXALTED
			|| !data.honorshields$hasDawnLastChance()) return true;
		data.honorshields$setDawnLastChance(false);
		data.honorshields$setDawnLastChanceProgress(((ServerLevel) player.level()).getGameTime() + DAWN_LAST_CHANCE_TICKS);
		player.setHealth(1.0F);
		EffectStacking.applyOnce(player, MobEffects.RESISTANCE, 80, 1);
		EffectStacking.applyOnce(player, MobEffects.REGENERATION, 200, 0);
		EffectStacking.applyOnce(player, MobEffects.FIRE_RESISTANCE, 1_200, 0);
		EffectStacking.applyOnce(player, MobEffects.ABSORPTION, 2_400, 1);
		for (LivingEntity target : enemies(player, player.position(), 10.0)) {
			EffectStacking.applyOnce(target, MobEffects.BLINDNESS, 100, 0);
				HonorShieldsPackets.abilityEffect(player, ShieldType.DAWN, 4, target, 2);
		}
		HonorShieldsPackets.abilityEffect(player, ShieldType.DAWN, 4, null, 0);
		sync(player, true);
		return false;
	}

	public static float absorbDawnDamage(ServerPlayer player, float amount) {
		HonorPlayerData data = (HonorPlayerData) player;
		if (activeShield(player) != ShieldType.DAWN || data.honorshields$getDawnSunCharge() <= 0 || amount <= 0.0F) return amount;
		data.honorshields$setDawnSunCharge(data.honorshields$getDawnSunCharge() - 1);
		HonorShieldsPackets.abilityEffect(player, ShieldType.DAWN, 0, null, 2);
		sync(player, true);
		return Math.max(0.0F, amount - 1.0F);
	}

	public static float applyFullSunAttack(ServerPlayer attacker, LivingEntity target, float amount) {
		HonorPlayerData data = (HonorPlayerData) attacker;
		if (activeShield(attacker) != ShieldType.DAWN || !data.honorshields$isDawnFullSunArmed()
			|| data.honorshields$getDawnSunCharge() <= 0 || !isEnemy(attacker, target)) return amount;
		data.honorshields$setDawnFullSunArmed(false);
		data.honorshields$setDawnSunCharge(data.honorshields$getDawnSunCharge() - 1);
		ServerLevel level = (ServerLevel) attacker.level();
		for (ServerPlayer ally : level.getPlayers(candidate -> candidate.distanceToSqr(attacker) <= 100.0
			&& TrustManager.isMutualTrust(attacker, candidate))) EffectStacking.applyOnce(ally, MobEffects.REGENERATION, 100, 0);
		HonorShieldsPackets.abilityEffect(attacker, ShieldType.DAWN, 0, target, 2);
		sync(attacker, true);
		return amount + 1.0F;
	}

	private static void addWardenDamage(ServerPlayer player, float damage) {
		HonorPlayerData data = (HonorPlayerData) player;
		float maximum = wardenMaximumDamage(player);
		float before = data.honorshields$getWardenStoredDamage();
		float after = Math.min(maximum, before + damage);
		data.honorshields$setWardenStoredDamage(after);
		long now = ((ServerLevel) player.level()).getGameTime();
		WARDEN_LAST_DAMAGE.put(player.getUUID(), now);
		// The meter remains stable for twenty quiet seconds, then loses one heart
		// every five seconds.  The tick loop runs once per second, so these values
		// are expressed in game ticks rather than wall-clock milliseconds.
		WARDEN_NEXT_DECAY.put(player.getUUID(), now + 400L);
		if (before < maximum && after >= maximum) {
			for (LivingEntity target : enemies(player, player.position(), 10.0)) {
				EffectStacking.applyOnce(target, MobEffects.GLOWING, 200, 0);
				HonorShieldsPackets.abilityEffect(player, ShieldType.WARDEN, 0, target, 2);
			}
			HonorShieldsPackets.abilityEffect(player, ShieldType.WARDEN, 0, null, 1);
		}
		sync(player, true);
	}

	/** Stored Warden damage begins fading only after twenty quiet seconds, one heart every five seconds. */
	private static void tickWardenDecay(ServerPlayer player, long now) {
		if (activeShield(player) != ShieldType.WARDEN) return;
		HonorPlayerData data = (HonorPlayerData) player;
		if (data.honorshields$getWardenStoredDamage() <= 0.0F) return;
		UUID id = player.getUUID();
		long lastDamage = WARDEN_LAST_DAMAGE.computeIfAbsent(id, ignored -> now);
		long nextDecay = WARDEN_NEXT_DECAY.computeIfAbsent(id, ignored -> lastDamage + 400L);
		if (now < nextDecay) return;
		data.honorshields$setWardenStoredDamage(Math.max(0.0F, data.honorshields$getWardenStoredDamage() - 2.0F));
		WARDEN_NEXT_DECAY.put(id, now + 100L);
		sync(player, true);
	}

	private static void tickWardenScream(ServerPlayer player, long now) {
		UUID id = player.getUUID();
		Scream scream = WARDEN_SCREAMS.get(id);
		if (scream == null) {
			if (activeShield(player) == ShieldType.WARDEN
				&& ((HonorPlayerData) player).honorshields$getWardenStoredDamage() >= wardenMaximumDamage(player)
				&& player.isShiftKeyDown() && player.isBlocking()) {
				LivingEntity target = nearestSonicTarget(player);
				if (target == null) return;
				// Vanilla 26.2 SonicBoom charges for 34 ticks and owns a 60-tick behavior.
				// Spend the full meter when the shriek is committed, even if the target
				// later steps out of range during the windup.
				((HonorPlayerData) player).honorshields$setWardenStoredDamage(0.0F);
				WARDEN_LAST_DAMAGE.remove(id);
				WARDEN_NEXT_DECAY.remove(id);
				WARDEN_SCREAMS.put(id, new Scream(target.getUUID(), now + 34L, now + 60L, false));
				HonorShieldsPackets.abilityEffect(player, ShieldType.WARDEN, 4, null, 0);
				sync(player, true);
			}
			return;
		}
		if (activeShield(player) != ShieldType.WARDEN || !player.isAlive()) {
			WARDEN_SCREAMS.remove(id);
			return;
		}
		if (!scream.fired() && now >= scream.firesAt()) {
			fireWardenScream(player, scream);
			WARDEN_SCREAMS.put(id, new Scream(scream.target(), scream.firesAt(), scream.completesAt(), true));
		}
		if (now >= scream.completesAt()) WARDEN_SCREAMS.remove(id);
	}

	private static void fireWardenScream(ServerPlayer player, Scream scream) {
		HonorPlayerData data = (HonorPlayerData) player;
		data.honorshields$setWardenStoredDamage(0.0F);
		WARDEN_LAST_DAMAGE.remove(player.getUUID());
		WARDEN_NEXT_DECAY.remove(player.getUUID());
		ServerLevel level = (ServerLevel) player.level();
		if (level.getEntity(scream.target()) instanceof LivingEntity target && target.isAlive()
			&& isEnemy(player, target) && sonicRange(player, target)) {
			float damage = switch (data.honorshields$getShieldCondition()) {
				case TARNISHED -> 6.0F;
				case HONORED -> 8.0F;
				case BLESSED, EXALTED -> 10.0F;
				case FORSAKEN -> 0.0F;
			};
			Vec3 direction = target.getEyePosition().subtract(player.getEyePosition()).normalize();
			if (target.hurtServer(level, AbilityDamage.source(level, player, AbilityDamage.Kind.WARDEN_SONIC_SHRIEK), damage)
				&& !ShieldBlockingHandler.blocksKnockback(target)) {
				double resistance = 1.0 - target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
				target.push(direction.x * 2.5 * resistance, direction.y * 0.5 * resistance,
					direction.z * 2.5 * resistance);
			}
			HonorShieldsPackets.abilityEffect(player, ShieldType.WARDEN, 4, target, 2);
		}
		HonorShieldsPackets.abilityEffect(player, ShieldType.WARDEN, 4, null, 1);
		ShieldAbilityHandler.setAllCooldowns(player, ShieldType.WARDEN, 10);
		sync(player, true);
	}

	private static LivingEntity nearestSonicTarget(ServerPlayer player) {
		AABB box = new AABB(player.getX() - 15.0, player.getY() - 20.0, player.getZ() - 15.0,
			player.getX() + 15.0, player.getY() + 20.0, player.getZ() + 15.0);
		return player.level().getEntitiesOfClass(LivingEntity.class, box,
			target -> isEnemy(player, target) && sonicRange(player, target)).stream()
			.min((left, right) -> Double.compare(left.distanceToSqr(player), right.distanceToSqr(player))).orElse(null);
	}

	private static boolean sonicRange(ServerPlayer player, LivingEntity target) {
		double x = target.getX() - player.getX();
		double z = target.getZ() - player.getZ();
		return x * x + z * z < 15.0 * 15.0 && Math.abs(target.getY() - player.getY()) < 20.0;
	}

	private static void tickShieldDash(ServerPlayer player) {
		UUID id = player.getUUID();
		ShieldType shield = activeShield(player);
		boolean combo = (shield == ShieldType.VOID || shield == ShieldType.THUNDER)
			&& player.isShiftKeyDown() && player.isBlocking();
		if (!combo) {
			DASH_COMBO_HELD.remove(id);
			return;
		}
		if (!DASH_COMBO_HELD.add(id)) return;
		HonorPlayerData data = (HonorPlayerData) player;
		if (shield == ShieldType.VOID && data.honorshields$getVoidCharge() > 0 && dash(player, 5.0, ShieldType.VOID)) {
			data.honorshields$setVoidCharge(data.honorshields$getVoidCharge() - 1);
			sync(player, true);
		} else if (shield == ShieldType.THUNDER && data.honorshields$getShieldCondition() == ShieldCondition.EXALTED
			&& data.honorshields$getThunderCharge() > 0 && dash(player, 13.0, ShieldType.THUNDER)) {
			data.honorshields$setThunderCharge(data.honorshields$getThunderCharge() - 1);
			sync(player, true);
		}
	}

	/** Server-authoritative, collision-clamped teleport dash. */
	private static boolean dash(ServerPlayer player, double distance, ShieldType shield) {
		ServerLevel level = (ServerLevel) player.level();
		Vec3 origin = player.position();
		Vec3 movement = player.getDeltaMovement().multiply(1.0, 0.0, 1.0);
		Vec3 direction = movement.lengthSqr() > 0.0025 ? movement.normalize() : player.getLookAngle().normalize();
		Vec3 destination = origin;
		for (double traveled = 0.25; traveled <= distance + 1.0E-6; traveled += 0.25) {
			Vec3 candidate = origin.add(direction.scale(traveled));
			BlockPos block = BlockPos.containing(candidate);
			if (!level.hasChunkAt(block) || !level.getWorldBorder().isWithinBounds(block)
				|| !level.noCollision(player, player.getBoundingBox().move(candidate.subtract(origin)))) break;
			destination = candidate;
		}
		if (destination.distanceToSqr(origin) < 0.25) return false;
		HonorShieldsPackets.abilityEffectFrom(player, shield, 4, null, 1, origin);
		player.teleportTo(destination.x, destination.y, destination.z);
		player.resetFallDistance();
		player.setDeltaMovement(Vec3.ZERO);
		if (shield == ShieldType.VOID) ShieldAbilityHandler.onVoidTeleport(player, origin, destination);
		HonorShieldsPackets.abilityEffectFrom(player, shield, 4, null, 1, destination);
		return true;
	}

	public static int thunderCharge(ServerPlayer player) { return ((HonorPlayerData) player).honorshields$getThunderCharge(); }
	public static void consumeThunder(ServerPlayer player) {
		HonorPlayerData data = (HonorPlayerData) player;
		data.honorshields$setThunderCharge(data.honorshields$getThunderCharge() - 1);
		sync(player, true);
	}

	public static boolean consumeTempest(ServerPlayer player, int cost) {
		HonorPlayerData data = (HonorPlayerData) player;
		if (data.honorshields$getTempestCharge() < cost) return false;
		data.honorshields$setTempestCharge(data.honorshields$getTempestCharge() - cost);
		sync(player, true);
		return true;
	}

	public static void initializeForShield(ServerPlayer player, ShieldType shield) {
		HonorPlayerData data = (HonorPlayerData) player;
		data.honorshields$setThunderCharge(0);
		data.honorshields$setVoidCharge(shield == ShieldType.VOID ? 3 : 0);
		data.honorshields$setTempestCharge(shield == ShieldType.TEMPEST ? tempestMaximum(player) : 0);
		data.honorshields$setDawnSunCharge(0);
		data.honorshields$setDawnFullSunArmed(false);
		data.honorshields$setWardenStoredDamage(0.0F);
		WARDEN_LAST_DAMAGE.remove(player.getUUID());
		WARDEN_NEXT_DECAY.remove(player.getUUID());
		data.honorshields$setDawnLastChance(false);
		data.honorshields$setDawnLastChanceProgress(0L);
		data.honorshields$setStoneBulwarkReadyAt(0L);
		data.honorshields$setDemonCoreReadyAt(0L);
		data.honorshields$setAbsoluteZeroReadyAt(0L);
		data.honorshields$setElderMercyReadyAt(0L);
		data.honorshields$setBlackoutReadyAt(0L);
		data.honorshields$setVerdancy(0);
		data.honorshields$setVerdancyOverflow(0);
		sync(player, true);
	}

	public static void resetPlayer(ServerPlayer player) {
		UUID id = player.getUUID();
		TIMERS.remove(id); LAST_SENT.remove(id); WARDEN_SCREAMS.remove(id); DASH_COMBO_HELD.remove(id);
		WARDEN_LAST_DAMAGE.remove(id); WARDEN_NEXT_DECAY.remove(id);
	}

	public static void sync(ServerPlayer player, boolean force) {
		ShieldType shield = ((HonorPlayerData) player).honorshields$getShieldType();
		HonorPlayerData data = (HonorPlayerData) player;
		Snapshot snapshot = shield == null ? new Snapshot("", 0, 0, false) : switch (shield) {
			case THUNDER -> new Snapshot("static", data.honorshields$getThunderCharge(), thunderMaximum(player), false);
			case VOID -> new Snapshot("void", data.honorshields$getVoidCharge(), 3, false);
			case TEMPEST -> new Snapshot("wind", data.honorshields$getTempestCharge(), tempestMaximum(player), false);
			case DAWN -> new Snapshot("sun", data.honorshields$getDawnSunCharge(), dawnMaximum(player), data.honorshields$isDawnFullSunArmed());
			case WARDEN -> new Snapshot("warden", Math.round(data.honorshields$getWardenStoredDamage() / 2.0F), Math.round(wardenMaximumDamage(player) / 2.0F), false);
			case PLOW -> new Snapshot("verdancy", data.honorshields$getVerdancy(), PlowHandler.MAX_VERDANCY, false);
			default -> new Snapshot("", 0, 0, false);
		};
		if (force || !snapshot.equals(LAST_SENT.get(player.getUUID()))) {
			LAST_SENT.put(player.getUUID(), snapshot);
			HonorShieldsPackets.shieldResource(player, snapshot.kind(), snapshot.current(), snapshot.maximum(), snapshot.armed());
		}
		if (force) syncExaltedPassive(player);
		if (force && shield == ShieldType.PLOW && data.honorshields$getShieldCondition() == ShieldCondition.EXALTED)
			HonorShieldsPackets.shieldResource(player, "verdancy_overflow", data.honorshields$getVerdancyOverflow(), PlowHandler.MAX_VERDANCY, false);
	}

	private static void clamp(ServerPlayer player) {
		HonorPlayerData data = (HonorPlayerData) player;
		data.honorshields$setTempestCharge(Math.min(tempestMaximum(player), data.honorshields$getTempestCharge()));
		data.honorshields$setThunderCharge(Math.min(thunderMaximum(player), data.honorshields$getThunderCharge()));
		data.honorshields$setVoidCharge(Math.min(3, data.honorshields$getVoidCharge()));
		data.honorshields$setDawnSunCharge(Math.min(dawnMaximum(player), data.honorshields$getDawnSunCharge()));
		data.honorshields$setWardenStoredDamage(Math.min(wardenMaximumDamage(player), data.honorshields$getWardenStoredDamage()));
		data.honorshields$setVerdancy(Math.min(PlowHandler.MAX_VERDANCY, data.honorshields$getVerdancy()));
		data.honorshields$setVerdancyOverflow(Math.min(PlowHandler.MAX_VERDANCY, data.honorshields$getVerdancyOverflow()));
		if (data.honorshields$getDawnSunCharge() >= dawnMaximum(player)) data.honorshields$setDawnFullSunArmed(true);
		if (data.honorshields$getDawnSunCharge() <= 0) data.honorshields$setDawnFullSunArmed(false);
	}

	public static void conditionChanged(ServerPlayer player) {
		clamp(player);
		SeasonTwoGameplay.validateCrystalBulwark(player);
		sync(player, true);
	}

	public static void exaltedPassiveUnlocked(ServerPlayer player) {
		HonorPlayerData data = (HonorPlayerData) player;
		long now = ((ServerLevel) player.level()).getGameTime();
		if (data.honorshields$getShieldType() == ShieldType.DAWN) {
			data.honorshields$setDawnLastChance(true);
			data.honorshields$setDawnLastChanceProgress(0L);
		} else if (data.honorshields$getShieldType() == ShieldType.STONE) {
			data.honorshields$setStoneBulwarkReadyAt(now);
		} else if (data.honorshields$getShieldType() == ShieldType.MONSOON) {
			MonsoonHandler.resetMaelstromCooldown(player);
		} else if (data.honorshields$getShieldType() == ShieldType.WARDEN) {
			data.honorshields$setWardenStoredDamage(0.0F);
			WARDEN_LAST_DAMAGE.remove(player.getUUID());
			WARDEN_NEXT_DECAY.remove(player.getUUID());
		}
		syncExaltedPassive(player);
	}

	public static void exaltedPassiveCooldownStarted(ServerPlayer player) {
		syncExaltedPassive(player);
	}

	private static void syncExaltedPassive(ServerPlayer player) {
		HonorPlayerData data = (HonorPlayerData) player;
		long now = ((ServerLevel) player.level()).getGameTime();
		if (data.honorshields$getShieldType() == ShieldType.CINDER) {
			HonorShieldsPackets.cooldown(player, 4, "Demon Core",
				(int) Math.ceil(Math.max(0L, data.honorshields$getDemonCoreReadyAt() - now) / 20.0));
		} else if (data.honorshields$getShieldType() == ShieldType.RIME) {
			HonorShieldsPackets.cooldown(player, 4, "Absolute Zero",
				(int) Math.ceil(Math.max(0L, data.honorshields$getAbsoluteZeroReadyAt() - now) / 20.0));
		} else if (data.honorshields$getShieldType() == ShieldType.TEMPEST) {
			HonorShieldsPackets.cooldown(player, 4, "Windborne Impact", 0);
		} else if (data.honorshields$getShieldType() == ShieldType.DAWN) {
			long readyAt = data.honorshields$hasDawnLastChance() ? now : data.honorshields$getDawnLastChanceProgress();
			HonorShieldsPackets.cooldown(player, 4, "Second Sunrise", (int) Math.ceil(Math.max(0L, readyAt - now) / 20.0));
		} else if (data.honorshields$getShieldType() == ShieldType.STONE) {
			HonorShieldsPackets.cooldown(player, 4, "Crystal Bulwark",
				(int) Math.ceil(Math.max(0L, data.honorshields$getStoneBulwarkReadyAt() - now) / 20.0));
		} else if (data.honorshields$getShieldType() == ShieldType.THUNDER) {
			HonorShieldsPackets.cooldown(player, 4, "Storm Step", 0);
		} else if (data.honorshields$getShieldType() == ShieldType.PLOW) {
			HonorShieldsPackets.cooldown(player, 4, "Eden's Intervention",
				(int) Math.ceil(Math.max(0L, data.honorshields$getVerdancy() < PlowHandler.MAX_VERDANCY
					? 1L : data.honorshields$getEdenInterventionReadyAt() - now) / 20.0));
		} else if (data.honorshields$getShieldType() == ShieldType.VOID) {
			HonorShieldsPackets.cooldown(player, 4, "Blackout",
				(int) Math.ceil(Math.max(0L, data.honorshields$getBlackoutReadyAt() - now) / 20.0));
		} else if (data.honorshields$getShieldType() == ShieldType.OAK) {
			HonorShieldsPackets.cooldown(player, 4, "Elder's Mercy",
				(int) Math.ceil(Math.max(0L, data.honorshields$getElderMercyReadyAt() - now) / 20.0));
		} else if (data.honorshields$getShieldType() == ShieldType.MONSOON) {
			HonorShieldsPackets.cooldown(player, 4, "Maelstrom", MonsoonHandler.maelstromCooldownSeconds(player));
		} else if (data.honorshields$getShieldType() == ShieldType.ANGLER) {
			HonorShieldsPackets.cooldown(player, 4, "Abyssal Treasure", 0);
		}
	}

	public static int tempestMaximum(ServerPlayer player) {
		return ((HonorPlayerData) player).honorshields$getShieldCondition() == ShieldCondition.EXALTED ? 20 : 10;
	}
	public static int thunderMaximum(ServerPlayer player) {
		return ((HonorPlayerData) player).honorshields$getShieldCondition() == ShieldCondition.EXALTED ? 6 : 3;
	}
	public static int dawnMaximum(ServerPlayer player) {
		return ((HonorPlayerData) player).honorshields$getShieldCondition() == ShieldCondition.EXALTED ? 6 : 3;
	}
	public static float wardenMaximumDamage(ServerPlayer player) {
		return ((HonorPlayerData) player).honorshields$getShieldCondition() == ShieldCondition.EXALTED ? 12.0F : 20.0F;
	}

	public static ShieldType activeShield(ServerPlayer player) {
		HonorPlayerData data = (HonorPlayerData) player;
		ShieldType assigned = data.honorshields$getShieldType();
		return assigned != null && data.honorshields$getShieldCondition().usable()
			&& ShieldType.fromStack(player.getOffhandItem()) == assigned ? assigned : null;
	}

	public static boolean hostileSource(ServerPlayer player, DamageSource source) {
		return source.getEntity() instanceof LivingEntity attacker && attacker != player && isEnemy(player, attacker);
	}

	public static boolean isEnemy(ServerPlayer owner, LivingEntity candidate) {
		if (candidate == owner || !candidate.isAlive() || candidate instanceof ArmorStand || TrustManager.trusts(owner, candidate)) return false;
		if (candidate instanceof ServerPlayer) return true;
		return candidate instanceof Enemy || candidate instanceof Mob mob && mob.getTarget() == owner;
	}

	public static java.util.List<LivingEntity> enemies(ServerPlayer owner, Vec3 center, double radius) {
		AABB box = new AABB(center.x - radius, center.y - radius, center.z - radius, center.x + radius, center.y + radius, center.z + radius);
		return owner.level().getEntitiesOfClass(LivingEntity.class, box,
			target -> target.position().distanceToSqr(center) <= radius * radius && isEnemy(owner, target));
	}

	private ShieldResourceManager() {}
}
