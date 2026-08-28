package com.honorablesmp.honorshields.shield;

import com.honorablesmp.honorshields.data.HonorPlayerData;
import com.honorablesmp.honorshields.classsystem.TrustManager;
import com.honorablesmp.honorshields.classsystem.EffectStacking;
import com.honorablesmp.honorshields.classsystem.RogueStealthState;
import com.honorablesmp.honorshields.network.HonorShieldsPackets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.fish.Pufferfish;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class ShieldAbilityHandler {
	private enum Slot { ONE, TWO, ULTIMATE }
	private record CooldownKey(ShieldType shield, Slot slot) {}
	private record Scheduled(long tick, Runnable action) {}
	private record TemporaryBlock(ServerLevel level, BlockPos pos, BlockState oldState, BlockState replacement, long expires, UUID owner) {}
	private static final class EarthquakeLift {
		final ServerLevel level;
		final BlockPos pos;
		final BlockState state;
		final UUID owner;
		final long startTick;
		final int delay;
		final double outwardX;
		final double outwardZ;
		final double height;
		Display.BlockDisplay display;
		boolean lifted;

		EarthquakeLift(ServerLevel level, BlockPos pos, BlockState state, UUID owner, long startTick,
			int delay, double outwardX, double outwardZ, double height) {
			this.level = level;
			this.pos = pos;
			this.state = state;
			this.owner = owner;
			this.startTick = startTick;
			this.delay = delay;
			this.outwardX = outwardX;
			this.outwardZ = outwardZ;
			this.height = height;
		}
	}
	/**
	 * Captures the cast point as well as the owner. Most lingering abilities still
	 * resolve around their moving caster, but Hurricane and Black Hole deliberately
	 * use this immutable origin for their full lifetime.
	 */
	private record Zone(UUID owner, ShieldType type, ServerLevel level, Vec3 origin, long expires, long nextPulse) {}
	private record VeinSenseCache(ServerLevel level, List<BlockPos> ores) {}
	private record BurnKey(UUID owner, UUID target) {}
	private record CinderBurn(ServerLevel level, long expires, long nextPulse, String slot) {}
	private record CinderTether(ServerLevel level, UUID target, long expires) {}
	private record RiftMirror(ServerLevel level, Vec3 origin, long expires) {}
	private record DemonCore(long expires, long nextPulse) {}
	private record EchoBeacon(ServerLevel level, BlockPos pos, BlockState previous, long expires, long nextPulse, UUID lastTrigger) {}

	private static final Map<UUID, Map<CooldownKey, Long>> COOLDOWNS = new HashMap<>();
	private static final Map<UUID, Long> PHASED_UNTIL = new HashMap<>();
	private static final Map<UUID, Float> STONE_SKIN = new HashMap<>();
	private static final Map<UUID, VeinSenseCache> VEIN_SENSE_ORES = new HashMap<>();
	private static final Map<BurnKey, CinderBurn> CINDER_BURNS = new HashMap<>();
	private static final Map<UUID, CinderTether> CINDER_TETHERS = new HashMap<>();
	private static final Map<UUID, RiftMirror> RIFT_MIRRORS = new HashMap<>();
	private static final Map<UUID, DemonCore> DEMON_CORES = new HashMap<>();
	private static final Map<UUID, EchoBeacon> ECHO_BEACONS = new HashMap<>();
	private static final List<Scheduled> SCHEDULED = new ArrayList<>();
	private static final List<TemporaryBlock> TEMP_BLOCKS = new ArrayList<>();
	private static final List<EarthquakeLift> EARTHQUAKE_LIFTS = new ArrayList<>();
	private static final List<Zone> ZONES = new ArrayList<>();
	private static final int RIME_FIELD_RADIUS = 3;
	private static final long RIME_FIELD_DURATION_TICKS = 5L * 20L;
	private static final long OAK_WOLF_LIFETIME_TICKS = 30L * 20L;
	private static final double VOID_TENDRIL_RANGE = 5.0;
	private static final int EARTHQUAKE_MAX_BLOCKS = 256;
	private static final int EARTHQUAKE_GLOBAL_MAX_LIFTS = 768;
	private static final int EARTHQUAKE_LIFT_TICKS = 14;
	private static final net.minecraft.world.entity.ai.attributes.AttributeModifier BLACKOUT_STEALTH_MARKER =
		new net.minecraft.world.entity.ai.attributes.AttributeModifier(
			RogueStealthState.BLACKOUT_STEALTH_MARKER_ID, 0.0D,
			net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE);

	/** Registers non-networked server interaction hooks for Exalted passive actions. */
	public static void registerEvents() {
		UseBlockCallback.EVENT.register((raw, level, hand, hit) -> {
			if (level.isClientSide() || !(raw instanceof ServerPlayer player) || !player.isShiftKeyDown()) return InteractionResult.PASS;
			if (player.getItemInHand(hand).is(Items.OAK_SAPLING) && triggerElderMercy(player, hand)) return InteractionResult.SUCCESS;
			return InteractionResult.PASS;
		});
	}

	public static void activateAbilityOne(ServerPlayer player, ShieldType type) {
		if (!canUse(player, type) || !ready(player, type, Slot.ONE, type.abilityOneCooldown())) return;
		if ((type == ShieldType.BOULDER && nearestInSight(player, 14.0) == null)
			|| (type == ShieldType.VOID && nearestInSight(player, VOID_TENDRIL_RANGE) == null)
			|| (type == ShieldType.ANGLER && nearestInSight(player, 12.0) == null)
			|| (type == ShieldType.THUNDER && enemies(player, 12.0).isEmpty())) return;
		ServerLevel level = (ServerLevel) player.level();
		boolean success = true;
		boolean sendGenericStart = true;
		switch (type) {
			case CINDER -> {
				List<LivingEntity> targets = coneTargets(player, 6.0, 0.45);
				if (targets.isEmpty()) { success = false; break; }
				int detailedImpacts = 0;
				for (LivingEntity target : targets) {
					applyCinderBurn(player, target, 3.0F, "one");
					hurt(player, target, 6.0F);
					if (detailedImpacts++ < 8) HonorShieldsPackets.abilityEffect(player, type, 1, target, 2);
				}
			}
			case RIME -> {
				List<LivingEntity> targets = enemies(player, 3.0);
				if (targets.isEmpty()) { success = false; break; }
				int detailedImpacts = 0;
				for (LivingEntity target : targets) {
					hurt(player, target, 6.0F);
					EffectStacking.applyOnce(target, MobEffects.SLOWNESS, 40, 1);
					applyVisibleFreeze(player, target, 60);
					if (detailedImpacts++ < 12) HonorShieldsPackets.abilityEffect(player, type, 1, target, 2);
				}
			}
			case TEMPEST -> {
				List<LivingEntity> targets = coneTargets(player, 8.0, 0.35);
				if (targets.isEmpty()) { success = false; break; }
				for (LivingEntity target : targets) { hurt(player, target, 6.0F); pushAway(player.position(), target, 2.2, 0.25); }
			}
			case THUNDER -> chainLightning(player);
			case DAWN -> player.heal(6.0F * condition(player).abilityMultiplier());
			case BOULDER -> {
				LivingEntity target = nearestInSight(player, 14.0);
				if (target != null) {
					hurt(player, target, 6.0F);
					pushAway(player.position(), target, 1.0, 0.2);
					HonorShieldsPackets.abilityEffect(player, type, 1, target, 2);
				}
			}
			case MONSOON -> {
				player.clearFire();
				affectNearby(player, 5.0, target -> { target.clearFire(); hurt(player, target, 6.0F); pushAway(player.position(), target, 1.7, 0.15); });
			}
			case VOID -> {
				LivingEntity target = nearestInSight(player, VOID_TENDRIL_RANGE);
				if (target != null) {
					hurt(player, target, 6.0F);
					pullTo(player.position(), target, 1.9);
					HonorShieldsPackets.abilityEffect(player, type, 1, target, 2);
				}
			}
			case OAK -> summonWolves(player);
			case STONE -> {
				veinSense(player, 15, true);
				affectNearby(player, 4.0, target -> { hurt(player, target, 4.0F); pushAway(player.position(), target, 1.35, 0.25); });
				ZONES.add(new Zone(player.getUUID(), type, level, player.position(), level.getGameTime() + 200, level.getGameTime() + 20));
			}
			case PLOW -> { success = PlowHandler.furrowbreaker(player); sendGenericStart = false; }
			case ANGLER -> {
				LivingEntity target = nearestInSight(player, 12.0);
				if (target != null) {
					pullTo(player.position(), target, 2.1);
					HonorShieldsPackets.abilityEffect(player, type, 1, target, 2);
				}
			}
			case VAGABOND -> {
				// Keep existing momentum and add a short, forward-only travel impulse.
				Vec3 facing = new Vec3(player.getLookAngle().x, 0.0, player.getLookAngle().z);
				if (facing.lengthSqr() < 1.0E-6) success = false;
				else {
					facing = facing.normalize();
					Vec3 motion = player.getDeltaMovement();
					player.setDeltaMovement(motion.x + facing.x * 1.25, motion.y, motion.z + facing.z * 1.25);
					player.hurtMarked = true;
				}
			}
			case WARDEN -> affectNearby(player, 4.0, target -> EffectStacking.applyOnce(target, MobEffects.SLOWNESS, 40, 4));
		}
		if (!success) return;
		if (sendGenericStart) HonorShieldsPackets.abilityEffect(player, type, 1, null, 0);
		complete(player, type, Slot.ONE, type.abilityOneCooldown(), type.abilityOne(), false);
	}

	public static void activateAbilityTwo(ServerPlayer player, ShieldType type, LivingEntity attacker) {
		if (!canUse(player, type) || !ready(player, type, Slot.TWO, type.abilityTwoCooldown())) return;
		ServerLevel level = (ServerLevel) player.level();
		CinderTether activeTether = type == ShieldType.CINDER ? CINDER_TETHERS.get(player.getUUID()) : null;
		boolean startingCinderTether = type == ShieldType.CINDER && activeTether == null;
		LivingEntity target = attacker == null ? nearestInSight(player, 10.0) : attacker;
		if (type == ShieldType.CINDER && activeTether == null && target == null) return;
		if ((type == ShieldType.THUNDER || type == ShieldType.DAWN) && target == null) return;
		boolean sendGenericStart = true;
		boolean success = true;
		switch (type) {
			case CINDER -> { success = phoenixTether(player, activeTether, target); sendGenericStart = false; }
			case RIME -> powderField(player);
			case TEMPEST -> {
				player.setDeltaMovement(player.getDeltaMovement().x, 1.25, player.getDeltaMovement().z);
				EffectStacking.applyOnce(player, MobEffects.SLOW_FALLING, 140, 0);
				player.hurtMarked = true;
			}
			case THUNDER -> {
				if (target != null) {
					EffectStacking.applyOnce(target, MobEffects.SLOWNESS, 100, 1);
					EffectStacking.applyOnce(target, MobEffects.WEAKNESS, 100, 1);
					HonorShieldsPackets.abilityEffect(player, type, 2, target, 2);
				}
			}
			case DAWN -> {
				if (target != null) {
					EffectStacking.applyOnce(target, MobEffects.BLINDNESS, 60, 0);
					HonorShieldsPackets.abilityEffect(player, type, 2, target, 2);
				}
			}
			case BOULDER -> EffectStacking.applyOnce(player, MobEffects.RESISTANCE, 60, 2);
			case MONSOON -> MonsoonHandler.healingWaters(player);
			case VOID -> { success = riftMirror(player); sendGenericStart = false; }
			case OAK -> regrow(player, 5, 4);
			case STONE -> STONE_SKIN.put(player.getUUID(), 5.0F);
			case PLOW -> { success = PlowHandler.harvestWard(player); sendGenericStart = false; }
			case ANGLER -> player.drop(AnglerLoot.roll(player), false);
			case VAGABOND -> VagabondHandler.demolition(player);
			case WARDEN -> { success = echoBeacon(player); sendGenericStart = false; }
		}
		if (!success) return;
		// Phoenix Tether is a two-step transaction: the first cast opens a short
		// recast window and must not put the move on cooldown yet. The cooldown is
		// committed by the launch recast or by the expiry path on the server.
		if (startingCinderTether) {
			feedback(player, type.abilityTwo(), false);
			return;
		}
		if (sendGenericStart) HonorShieldsPackets.abilityEffect(player, type, 2, null, 0);
		complete(player, type, Slot.TWO, type.abilityTwoCooldown(), type.abilityTwo(), false);
	}

	public static void activateUltimate(ServerPlayer player, ShieldType type) {
		if (type == ShieldType.VAGABOND) {
			player.sendOverlayMessage(Component.literal("Vagabond has no ultimate.").withStyle(ChatFormatting.GRAY));
			return;
		}
		if (!canUse(player, type)) return;
		if (condition(player) != ShieldCondition.EXALTED) {
			player.sendOverlayMessage(Component.literal("Ultimate locked: Exalted shield condition required.")
				.withStyle(ChatFormatting.LIGHT_PURPLE));
			return;
		}
		if (!ready(player, type, Slot.ULTIMATE, type.ultimateCooldown())) return;
		if (type == ShieldType.STONE && !hasVeinBurstTarget(player, 20) && enemies(player, 7.0).isEmpty()) return;
		ServerLevel level = (ServerLevel) player.level();
		long now = level.getGameTime();
		boolean sendGenericStart = type != ShieldType.PLOW;
		switch (type) {
			case CINDER -> {
				EffectStacking.applyOnce(player, MobEffects.FIRE_RESISTANCE, 200, 0);
				placeCinderMagmaTrail(player);
				ZONES.add(new Zone(player.getUUID(), type, level, player.position(), now + 200, now));
			}
			case RIME -> {
				int detailedImpacts = 0;
				for (LivingEntity target : enemies(player, 8.0)) {
					EffectStacking.applyOnce(target, MobEffects.SLOWNESS, 100, 2);
					EffectStacking.applyOnce(target, MobEffects.MINING_FATIGUE, 100, 2);
					applyVisibleFreeze(player, target, 100);
					if (detailedImpacts++ < 16) HonorShieldsPackets.abilityEffect(player, type, 3, target, 2);
				}
				ZONES.add(new Zone(player.getUUID(), type, level, player.position(), now + 100, now));
			}
			case TEMPEST, THUNDER, MONSOON, VOID, ANGLER -> {
				ZONES.add(new Zone(player.getUUID(), type, level, player.position(),
					now + (type == ShieldType.TEMPEST || type == ShieldType.THUNDER ? 100 : 60),
					type == ShieldType.TEMPEST ? now + 20 : now));
				if (type == ShieldType.ANGLER) summonAttackFish(player);
			}
			case DAWN -> {
				player.setHealth(player.getMaxHealth());
				EffectStacking.applyOnce(player, MobEffects.REGENERATION, 200, 1);
				for (LivingEntity target : enemies(player, 10.0)) if (target.isInvertedHealAndHarm()) target.igniteForSeconds(10.0F);
			}
			case BOULDER -> {
				affectNearby(player, 8.0, target -> {
					hurt(player, target, 12.0F, "ultimate");
					EffectStacking.applyOnce(target, MobEffects.SLOWNESS, 140, 1);
					pushAway(player.position(), target, 2.7, 0.8);
				});
				startEarthquake(player, 8.0);
			}
			case OAK -> {
				affectNearby(player, 8.0, target -> EffectStacking.applyOnce(target, MobEffects.SLOWNESS, 100, 3));
				for (ServerPlayer ally : level.getPlayers(candidate -> candidate != player && candidate.distanceToSqr(player) <= 64.0 && TrustManager.isMutualTrust(player, candidate))) {
					EffectStacking.applyOnce(ally, MobEffects.REGENERATION, 100, 0);
				}
			}
			case STONE -> {
				veinBurst(player, 20);
				affectNearby(player, 7.0, target -> { hurt(player, target, 8.0F, "ultimate"); pushAway(player.position(), target, 2.2, 0.55); });
			}
			case PLOW -> {
				if (!PlowHandler.bountifulHarvest(player)) return;
			}
			case VAGABOND -> { /* guarded above: Vagabond intentionally has no ultimate */ }
			case WARDEN -> {
				EffectStacking.applyOnce(player, MobEffects.RESISTANCE, 140, 1);
				affectNearby(player, 7.0, target -> {
					hurt(player, target, 12.0F, "ultimate");
					pushAway(player.position(), target, 1.4, 0.25);
				});
			}
		}
		if (sendGenericStart) HonorShieldsPackets.abilityEffect(player, type, 3, null, 0);
		complete(player, type, Slot.ULTIMATE, type.ultimateCooldown(), type.ultimate(), true);
	}

	public static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		Iterator<Scheduled> actions = SCHEDULED.iterator();
		while (actions.hasNext()) {
			Scheduled action = actions.next();
			if (now >= action.tick()) { action.action().run(); actions.remove(); }
		}
		Iterator<TemporaryBlock> blocks = TEMP_BLOCKS.iterator();
		while (blocks.hasNext()) {
			TemporaryBlock temp = blocks.next();
			if (now >= temp.expires()) {
				restoreTemporaryBlock(temp);
				blocks.remove();
			}
		}
		tickEarthquakeLifts();
		tickCinderTethers(server, now);
		tickEchoBeacons(server, now);
		tickDemonCores(server, now);
		tickRiftMirrors(server, now);
		ListIterator<Zone> zones = ZONES.listIterator();
		while (zones.hasNext()) {
			Zone zone = zones.next();
			ServerPlayer owner = server.getPlayerList().getPlayer(zone.owner());
			if (owner == null || (isCastAnchored(zone.type()) && owner.level() != zone.level())) {
				if (zone.type() == ShieldType.STONE) VEIN_SENSE_ORES.remove(zone.owner());
				zones.remove();
				continue;
			}
			if (now >= zone.expires()) {
				if (zone.type() == ShieldType.TEMPEST) finishHurricane(owner, zone);
				if (zone.type() == ShieldType.STONE) VEIN_SENSE_ORES.remove(zone.owner());
				zones.remove();
				continue;
			}
			if (zone.type() == ShieldType.TEMPEST) pullHurricaneTargets(owner, zone);
			if (zone.type() == ShieldType.CINDER && now % 4L == 0L) placeCinderMagmaTrail(owner);
			if (now >= zone.nextPulse()) {
				pulseZone(owner, zone);
				zones.set(new Zone(zone.owner(), zone.type(), zone.level(), zone.origin(), zone.expires(),
					now + (zone.type() == ShieldType.THUNDER ? 10 : 20)));
			}
		}
		tickCinderBurns(server, now);
		tickBlackoutMarkers(server);
		PHASED_UNTIL.entrySet().removeIf(entry -> entry.getValue() <= now);
	}

	public static void resetPlayer(ServerPlayer player) {
		UUID id = player.getUUID();
		TempestDoubleJumpHandler.resetPlayer(player);
		SeasonTwoGameplay.resetPlayer(player);
		COOLDOWNS.remove(id);
		PHASED_UNTIL.remove(id);
		STONE_SKIN.remove(id);
		VEIN_SENSE_ORES.remove(id);
		CINDER_BURNS.entrySet().removeIf(entry -> entry.getKey().owner().equals(id) || entry.getKey().target().equals(id));
		CINDER_TETHERS.remove(id);
		RIFT_MIRRORS.remove(id);
		DEMON_CORES.remove(id);
		EchoBeacon beacon = ECHO_BEACONS.remove(id);
		if (beacon != null) restoreEchoBeacon(beacon);
		ZONES.removeIf(zone -> zone.owner().equals(id));
		removeBlackoutMarker(player);
		Iterator<TemporaryBlock> blocks = TEMP_BLOCKS.iterator();
		while (blocks.hasNext()) {
			TemporaryBlock temp = blocks.next();
			if (temp.owner().equals(id)) {
				restoreTemporaryBlock(temp);
				blocks.remove();
			}
		}
		Iterator<EarthquakeLift> lifts = EARTHQUAKE_LIFTS.iterator();
		while (lifts.hasNext()) {
			EarthquakeLift lift = lifts.next();
			if (lift.owner.equals(id)) {
				restoreEarthquakeLift(lift);
				lifts.remove();
			}
		}
	}

	public static void restoreTemporaryBlocks() {
		for (TemporaryBlock temp : TEMP_BLOCKS) restoreTemporaryBlock(temp);
		TEMP_BLOCKS.clear();
		for (EarthquakeLift lift : EARTHQUAKE_LIFTS) restoreEarthquakeLift(lift);
		EARTHQUAKE_LIFTS.clear();
	}

	private static boolean phoenixTether(ServerPlayer player, CinderTether existing, LivingEntity target) {
		ServerLevel level = (ServerLevel) player.level();
		long now = level.getGameTime();
		if (existing != null) {
			LivingEntity tethered = level.getEntity(existing.target()) instanceof LivingEntity living ? living : null;
			if (tethered == null || !tethered.isAlive() || !ShieldResourceManager.isEnemy(player, tethered)) {
				CINDER_TETHERS.remove(player.getUUID());
				return false;
			}
			Vec3 direction = (player.isShiftKeyDown() ? player.position().subtract(tethered.position())
				: tethered.position().subtract(player.position()));
			if (direction.lengthSqr() < 1.0E-6) direction = player.getLookAngle();
			direction = direction.normalize();
			if (player.isShiftKeyDown()) {
				tethered.setDeltaMovement(direction.x * 1.35, Math.max(0.18, direction.y * 0.7), direction.z * 1.35);
				tethered.hurtMarked = true;
			} else {
				player.setDeltaMovement(direction.x * 1.35, Math.max(0.18, direction.y * 0.7), direction.z * 1.35);
				player.hurtMarked = true;
			}
			CINDER_TETHERS.remove(player.getUUID());
			HonorShieldsPackets.abilityEffectFrom(player, ShieldType.CINDER, 2, tethered, 2, player.position());
			return true;
		}
		if (target == null || !ShieldResourceManager.isEnemy(player, target)) return false;
		CINDER_TETHERS.put(player.getUUID(), new CinderTether(level, target.getUUID(), now + 60L));
		HonorShieldsPackets.abilityEffectFrom(player, ShieldType.CINDER, 2, target, 0, player.position());
		return true;
	}

	private static void tickCinderTethers(MinecraftServer server, long now) {
		Iterator<Map.Entry<UUID, CinderTether>> iterator = CINDER_TETHERS.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<UUID, CinderTether> entry = iterator.next();
			ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey());
			CinderTether tether = entry.getValue();
			if (owner == null || owner.level() != tether.level() || now >= tether.expires()
				|| !(tether.level().getEntity(tether.target()) instanceof LivingEntity target)
				|| !target.isAlive() || !ShieldResourceManager.isEnemy(owner, target)) {
				if (owner != null && owner.isAlive()) {
					HonorShieldsPackets.abilityEffectFrom(owner, ShieldType.CINDER, 2, null, 2, owner.position());
					commitCooldown(owner, ShieldType.CINDER, Slot.TWO, ShieldType.CINDER.abilityTwoCooldown());
				}
				iterator.remove();
				continue;
			}
			Vec3 delta = target.position().subtract(owner.position());
			if (delta.lengthSqr() > 36.0) {
				Vec3 correction = owner.position().subtract(target.position()).normalize().scale(0.12);
				target.setDeltaMovement(target.getDeltaMovement().add(correction.x, 0.0, correction.z));
				target.hurtMarked = true;
			}
			if (now % 10L == 0L) HonorShieldsPackets.abilityEffectFrom(owner, ShieldType.CINDER, 2, target, 1, owner.position());
		}
	}

	private static boolean riftMirror(ServerPlayer player) {
		ServerLevel level = (ServerLevel) player.level();
		long now = level.getGameTime();
		RiftMirror existing = RIFT_MIRRORS.get(player.getUUID());
		if (existing != null) {
			Vec3 destination = safeDestination(player, existing.origin());
			RIFT_MIRRORS.remove(player.getUUID());
			player.teleportTo(destination.x, destination.y, destination.z);
			player.resetFallDistance();
			player.setDeltaMovement(Vec3.ZERO);
			triggerBlackout(player, existing.origin(), destination);
			HonorShieldsPackets.abilityEffectFrom(player, ShieldType.VOID, 2, null, 2, destination);
			return true;
		}
		RIFT_MIRRORS.put(player.getUUID(), new RiftMirror(level, player.position(), now + 200L));
		HonorShieldsPackets.abilityEffectFrom(player, ShieldType.VOID, 2, null, 0, player.position());
		return true;
	}

	private static void tickRiftMirrors(MinecraftServer server, long now) {
		Iterator<Map.Entry<UUID, RiftMirror>> iterator = RIFT_MIRRORS.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<UUID, RiftMirror> entry = iterator.next();
			ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey());
			if (owner == null || owner.level() != entry.getValue().level()) {
				iterator.remove();
				continue;
			}
			RiftMirror mirror = entry.getValue();
			if (now < mirror.expires()) {
				// A real decoy entity would add collision, targeting, and persistence
				// overhead.  While the mirror is alive, redirect nearby hostile mobs'
				// navigation to its immutable origin and clear their live target; their
				// normal AI can reacquire the player after the ten-second illusion ends.
				AABB bounds = new AABB(mirror.origin(), mirror.origin()).inflate(12.0);
				for (Mob mob : mirror.level().getEntitiesOfClass(Mob.class, bounds,
					candidate -> candidate.isAlive() && candidate.getTarget() == owner)) {
					mob.setTarget(null);
					mob.getNavigation().moveTo(mirror.origin().x, mirror.origin().y, mirror.origin().z, 1.0D);
				}
				continue;
			}
			HonorShieldsPackets.abilityEffectFrom(owner, ShieldType.VOID, 2, null, 2, entry.getValue().origin());
			commitCooldown(owner, ShieldType.VOID, Slot.TWO, ShieldType.VOID.abilityTwoCooldown());
			iterator.remove();
		}
	}

	private static Vec3 safeDestination(ServerPlayer player, Vec3 requested) {
		ServerLevel level = (ServerLevel) player.level();
		Vec3 current = player.position();
		for (int y = 0; y <= 2; y++) for (int x = 0; x <= 1; x++) for (int z = 0; z <= 1; z++) {
			Vec3 candidate = requested.add(x * 0.5, y * 0.5, z * 0.5);
			if (level.noCollision(player, player.getBoundingBox().move(candidate.subtract(current)))) return candidate;
		}
		return current;
	}

	/** Called by the server-owned Void/Thunder dash paths after a successful teleport. */
	public static void onVoidTeleport(ServerPlayer player, Vec3 origin, Vec3 destination) {
		if (((HonorPlayerData) player).honorshields$getShieldType() == ShieldType.VOID)
			triggerBlackout(player, origin, destination);
	}

	private static void triggerBlackout(ServerPlayer player, Vec3 origin, Vec3 destination) {
		HonorPlayerData data = (HonorPlayerData) player;
		ServerLevel level = (ServerLevel) player.level();
		long now = level.getGameTime();
		if (data.honorshields$getShieldCondition() != ShieldCondition.EXALTED
			|| data.honorshields$getBlackoutReadyAt() > now) return;
		data.honorshields$setBlackoutReadyAt(now + 300L);
		EffectStacking.applyOnce(player, MobEffects.INVISIBILITY, 100, 0);
		EffectStacking.applyOnce(player, MobEffects.RESISTANCE, 100, 1);
		var sneakingSpeed = player.getAttribute(Attributes.SNEAKING_SPEED);
		if (sneakingSpeed != null && !sneakingSpeed.hasModifier(RogueStealthState.BLACKOUT_STEALTH_MARKER_ID))
			sneakingSpeed.addTransientModifier(BLACKOUT_STEALTH_MARKER);
		for (LivingEntity target : enemiesAround(player, destination, 10.0)) {
			EffectStacking.applyOnce(target, MobEffects.DARKNESS, 200, 0);
			EffectStacking.applyOnce(target, MobEffects.WEAKNESS, 200, 1);
			EffectStacking.applyOnce(target, MobEffects.GLOWING, 200, 0);
		}
		HonorShieldsPackets.abilityEffectFrom(player, ShieldType.VOID, 4, null, 0, origin);
		HonorShieldsPackets.abilityEffectFrom(player, ShieldType.VOID, 4, null, 2, destination);
	}

	/** Removes the synchronized equipment-hide marker as soon as Blackout's cloak ends. */
	private static void tickBlackoutMarkers(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!player.hasEffect(MobEffects.INVISIBILITY)) removeBlackoutMarker(player);
		}
	}

	private static void removeBlackoutMarker(ServerPlayer player) {
		var sneakingSpeed = player.getAttribute(Attributes.SNEAKING_SPEED);
		if (sneakingSpeed != null) sneakingSpeed.removeModifier(RogueStealthState.BLACKOUT_STEALTH_MARKER_ID);
	}

	public static boolean triggerAbsoluteZero(ServerPlayer player, LivingEntity attacker) {
		if (attacker == null || !ShieldResourceManager.isEnemy(player, attacker)) return false;
		HonorPlayerData data = (HonorPlayerData) player;
		ServerLevel level = (ServerLevel) player.level();
		long now = level.getGameTime();
		if (data.honorshields$getShieldCondition() != ShieldCondition.EXALTED
			|| data.honorshields$getAbsoluteZeroReadyAt() > now) return false;
		data.honorshields$setAbsoluteZeroReadyAt(now + 12_000L);
		EffectStacking.applyOnce(attacker, MobEffects.WEAKNESS, 200, 0);
		EffectStacking.applyOnce(attacker, MobEffects.BLINDNESS, 120, 0);
		EffectStacking.applyOnce(attacker, MobEffects.SLOWNESS, 160, 0);
		applyVisibleFreeze(player, attacker, 160);
		HonorShieldsPackets.abilityEffectFrom(player, ShieldType.RIME, 4, attacker, 0, player.position());
		return true;
	}

	/**
	 * Exalted Oak's sapling interaction. The trees are deliberately hand-built
	 * instead of using a configured feature so no worldgen feature can replace an
	 * existing block or touch a container.
	 */
	private static boolean triggerElderMercy(ServerPlayer player, InteractionHand hand) {
		if (ShieldResourceManager.activeShield(player) != ShieldType.OAK
			|| ((HonorPlayerData) player).honorshields$getShieldCondition() != ShieldCondition.EXALTED) return false;
		ServerLevel level = (ServerLevel) player.level();
		HonorPlayerData data = (HonorPlayerData) player;
		long now = level.getGameTime();
		if (data.honorshields$getElderMercyReadyAt() > now) {
			player.sendOverlayMessage(Component.literal("Cooldown: %.1fs".formatted(
				(data.honorshields$getElderMercyReadyAt() - now) / 20.0)).withStyle(ChatFormatting.RED));
			return false;
		}
		int planted = 0;
		BlockPos base = player.blockPosition();
		// Deterministic rings read as a grove while keeping the operation bounded.
		for (int ring = 1; ring <= 4; ring++) {
			int radius = ring * 5;
			int points = ring * 4;
			for (int i = 0; i < points; i++) {
				double angle = (Math.PI * 2.0 * i / points) + (ring & 1) * 0.18;
				BlockPos ground = base.offset((int) Math.round(Math.cos(angle) * radius), 0,
					(int) Math.round(Math.sin(angle) * radius));
				if (placeElderTree(player, level, ground.above())) planted++;
			}
		}
		if (planted == 0) return false;
		data.honorshields$setElderMercyReadyAt(now + 24_000L);
		EffectStacking.applyOnce(player, MobEffects.ABSORPTION, 60, 1);
		EffectStacking.applyOnce(player, MobEffects.RESISTANCE, 200, 1);
		EffectStacking.applyOnce(player, MobEffects.REGENERATION, 100, 2);
		HonorShieldsPackets.cooldown(player, 4, "Elder's Mercy", 1_200);
		HonorShieldsPackets.abilityEffectFrom(player, ShieldType.OAK, 4, null, 0, player.position());
		return true;
	}

	private static boolean placeElderTree(ServerPlayer owner, ServerLevel level, BlockPos origin) {
		BlockState ground = level.getBlockState(origin.below());
		if (!level.hasChunkAt(origin) || !level.getWorldBorder().isWithinBounds(origin)
			|| !ground.is(BlockTags.DIRT) || level.getBlockEntity(origin.below()) != null
			|| !level.mayInteract(owner, origin.below())) return false;
		List<BlockPos> writes = new ArrayList<>();
		for (int y = 0; y < 4; y++) {
			BlockPos pos = origin.above(y);
			if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos)
				|| level.getBlockEntity(pos) != null || !level.mayInteract(owner, pos)
				|| !level.getBlockState(pos).canBeReplaced()) return false;
			writes.add(pos.immutable());
		}
		for (int x = -2; x <= 2; x++) for (int y = 2; y <= 4; y++) for (int z = -2; z <= 2; z++) {
			if (Math.abs(x) + Math.abs(z) > 3) continue;
			BlockPos pos = origin.offset(x, y, z);
			if (writes.contains(pos) || !level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos)
				|| level.getBlockEntity(pos) != null || !level.mayInteract(owner, pos)
				|| !level.getBlockState(pos).canBeReplaced()) continue;
			writes.add(pos.immutable());
		}
		for (BlockPos pos : writes) {
			int y = pos.getY() - origin.getY();
			boolean trunk = pos.getX() == origin.getX() && pos.getZ() == origin.getZ() && y < 4;
			level.setBlock(pos, trunk ? Blocks.OAK_LOG.defaultBlockState() : Blocks.OAK_LEAVES.defaultBlockState(), 3);
		}
		return true;
	}

	private static void tickDemonCores(MinecraftServer server, long now) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (ShieldResourceManager.activeShield(player) != ShieldType.CINDER
				|| ((HonorPlayerData) player).honorshields$getShieldCondition() != ShieldCondition.EXALTED) {
				DEMON_CORES.remove(player.getUUID());
				continue;
			}
			HonorPlayerData data = (HonorPlayerData) player;
			DemonCore core = DEMON_CORES.get(player.getUUID());
			if (core == null && (player.isInLava() || player.isOnFire() || player.getBlockStateOn().is(Blocks.FIRE) || player.getBlockStateOn().is(Blocks.SOUL_FIRE))
				&& data.honorshields$getDemonCoreReadyAt() <= now) {
				data.honorshields$setDemonCoreReadyAt(now + 900L);
				DEMON_CORES.put(player.getUUID(), new DemonCore(now + 120L, now));
				EffectStacking.applyOnce(player, MobEffects.REGENERATION, 200, 0);
				HonorShieldsPackets.abilityEffectFrom(player, ShieldType.CINDER, 4, null, 0, player.position());
				core = DEMON_CORES.get(player.getUUID());
			}
			if (core == null) continue;
			if (now >= core.expires()) {
				DEMON_CORES.remove(player.getUUID());
				HonorShieldsPackets.abilityEffectFrom(player, ShieldType.CINDER, 4, null, 2, player.position());
				continue;
			}
			if (now >= core.nextPulse()) {
				for (LivingEntity target : enemies(player, 4.0)) {
					target.igniteForSeconds(2.0F);
					hurt(player, target, 1.0F, "ultimate", AbilityDamage.Kind.CINDER_DEMON_CORE);
					pushAway(player.position(), target, 0.35, 0.08);
				}
				HonorShieldsPackets.abilityEffectFrom(player, ShieldType.CINDER, 4, null, 1, player.position());
				DEMON_CORES.put(player.getUUID(), new DemonCore(core.expires(), now + 10L));
			}
		}
	}

	private static boolean echoBeacon(ServerPlayer player) {
		ServerLevel level = (ServerLevel) player.level();
		long now = level.getGameTime();
		BlockPos pos = player.blockPosition().relative(net.minecraft.core.Direction.getApproximateNearest(player.getLookAngle().x, player.getLookAngle().y, player.getLookAngle().z));
		if (!level.hasChunkAt(pos)) return false;
		BlockState previous = level.getBlockState(pos);
		if (!previous.isAir() && !previous.canBeReplaced() || level.getBlockEntity(pos) != null || !level.mayInteract(player, pos)) return false;
		EchoBeacon old = ECHO_BEACONS.remove(player.getUUID());
		if (old != null) restoreEchoBeacon(old);
		level.setBlock(pos, Blocks.SCULK_SENSOR.defaultBlockState(), 3);
		ECHO_BEACONS.put(player.getUUID(), new EchoBeacon(level, pos.immutable(), previous, now + 100L, now + 5L, null));
		HonorShieldsPackets.abilityEffectFrom(player, ShieldType.WARDEN, 2, null, 0, Vec3.atCenterOf(pos));
		return true;
	}

	private static void tickEchoBeacons(MinecraftServer server, long now) {
		Iterator<Map.Entry<UUID, EchoBeacon>> iterator = ECHO_BEACONS.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<UUID, EchoBeacon> entry = iterator.next();
			ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey());
			EchoBeacon beacon = entry.getValue();
			if (owner == null || owner.level() != beacon.level() || now >= beacon.expires()) {
				restoreEchoBeacon(beacon);
				iterator.remove();
				continue;
			}
			if (now < beacon.nextPulse()) continue;
			LivingEntity trigger = beacon.level().getEntitiesOfClass(LivingEntity.class,
				new AABB(Vec3.atCenterOf(beacon.pos()), Vec3.atCenterOf(beacon.pos())).inflate(15.0), entity ->
					entity != owner && entity.isAlive() && entity.getDeltaMovement().lengthSqr() > 0.001
						&& !(entity instanceof net.minecraft.world.entity.decoration.ArmorStand)
						&& ShieldResourceManager.isEnemy(owner, entity)).stream().findFirst().orElse(null);
			if (trigger == null) {
				// Keep the temporary beacon event-driven at a bounded cadence instead
				// of rescanning its full radius every server tick while quiet.
				entry.setValue(new EchoBeacon(beacon.level(), beacon.pos(), beacon.previous(), beacon.expires(), now + 5L, beacon.lastTrigger()));
				continue;
			}
			for (LivingEntity target : enemiesAround(owner, Vec3.atCenterOf(beacon.pos()), 15.0))
				hurt(owner, target, 1.0F, "one", AbilityDamage.Kind.WARDEN_ECHO_BEACON);
			HonorShieldsPackets.abilityEffectFrom(owner, ShieldType.WARDEN, 2, trigger, 1, Vec3.atCenterOf(beacon.pos()));
			entry.setValue(new EchoBeacon(beacon.level(), beacon.pos(), beacon.previous(), beacon.expires(), now + 10L, trigger.getUUID()));
		}
	}

	private static void restoreEchoBeacon(EchoBeacon beacon) {
		if (beacon.level().getBlockState(beacon.pos()).is(Blocks.SCULK_SENSOR)) beacon.level().setBlockAndUpdate(beacon.pos(), beacon.previous());
	}

	private static void commitCooldown(ServerPlayer player, ShieldType shield, Slot slot, int seconds) {
		long now = ((ServerLevel) player.level()).getGameTime();
		COOLDOWNS.computeIfAbsent(player.getUUID(), ignored -> new HashMap<>()).put(new CooldownKey(shield, slot), now + seconds * 20L);
		HonorShieldsPackets.cooldown(player, slot == Slot.ONE ? 1 : slot == Slot.TWO ? 2 : 3,
			slot == Slot.ONE ? shield.abilityOne() : slot == Slot.TWO ? shield.abilityTwo() : shield.ultimate(), seconds);
	}

	private static void pulseZone(ServerPlayer player, Zone zone) {
		ShieldType type = zone.type();
		boolean castAnchored = isCastAnchored(type);
		Vec3 center = castAnchored ? zone.origin() : player.position();
		HonorShieldsPackets.abilityEffectFrom(player, type, type == ShieldType.STONE ? 1 : 3, null, 1, center);
		switch (type) {
			case CINDER -> {
				affectNearby(player, 4.0, target -> { applyCinderBurn(player, target, 2.0F, "ultimate"); hurt(player, target, 1.4F, "ultimate"); });
				placeCinderMagmaTrail(player);
			}
			case RIME -> affectNearby(player, 8.0, target -> {
				hurt(player, target, 2.4F, "ultimate");
				applyVisibleFreeze(player, target, 40);
			});
			case TEMPEST -> {
				for (LivingEntity target : enemiesAround(player, center, 8.0)) {
					if (target.position().distanceToSqr(center) <= 25.0) {
						hurt(player, target, 2.4F, "ultimate");
					}
				}
			}
			case THUNDER -> {
				List<LivingEntity> targets = enemies(player, 10.0);
				targets.sort((left, right) -> Double.compare(left.distanceToSqr(player), right.distanceToSqr(player)));
				// Ten half-second pulses add up to the authored seven-heart ultimate.
				for (int index = 0; index < targets.size(); index++) ultimateShock(player, targets.get(index), 1.4F, index < 6);
			}
			case MONSOON -> {
				affectNearby(player, 8.0, target -> {
					if (ShieldBlockingHandler.blocksKnockback(target)) return;
					pullTo(player.position(), target, 1.0);
					target.setDeltaMovement(target.getDeltaMovement().add(0.0, 0.22, 0.0));
					target.hurtMarked = true;
					hurt(player, target, 10.0F / 3.0F, "ultimate");
				});
				for (ServerPlayer ally : ((ServerLevel) player.level()).getPlayers(candidate ->
					candidate.distanceToSqr(player) <= 64.0 && TrustManager.isMutualTrust(player, candidate))) {
					ally.heal(10.0F / 3.0F * condition(player).ultimateMultiplier());
				}
			}
			case VOID -> affectNearby(player, center, 7.0, target -> {
				pullTo(center, target, 1.3);
				hurt(player, target, 14.0F / 3.0F, "ultimate");
			});
			case ANGLER -> affectNearby(player, 9.0, target -> { hurt(player, target, 14.0F / 3.0F, "ultimate"); pullTo(player.position(), target, 0.7); });
			case STONE -> veinSense(player, 15, false);
			default -> { }
		}
	}

	private static boolean isCastAnchored(ShieldType type) {
		return type == ShieldType.TEMPEST || type == ShieldType.VOID;
	}

	/** Applies a low, continuous inward force without moving the world-anchored funnel. */
	private static void pullHurricaneTargets(ServerPlayer player, Zone zone) {
		Vec3 center = zone.origin();
		for (LivingEntity target : enemiesAround(player, center, 8.0)) {
			if (ShieldBlockingHandler.blocksKnockback(target)) continue;
			Vec3 inward = center.subtract(target.position());
			Vec3 horizontal = new Vec3(inward.x, 0.0, inward.z);
			double distance = horizontal.length();
			if (distance < 0.08) continue;
			double strength = Math.min(0.10, 0.045 + distance * 0.007);
			Vec3 pull = horizontal.scale(strength / distance);
			target.setDeltaMovement(target.getDeltaMovement().add(pull.x, 0.018, pull.z));
			target.hurtMarked = true;
		}
	}

	/** Resolves once, at the true end of Hurricane's five-second lifetime. */
	private static void finishHurricane(ServerPlayer player, Zone zone) {
		Vec3 center = zone.origin();
		// Always broadcast one central release, even when the funnel caught no targets.
		HonorShieldsPackets.abilityEffectFrom(player, ShieldType.TEMPEST, 3, null, 2, center);
		int detailedImpacts = 0;
		for (LivingEntity target : enemiesAround(player, center, 8.0)) {
			if (target.position().distanceToSqr(center) <= 25.0) {
				hurt(player, target, 2.4F, "ultimate");
			}
			if (ShieldBlockingHandler.blocksKnockback(target)) continue;
			Vec3 outward = target.position().subtract(center);
			Vec3 horizontal = new Vec3(outward.x, 0.0, outward.z);
			if (horizontal.lengthSqr() < 1.0E-6) {
				double angle = target.getId() * 2.399963229728653;
				horizontal = new Vec3(Math.cos(angle), 0.0, Math.sin(angle));
			} else {
				horizontal = horizontal.normalize();
			}
			Vec3 motion = target.getDeltaMovement();
			target.setDeltaMovement(motion.x * 0.20 + horizontal.x * 3.6,
				Math.max(0.90, motion.y * 0.20 + 0.90),
				motion.z * 0.20 + horizontal.z * 3.6);
			target.hurtMarked = true;
			if (detailedImpacts++ < 12) HonorShieldsPackets.abilityEffectFrom(
				player, ShieldType.TEMPEST, 3, target, 2, center);
		}
	}

	public static float modifyIncoming(ServerPlayer player, float amount) {
		ShieldType assigned = ((HonorPlayerData) player).honorshields$getShieldType();
		if (assigned == null || ShieldType.fromStack(player.getOffhandItem()) != assigned) return amount;
		long now = ((ServerLevel) player.level()).getGameTime();
		if (PHASED_UNTIL.getOrDefault(player.getUUID(), 0L) > now) return 0.0F;
		if (assigned == ShieldType.WARDEN && player.isShiftKeyDown()) amount *= 1.0F - 0.10F * condition(player).passiveMultiplier();
		float absorb = STONE_SKIN.getOrDefault(player.getUUID(), 0.0F);
		if (absorb > 0.0F) {
			float used = Math.min(absorb, amount);
			amount -= used;
			if (absorb - used <= 0.0F) STONE_SKIN.remove(player.getUUID()); else STONE_SKIN.put(player.getUUID(), absorb - used);
		}
		return amount;
	}

	public static boolean isInvulnerable(ServerPlayer player) {
		return PHASED_UNTIL.getOrDefault(player.getUUID(), 0L) > ((ServerLevel) player.level()).getGameTime();
	}

	private static boolean ready(ServerPlayer player, ShieldType shield, Slot slot, int seconds) {
		long now = ((ServerLevel) player.level()).getGameTime();
		Map<CooldownKey, Long> map = COOLDOWNS.get(player.getUUID());
		CooldownKey key = new CooldownKey(shield, slot);
		long readyAt = map == null ? 0L : map.getOrDefault(key, 0L);
		if (readyAt > now) {
			double remaining = (readyAt - now) / 20.0;
			player.sendOverlayMessage(Component.literal("Cooldown: %.1fs".formatted(remaining)).withStyle(ChatFormatting.RED));
			return false;
		}
		return true;
	}

	private static void complete(ServerPlayer player, ShieldType shield, Slot slot, int seconds, String abilityName, boolean ultimate) {
		long now = ((ServerLevel) player.level()).getGameTime();
		COOLDOWNS.computeIfAbsent(player.getUUID(), ignored -> new HashMap<>())
			.put(new CooldownKey(shield, slot), now + seconds * 20L);
		HonorShieldsPackets.cooldown(player, slot == Slot.ONE ? 1 : slot == Slot.TWO ? 2 : 3, abilityName, seconds);
		feedback(player, abilityName, ultimate);
	}

	public static void setAllCooldowns(ServerPlayer player, ShieldType shield, int seconds) {
		for (int slotNumber = 1; slotNumber <= 3; slotNumber++) {
			Slot slot = slotNumber == 1 ? Slot.ONE : slotNumber == 2 ? Slot.TWO : Slot.ULTIMATE;
			long now = ((ServerLevel) player.level()).getGameTime();
			CooldownKey key = new CooldownKey(shield, slot);
			Map<CooldownKey, Long> map = COOLDOWNS.computeIfAbsent(player.getUUID(), ignored -> new HashMap<>());
			map.put(key, Math.max(map.getOrDefault(key, 0L), now + seconds * 20L));
			String name = slot == Slot.ONE ? shield.abilityOne() : slot == Slot.TWO ? shield.abilityTwo() : shield.ultimate();
			HonorShieldsPackets.cooldown(player, slotNumber, name, (int) Math.ceil((map.get(key) - now) / 20.0));
		}
	}

	/** Adds time to every move without shortening an existing cooldown. */
	public static void addAllCooldowns(ServerPlayer player, ShieldType shield, int seconds) {
		for (int slotNumber = 1; slotNumber <= 3; slotNumber++) addCooldown(player, shield, slotNumber, seconds);
	}

	public static void addCooldown(ServerPlayer player, ShieldType shield, int slotNumber, int seconds) {
		Slot slot = slotNumber == 1 ? Slot.ONE : slotNumber == 2 ? Slot.TWO : Slot.ULTIMATE;
		long now = ((ServerLevel) player.level()).getGameTime();
		CooldownKey key = new CooldownKey(shield, slot);
		Map<CooldownKey, Long> map = COOLDOWNS.computeIfAbsent(player.getUUID(), ignored -> new HashMap<>());
		map.put(key, Math.max(map.getOrDefault(key, 0L), now) + seconds * 20L);
		String name = slot == Slot.ONE ? shield.abilityOne() : slot == Slot.TWO ? shield.abilityTwo() : shield.ultimate();
		HonorShieldsPackets.cooldown(player, slotNumber, name,
			(int) Math.ceil((map.get(key) - now) / 20.0));
	}

	/** Removes one move cooldown without touching the other two slots. */
	public static void clearCooldown(ServerPlayer player, ShieldType shield, int slotNumber) {
		Slot slot = slotNumber == 1 ? Slot.ONE : slotNumber == 2 ? Slot.TWO : Slot.ULTIMATE;
		Map<CooldownKey, Long> map = COOLDOWNS.get(player.getUUID());
		if (map == null) return;
		map.remove(new CooldownKey(shield, slot));
		String name = slot == Slot.ONE ? shield.abilityOne() : slot == Slot.TWO ? shield.abilityTwo() : shield.ultimate();
		HonorShieldsPackets.cooldown(player, slotNumber, name, 0);
	}

	private static ShieldCondition condition(ServerPlayer player) {
		return ((HonorPlayerData) player).honorshields$getShieldCondition();
	}

	private static boolean canUse(ServerPlayer player, ShieldType type) {
		if (!owns(player, type)) return false;
		if (!condition(player).usable()) {
			player.sendOverlayMessage(Component.literal("This Forsaken shield must be repaired.").withStyle(ChatFormatting.DARK_RED));
			return false;
		}
		return true;
	}

	private static boolean owns(ServerPlayer player, ShieldType type) {
		return ((HonorPlayerData) player).honorshields$getShieldType() == type && ShieldType.fromStack(player.getOffhandItem()) == type;
	}

	private static void feedback(ServerPlayer player, String name, boolean ultimate) {
		player.sendOverlayMessage(Component.literal((ultimate ? "ULTIMATE: " : "Ability: ") + name).withStyle(ultimate ? ChatFormatting.GOLD : ChatFormatting.AQUA));
	}

	private static List<LivingEntity> enemies(ServerPlayer player, double radius) {
		return enemiesAround(player, player.position(), radius);
	}

	private static List<ServerPlayer> trustedAllies(ServerPlayer player, double radius, boolean includeCaster) {
		double radiusSquared = radius * radius;
		return ((ServerLevel) player.level()).getPlayers(candidate ->
			(includeCaster && candidate == player) || candidate != player
				&& candidate.distanceToSqr(player) <= radiusSquared && TrustManager.isMutualTrust(player, candidate));
	}

	private static List<LivingEntity> enemiesAround(ServerPlayer player, Vec3 center, double radius) {
		AABB bounds = new AABB(center.x - radius, center.y - radius, center.z - radius,
			center.x + radius, center.y + radius, center.z + radius);
		return player.level().getEntitiesOfClass(LivingEntity.class, bounds, target ->
			target != player && target.isAlive() && target.position().distanceToSqr(center) <= radius * radius
				&& ShieldResourceManager.isEnemy(player, target));
	}

	private static List<LivingEntity> coneTargets(ServerPlayer player, double radius, double minDot) {
		Vec3 look = player.getLookAngle().normalize();
		return enemies(player, radius).stream().filter(target -> target.position().subtract(player.position()).normalize().dot(look) >= minDot).toList();
	}

	private static LivingEntity nearestInSight(ServerPlayer player, double radius) {
		return coneTargets(player, radius, 0.25).stream().min((a, b) -> Double.compare(a.distanceToSqr(player), b.distanceToSqr(player))).orElse(null);
	}

	private static void affectNearby(ServerPlayer player, double radius, java.util.function.Consumer<LivingEntity> effect) {
		enemies(player, radius).forEach(effect);
	}

	private static void affectNearby(ServerPlayer player, Vec3 center, double radius,
		java.util.function.Consumer<LivingEntity> effect) {
		enemiesAround(player, center, radius).forEach(effect);
	}

	/** Forces the vanilla frozen overlay/shiver state, never on the caster. */
	private static void applyVisibleFreeze(ServerPlayer caster, LivingEntity target, int lingeringTicks) {
		if (target == caster) return;
		int fullyFrozen = target.getTicksRequiredToFreeze();
		target.setTicksFrozen(Math.max(target.getTicksFrozen(), fullyFrozen + lingeringTicks));
	}

	private static void hurt(ServerPlayer player, LivingEntity target, float damage) {
		hurt(player, target, damage, "one");
	}

	private static void hurt(ServerPlayer player, LivingEntity target, float damage, String slot) {
		hurt(player, target, damage, slot, damageKind(player, slot));
	}

	private static void hurt(ServerPlayer player, LivingEntity target, float damage, String slot, AbilityDamage.Kind kind) {
		ServerLevel level = (ServerLevel) player.level();
		target.hurtServer(level, AbilityDamage.source(level, player, kind), scaledAbilityDamage(player, target, damage, slot));
	}

	private static AbilityDamage.Kind damageKind(ServerPlayer player, String slot) {
		boolean ultimate = "ultimate".equals(slot);
		ShieldType shield = ((HonorPlayerData) player).honorshields$getShieldType();
		if (shield == null) return AbilityDamage.Kind.GENERIC;
		return switch (shield) {
			case CINDER -> ultimate ? AbilityDamage.Kind.CINDER_INFERNO_AEGIS : AbilityDamage.Kind.CINDER_FLAME_BURST;
			case RIME -> ultimate ? AbilityDamage.Kind.RIME_PERMAFROST : AbilityDamage.Kind.RIME_FROST_NOVA;
			case TEMPEST -> ultimate ? AbilityDamage.Kind.TEMPEST_HURRICANE : AbilityDamage.Kind.TEMPEST_WIND_SLASH;
			case THUNDER -> ultimate ? AbilityDamage.Kind.THUNDER_STORM : AbilityDamage.Kind.THUNDER_CHAIN_LIGHTNING;
			case BOULDER -> ultimate ? AbilityDamage.Kind.BOULDER_EARTHQUAKE : AbilityDamage.Kind.BOULDER_STONE_THROW;
			case MONSOON -> ultimate ? AbilityDamage.Kind.MONSOON_WHIRLPOOL : AbilityDamage.Kind.MONSOON_TIDAL_WAVE;
			case VOID -> ultimate ? AbilityDamage.Kind.VOID_BLACK_HOLE : AbilityDamage.Kind.VOID_TENDRIL;
			case STONE -> ultimate ? AbilityDamage.Kind.STONE_VEIN_QUAKE : AbilityDamage.Kind.STONE_SEISMIC_SURVEY;
			case ANGLER -> AbilityDamage.Kind.ANGLER_FEEDING_FRENZY;
			case WARDEN -> ultimate ? AbilityDamage.Kind.WARDEN_LAST_STAND : AbilityDamage.Kind.GENERIC;
			default -> AbilityDamage.Kind.GENERIC;
		};
	}

	/** Active shield damage doubles only against Mob targets; PvP and all non-shield damage stay unchanged. */
	private static float scaledAbilityDamage(ServerPlayer player, LivingEntity target, float damage, String slot) {
		float conditionMultiplier = slot.equals("ultimate")
			? condition(player).ultimateMultiplier()
			: condition(player).abilityMultiplier();
		float targetMultiplier = target instanceof Mob ? 2.0F : 1.0F;
		return damage * conditionMultiplier * targetMultiplier;
	}

	private static void pushAway(Vec3 center, LivingEntity target, double strength, double y) {
		if (ShieldBlockingHandler.blocksKnockback(target)) return;
		Vec3 direction = target.position().subtract(center).normalize().scale(strength);
		target.setDeltaMovement(target.getDeltaMovement().add(direction.x, y, direction.z));
		target.hurtMarked = true;
	}

	private static void pullTo(Vec3 center, LivingEntity target, double strength) {
		if (ShieldBlockingHandler.blocksKnockback(target)) return;
		Vec3 direction = center.subtract(target.position()).normalize().scale(strength);
		target.setDeltaMovement(target.getDeltaMovement().add(direction.x, 0.15, direction.z));
		target.hurtMarked = true;
	}


	private static void chainLightning(ServerPlayer player) {
		int charge = ShieldResourceManager.thunderCharge(player);
		List<LivingEntity> candidates = new ArrayList<>(enemies(player, 12.0));
		// Static Charge empowers the cast by one heart, capped at the authored
		// three-heart maximum; it never creates an unbounded ordinary hit.
		float damage = charge > 0 ? 6.0F : 4.0F;
		if (charge > 0) ShieldResourceManager.consumeThunder(player);
		Vec3 origin = player.position();
		for (int i = 0; i < 3 && !candidates.isEmpty(); i++) {
			Vec3 point = origin;
			LivingEntity target = candidates.stream().min((a, b) -> Double.compare(a.position().distanceToSqr(point), b.position().distanceToSqr(point))).orElse(null);
			if (target == null) break;
			shock(player, target, damage, "one", origin);
			origin = target.position();
			candidates.remove(target);
		}
	}

	private static void shock(ServerPlayer player, LivingEntity target, float damage, String slot, Vec3 origin) {
		hurt(player, target, damage, slot);
		visualLightning(player, target, 1, origin);
	}

	private static void ultimateShock(ServerPlayer player, LivingEntity target, float damage, boolean detailedImpact) {
		float scaledDamage = scaledAbilityDamage(player, target, damage, "ultimate");
		ServerLevel level = (ServerLevel) player.level();
		target.hurtServer(level, AbilityDamage.source(level, player, AbilityDamage.Kind.THUNDER_STORM), scaledDamage);
		if (detailedImpact) {
			// Keep real visual-only lightning bounded in dense farms while damage
			// still resolves against every valid target in the ten-block storm.
			spawnVisualLightning(level, target.position());
			HonorShieldsPackets.abilityEffectFrom(player, ShieldType.THUNDER, 3, target, 2, target.position().add(0.0, 7.0, 0.0));
		}
	}

	private static void visualLightning(ServerPlayer player, LivingEntity target, int slot, Vec3 origin) {
		HonorShieldsPackets.abilityEffectFrom(player, ShieldType.THUNDER, slot, target, 2, origin);
		spawnVisualLightning((ServerLevel) player.level(), target.position());
	}

	/** Exalted three-hit presentation hook; it is visual-only and never adds damage. */
	public static void broadcastThunderStrike(ServerPlayer player, LivingEntity target) {
		if (target == null || !target.isAlive()) return;
		visualLightning(player, target, 4, player.position());
	}

	/**
	 * Adds the real vanilla lightning renderer and sound without a second damage source,
	 * fire, or lightning conversion. Ability damage remains authoritative in shock().
	 */
	private static void spawnVisualLightning(ServerLevel level, Vec3 strikePosition) {
		LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
		if (bolt == null) return;
		bolt.setVisualOnly(true);
		bolt.snapTo(strikePosition.x, strikePosition.y, strikePosition.z);
		level.addFreshEntity(bolt);
	}

	private static void powderField(ServerPlayer player) {
		ServerLevel level = (ServerLevel) player.level();
		BlockPos center = player.blockPosition().below();
		long expires = level.getGameTime() + RIME_FIELD_DURATION_TICKS;
		for (int x = -RIME_FIELD_RADIUS; x <= RIME_FIELD_RADIUS; x++) {
			for (int z = -RIME_FIELD_RADIUS; z <= RIME_FIELD_RADIUS; z++) {
			BlockPos pos = center.offset(x, 0, z);
			BlockState old = level.getBlockState(pos);
			if (!old.is(Blocks.POWDER_SNOW) && level.getBlockEntity(pos) == null
				&& old.getDestroySpeed(level, pos) >= 0.0F && level.mayInteract(player, pos)
				&& level.getWorldBorder().isWithinBounds(pos)) {
				BlockState replacement = Blocks.POWDER_SNOW.defaultBlockState();
				TEMP_BLOCKS.add(new TemporaryBlock(level, pos.immutable(), old, replacement, expires, player.getUUID()));
				level.setBlockAndUpdate(pos, replacement);
			}
			}
		}
	}

	private static void restoreTemporaryBlock(TemporaryBlock temp) {
		if (temp.level().getBlockState(temp.pos()).equals(temp.replacement()))
			temp.level().setBlockAndUpdate(temp.pos(), temp.oldState());
	}

	private static void placeCinderMagmaTrail(ServerPlayer player) {
		ServerLevel level = (ServerLevel) player.level();
		long expires = level.getGameTime() + 200L;
		BlockPos center = player.blockPosition();
		for (int x = -3; x < 3; x++) for (int z = -3; z < 3; z++) {
			BlockPos pos = findCinderSurface(player, level, center.getX() + x, center.getZ() + z, center.getY());
			if (pos == null || TEMP_BLOCKS.stream().anyMatch(temp -> temp.level() == level && temp.pos().equals(pos))) continue;
			BlockState old = level.getBlockState(pos);
			BlockState replacement = Blocks.MAGMA_BLOCK.defaultBlockState();
			TEMP_BLOCKS.add(new TemporaryBlock(level, pos.immutable(), old, replacement, expires, player.getUUID()));
			level.setBlockAndUpdate(pos, replacement);
		}
	}

	private static BlockPos findCinderSurface(ServerPlayer player, ServerLevel level, int x, int z, int referenceY) {
		for (int y = referenceY; y >= referenceY - 3; y--) {
			BlockPos pos = new BlockPos(x, y, z);
			BlockState state = level.getBlockState(pos);
			if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos) || state.isAir()
				|| !state.blocksMotion() || !state.getFluidState().isEmpty() || state.hasBlockEntity()
				|| state.getDestroySpeed(level, pos) < 0.0F || !level.mayInteract(player, pos)
				|| level.getBlockState(pos.above()).blocksMotion()) continue;
			return pos;
		}
		return null;
	}

	/**
	 * Builds an outward Midas-style wave from real surface blocks. Each selected
	 * source state is temporarily replaced with air using client-update-only flags,
	 * carried by a server-tracked BlockDisplay, then restored after it settles.
	 * Sparse radial lanes and a two-block center exclusion keep the caster's footing
	 * safe; block entities, fluids, protected blocks, and unbreakable states are never
	 * touched.
	 */
	public static void startEarthquakeReplica(ServerPlayer player, double radius) {
		startEarthquake(player, radius);
	}

	private static void startEarthquake(ServerPlayer player, double radius) {
		ServerLevel level = (ServerLevel) player.level();
		long startTick = level.getGameTime();
		int centerY = player.blockPosition().getY();
		int castLimit = Math.min(EARTHQUAKE_MAX_BLOCKS,
			Math.max(0, EARTHQUAKE_GLOBAL_MAX_LIFTS - EARTHQUAKE_LIFTS.size()));
		if (castLimit == 0) return;
		Set<BlockPos> selected = new HashSet<>();
		Set<Long> occupiedColumns = new HashSet<>();
		for (EarthquakeLift lift : EARTHQUAKE_LIFTS) if (lift.level == level) {
			occupiedColumns.add(horizontalKey(lift.pos.getX(), lift.pos.getZ()));
		}
		int blockRadius = (int) Math.ceil(radius);
		for (int offsetX = -blockRadius; offsetX <= blockRadius && selected.size() < castLimit; offsetX++) {
			for (int offsetZ = -blockRadius; offsetZ <= blockRadius && selected.size() < castLimit; offsetZ++) {
				double centerOffsetX = offsetX + 0.5;
				double centerOffsetZ = offsetZ + 0.5;
				double distanceSquared = centerOffsetX * centerOffsetX + centerOffsetZ * centerOffsetZ;
				if (distanceSquared > radius * radius) continue;
				int blockX = player.blockPosition().getX() + offsetX;
				int blockZ = player.blockPosition().getZ() + offsetZ;
				if (occupiedColumns.contains(horizontalKey(blockX, blockZ))) continue;
				BlockPos pos = findEarthquakeSurface(player, level, blockX, blockZ, centerY);
				if (pos == null || !selected.add(pos)) continue;
				// Never animate the block currently supporting the caster. It remains
				// solid either way, but lifting a duplicate through their feet looks awful.
				AABB blockBounds = new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
				if (player.getBoundingBox().intersects(blockBounds)) { selected.remove(pos); continue; }
				occupiedColumns.add(horizontalKey(pos.getX(), pos.getZ()));
				BlockState state = level.getBlockState(pos);
				double distance = Math.sqrt(distanceSquared);
				double outwardX = distance < 0.001 ? 0.0 : centerOffsetX / distance;
				double outwardZ = distance < 0.001 ? 0.0 : centerOffsetZ / distance;
				double height = 0.82 + player.getRandom().nextDouble() * 0.32;
				EARTHQUAKE_LIFTS.add(new EarthquakeLift(level, pos.immutable(), state, player.getUUID(),
					startTick, Math.min(10, (int) Math.floor(distance)), outwardX, outwardZ, height));
			}
		}
	}

	private static BlockPos findEarthquakeSurface(ServerPlayer player, ServerLevel level, int blockX, int blockZ, int referenceY) {
		for (int y = referenceY + 2; y >= referenceY - 5; y--) {
			BlockPos pos = new BlockPos(blockX, y, blockZ);
			if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos)) continue;
			BlockState state = level.getBlockState(pos);
			if (state.isAir() || !state.blocksMotion() || !state.getFluidState().isEmpty() || state.hasBlockEntity()
				|| state.getDestroySpeed(level, pos) < 0.0F || !level.mayInteract(player, pos)
				|| !level.getBlockState(pos.above()).isAir()) continue;
			return pos;
		}
		return null;
	}

	private static long horizontalKey(int x, int z) {
		return (long) x << 32 ^ z & 0xFFFFFFFFL;
	}

	private static void tickEarthquakeLifts() {
		Iterator<EarthquakeLift> lifts = EARTHQUAKE_LIFTS.iterator();
		while (lifts.hasNext()) {
			EarthquakeLift lift = lifts.next();
			long localAge = lift.level.getGameTime() - lift.startTick - lift.delay;
			if (localAge < 0L) continue;
			if (!lift.lifted && !beginEarthquakeLift(lift)) {
				lifts.remove();
				continue;
			}
			if (localAge >= EARTHQUAKE_LIFT_TICKS || lift.display == null || lift.display.isRemoved()) {
				restoreEarthquakeLift(lift);
				lifts.remove();
				continue;
			}
			double progress = localAge / Math.max(1.0, EARTHQUAKE_LIFT_TICKS - 1.0);
			double arc = Math.sin(Math.PI * progress);
			double drift = arc * 0.16;
			lift.display.setPos(lift.pos.getX() + lift.outwardX * drift,
				lift.pos.getY() + arc * lift.height, lift.pos.getZ() + lift.outwardZ * drift);
		}
	}

	private static boolean beginEarthquakeLift(EarthquakeLift lift) {
		if (!lift.level.hasChunkAt(lift.pos) || !lift.level.getBlockState(lift.pos).equals(lift.state)
			|| lift.level.getBlockEntity(lift.pos) != null) return false;
		Display.BlockDisplay display = new Display.BlockDisplay(EntityTypes.BLOCK_DISPLAY, lift.level);
		display.setBlockState(lift.state);
		display.setNoGravity(true);
		display.noPhysics = true;
		display.setInvulnerable(true);
		display.setViewRange(1.5F);
		display.setShadowRadius(0.65F);
		display.setShadowStrength(0.9F);
		display.setPosRotInterpolationDuration(2);
		display.snapTo(lift.pos.getX(), lift.pos.getY(), lift.pos.getZ());
		display.addTag("honorshields_earthquake");
		if (!lift.level.addFreshEntity(display)) return false;
		// The animated display is deliberately cosmetic. Keep the source block in
		// place so its collision never disappears under a player during a quake.
		// That prevents a player falling into the temporary gap and being trapped
		// when the visual settles.
		lift.display = display;
		lift.lifted = true;
		return true;
	}

	private static void restoreEarthquakeLift(EarthquakeLift lift) {
		if (lift.display != null && !lift.display.isRemoved()) lift.display.discard();
		if (!lift.lifted) return;
		// No world block was removed: only the short-lived display needs cleanup.
	}

	private static void summonWolves(ServerPlayer player) {
		ServerLevel level = (ServerLevel) player.level();
		Vec3 look = player.getLookAngle();
		Vec3 horizontalRight = new Vec3(-look.z, 0.0, look.x);
		Vec3 right = horizontalRight.lengthSqr() < 1.0E-8 ? new Vec3(1.0, 0.0, 0.0) : horizontalRight.normalize();
		for (int i = 0; i < 2; i++) {
			Wolf wolf = net.minecraft.world.entity.EntityTypes.WOLF.create(level, EntitySpawnReason.COMMAND);
			if (wolf != null) {
				Vec3 spawn = player.position().add(right.scale(i == 0 ? 1.15 : -1.15));
				wolf.snapTo(spawn.x, spawn.y, spawn.z);
				wolf.tame(player);
				level.addFreshEntity(wolf);
				SCHEDULED.add(new Scheduled(level.getGameTime() + OAK_WOLF_LIFETIME_TICKS, wolf::discard));
			}
		}
	}

	private static void veinSense(ServerPlayer player, int radius, boolean announce) {
		ServerLevel level = (ServerLevel) player.level();
		List<BlockPos> ores;
		if (announce) {
			ores = scanOres(level, player.blockPosition(), radius, 240);
			VEIN_SENSE_ORES.put(player.getUUID(), new VeinSenseCache(level, ores));
		} else {
			VeinSenseCache cache = VEIN_SENSE_ORES.get(player.getUUID());
			ores = cache != null && cache.level() == level ? cache.ores() : List.of();
		}
		// The single ability event above drives the client scan geometry. Do not send
		// one packet per ore (or server particles): nearby viewers render the same
		// bounded scan while retaining their own effects and density settings.
		if (announce) player.sendOverlayMessage(Component.literal("Vein Sense: " + ores.size()
			+ (ores.size() == 240 ? "+" : "") + " ores tracked for 10 seconds").withStyle(ChatFormatting.GOLD));
	}

	private static List<BlockPos> scanOres(ServerLevel level, BlockPos center, int radius, int limit) {
		List<BlockPos> ores = new ArrayList<>();
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius))) {
			if (ores.size() >= limit) break;
			if (center.distSqr(pos) > (long) radius * radius) continue;
			if (level.getBlockState(pos).is(ConventionalBlockTags.ORES)) ores.add(pos.immutable());
		}
		return ores;
	}

	private static void summonAttackFish(ServerPlayer player) {
		ServerLevel level = (ServerLevel) player.level();
		List<LivingEntity> targets = enemies(player, 9.0);
		for (int i = 0; i < 5; i++) {
			Pufferfish fish = net.minecraft.world.entity.EntityTypes.PUFFERFISH.create(level, EntitySpawnReason.COMMAND);
			if (fish == null) continue;
			double angle = Math.PI * 2.0 * i / 5.0;
			fish.snapTo(player.getX() + Math.cos(angle) * 1.6, player.getY() + 1.0, player.getZ() + Math.sin(angle) * 1.6);
			fish.setPuffState(2);
			if (!targets.isEmpty()) {
				LivingEntity target = targets.get(i % targets.size());
				Vec3 motion = target.position().subtract(fish.position()).normalize().scale(0.55);
				fish.setDeltaMovement(motion.x, 0.2, motion.z);
			}
			level.addFreshEntity(fish);
			SCHEDULED.add(new Scheduled(level.getGameTime() + 80, fish::discard));
		}
	}

	private static void veinBurst(ServerPlayer player, int radius) {
		ServerLevel level = (ServerLevel) player.level();
		BlockPos center = player.blockPosition();
		List<BlockPos> ores = new ArrayList<>();
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius))) {
			if (center.distSqr(pos) <= radius * radius && level.hasChunkAt(pos)
				&& level.mayInteract(player, pos) && level.getBlockState(pos).is(ConventionalBlockTags.ORES)) {
				ores.add(pos.immutable());
			}
		}
		ores.sort((left, right) -> Double.compare(center.distSqr(left), center.distSqr(right)));
		for (BlockPos pos : ores) {
			BlockState state = level.getBlockState(pos);
			if (!player.hasCorrectToolForDrops(state)) continue;
			ItemStack tool = player.getMainHandItem().copy();
			List<ItemStack> drops = Block.getDrops(state, level, pos, level.getBlockEntity(pos), player, tool);
			if (!level.destroyBlock(pos, false, player)) continue;
			state.spawnAfterBreak(level, pos, tool, true);
			for (ItemStack stack : drops) player.getInventory().placeItemBackInInventory(stack.copy());
		}
	}

	private static boolean hasVeinBurstTarget(ServerPlayer player, int radius) {
		ServerLevel level = (ServerLevel) player.level();
		BlockPos center = player.blockPosition();
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius))) {
			if (center.distSqr(pos) > (long) radius * radius || !level.hasChunkAt(pos) || !level.mayInteract(player, pos)) continue;
			BlockState state = level.getBlockState(pos);
			if (state.is(ConventionalBlockTags.ORES) && player.hasCorrectToolForDrops(state)) return true;
		}
		return false;
	}

	private static void regrow(ServerPlayer player, int radius, int passes) {
		ServerLevel level = (ServerLevel) player.level();
		BlockPos center = player.blockPosition();
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -2, -radius), center.offset(radius, 4, radius))) {
			BlockState state = level.getBlockState(pos);
			if (state.isRandomlyTicking()) for (int i = 0; i < passes; i++) state.randomTick(level, pos, level.getRandom());
		}
	}

	private static void harvest(ServerPlayer player, int radius, boolean bountiful) {
		ServerLevel level = (ServerLevel) player.level();
		BlockPos center = player.blockPosition();
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -2, -radius), center.offset(radius, 3, radius))) {
			long dx = pos.getX() - center.getX();
			long dz = pos.getZ() - center.getZ();
			if (dx * dx + dz * dz > (long) radius * radius) continue;
			BlockState state = level.getBlockState(pos);
			if (!state.is(BlockTags.CROPS)) continue;
			if (bountiful) for (int i = 0; i < 12; i++) state.randomTick(level, pos, level.getRandom());
			BlockState grown = level.getBlockState(pos);
			if (grown.is(BlockTags.CROPS)) {
				List<ItemStack> bonus = bountiful
					? Block.getDrops(grown, level, pos, level.getBlockEntity(pos), player, player.getMainHandItem())
					: List.of();
				level.destroyBlock(pos, true, player);
				for (ItemStack stack : bonus) Block.popResource(level, pos, stack.copy());
			}
		}
	}

	private static boolean hasHarvestTarget(ServerPlayer player, int radius) {
		ServerLevel level = (ServerLevel) player.level();
		BlockPos center = player.blockPosition();
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -2, -radius), center.offset(radius, 3, radius))) {
			long dx = pos.getX() - center.getX();
			long dz = pos.getZ() - center.getZ();
			if (dx * dx + dz * dz <= (long) radius * radius && level.getBlockState(pos).is(BlockTags.CROPS)) return true;
		}
		return false;
	}

	private static void applyCinderBurn(ServerPlayer player, LivingEntity target, float seconds, String slot) {
		target.igniteForSeconds(seconds);
		long now = ((ServerLevel) player.level()).getGameTime();
		BurnKey key = new BurnKey(player.getUUID(), target.getUUID());
		CinderBurn existing = CINDER_BURNS.get(key);
		long nextPulse = existing == null ? now + 20 : existing.nextPulse();
		long expires = Math.max(existing == null ? 0 : existing.expires(), now + (long) Math.ceil(seconds * 20.0F) + 1);
		CINDER_BURNS.put(key, new CinderBurn((ServerLevel) target.level(), expires, nextPulse, slot));
	}

	private static void tickCinderBurns(MinecraftServer server, long now) {
		Iterator<Map.Entry<BurnKey, CinderBurn>> burns = CINDER_BURNS.entrySet().iterator();
		while (burns.hasNext()) {
			Map.Entry<BurnKey, CinderBurn> entry = burns.next();
			CinderBurn burn = entry.getValue();
			ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey().owner());
			Entity entity = burn.level().getEntityInAnyDimension(entry.getKey().target());
			if (owner == null || !(entity instanceof LivingEntity target) || !target.isAlive() || now >= burn.expires()) {
				burns.remove();
				continue;
			}
			if (now >= burn.nextPulse()) {
				// Demon Core's authored burn is independent of vanilla Fire Resistance.
				// Do not remove the target's effect; simply use the ability damage source.
				hurt(owner, target, 1.0F, burn.slot());
				entry.setValue(new CinderBurn((ServerLevel) target.level(), burn.expires(), now + 20, burn.slot()));
			}
		}
	}

	private ShieldAbilityHandler() {}
}
