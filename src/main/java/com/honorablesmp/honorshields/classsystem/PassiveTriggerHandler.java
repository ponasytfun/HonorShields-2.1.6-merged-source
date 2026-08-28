package com.honorablesmp.honorshields.classsystem;

import com.honorablesmp.honorshields.data.HonorPlayerData;
import com.honorablesmp.honorshields.network.HonorShieldsPackets;
import com.honorablesmp.honorshields.shield.AbilityDamage;
import com.honorablesmp.honorshields.shield.ShieldType;
import com.honorablesmp.honorshields.shield.ShieldCondition;
import com.honorablesmp.honorshields.shield.SeasonTwoGameplay;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class PassiveTriggerHandler {
	private static final Identifier ROGUE_STEALTH_ARMOR_ID = Identifier.fromNamespaceAndPath("honorable-smp", "rogue_stealth_armor");
	private static final Identifier MONSOON_DEPTH_STRIDER_ID = Identifier.fromNamespaceAndPath("honorable-smp", "monsoon_depth_strider");
	private static final AttributeModifier ROGUE_SNEAK_SPEED = new AttributeModifier(
		RogueStealthState.SNEAK_SPEED_MODIFIER_ID, 0.7D, AttributeModifier.Operation.ADD_VALUE
	);
	private static final AttributeModifier MONSOON_DEPTH_STRIDER = new AttributeModifier(
		MONSOON_DEPTH_STRIDER_ID, 1.0D, AttributeModifier.Operation.ADD_VALUE
	);
	private static final double ROGUE_STEALTH_ARMOR_POINTS = 0.0D;
	private static final Set<UUID> ROGUE_STEALTH_ACTIVE = new HashSet<>();
	private static final Map<UUID, Boolean> WAS_IN_WATER = new HashMap<>();
	private static final Map<UUID, Boolean> DEEP_ACTIVE = new HashMap<>();
	private static final Map<UUID, Long> HEMORRHAGE_READY = new HashMap<>();
	private static final Map<UUID, Hemorrhage> HEMORRHAGES = new HashMap<>();
	private record Hemorrhage(UUID owner, long expires, long nextTick) { }

	public static void registerEvents() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register(PassiveTriggerHandler::onAfterDamage);
		ServerLivingEntityEvents.AFTER_DEATH.register(PassiveTriggerHandler::onDeath);
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (!(player instanceof ServerPlayer serverPlayer) || level.isClientSide()) return InteractionResult.PASS;
			if (((HonorPlayerData) serverPlayer).honorshields$getClassType() != ClassType.FARMER || !player.getItemInHand(hand).is(Items.BONE_MEAL)) return InteractionResult.PASS;
			BlockPos pos = hit.getBlockPos();
			boolean grew = false;
			for (int i = 0; i < 12; i++) grew |= BoneMealItem.growCrop(new ItemStack(Items.BONE_MEAL), level, pos);
			if (grew) {
				if (!player.isCreative()) player.getItemInHand(hand).shrink(1);
				triggerAt(serverPlayer, ClassType.FARMER, "Green Thumb", blockCenter(pos), serverPlayer.getLookAngle());
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (player instanceof ServerPlayer serverPlayer && level instanceof ServerLevel serverLevel)
				SeasonTwoGameplay.onBlockBreak(serverLevel, serverPlayer, pos, state, blockEntity);
		});
	}

	public static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		tickHemorrhages(server, now);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			HonorPlayerData data = (HonorPlayerData) player;
			ClassType type = data.honorshields$getClassType();
			ShieldType shield = data.honorshields$getShieldType();
			ShieldCondition shieldCondition = data.honorshields$getShieldCondition();
			boolean shieldEquipped = shield != null && ShieldType.fromStack(player.getOffhandItem()) == shield && shieldCondition.usable();
			updateMonsoonDepthStrider(player, shieldEquipped && shield == ShieldType.MONSOON);
			if (type == null) {
				clearRogueState(player);
				continue;
			}
			if (now % 20 == 0) tickClass(player, type, now);
			tickContinuous(player, type, now);
			if (now % 20 == 0) tickShieldPassive(player, shieldEquipped ? shield : null, now);
		}
	}

	/** Exalted Berserker axe critical: 30 seconds of true bleeding, once per minute. */
	public static void tryHemorrhage(ServerPlayer owner, LivingEntity target) {
		if (!target.isAlive() || target == owner) return;
		ServerLevel level = (ServerLevel) owner.level();
		long now = level.getGameTime();
		if (HEMORRHAGE_READY.getOrDefault(owner.getUUID(), 0L) > now) return;
		HEMORRHAGE_READY.put(owner.getUUID(), now + 1_200L);
		HEMORRHAGES.put(target.getUUID(), new Hemorrhage(owner.getUUID(), now + 600L, now + 40L));
		triggerAt(owner, ClassType.BERSERKER, "Hemorrhage", target.position().add(0.0, target.getBbHeight() * 0.5, 0.0),
			target.position().subtract(owner.position()));
	}

	private static void tickHemorrhages(MinecraftServer server, long now) {
		Iterator<Map.Entry<UUID, Hemorrhage>> iterator = HEMORRHAGES.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<UUID, Hemorrhage> entry = iterator.next();
			Hemorrhage hemorrhage = entry.getValue();
			ServerPlayer owner = server.getPlayerList().getPlayer(hemorrhage.owner());
			if (owner == null || now >= hemorrhage.expires()) { iterator.remove(); continue; }
			var entity = owner.level().getEntity(entry.getKey());
			if (!(entity instanceof LivingEntity target) || !target.isAlive()) { iterator.remove(); continue; }
			if (now < hemorrhage.nextTick()) continue;
			target.hurtServer((ServerLevel) owner.level(), AbilityDamage.source((ServerLevel) owner.level(), owner,
				AbilityDamage.Kind.BERSERKER_HEMORRHAGE), 2.0F);
			if (target instanceof ServerPlayer victim) {
				float saturation = victim.getFoodData().getSaturationLevel();
				if (saturation > 0.0F) victim.getFoodData().setSaturation(Math.max(0.0F, saturation - 2.0F));
				else victim.getFoodData().setFoodLevel(Math.max(0, victim.getFoodData().getFoodLevel() - 2));
			}
			HEMORRHAGES.put(entry.getKey(), new Hemorrhage(hemorrhage.owner(), hemorrhage.expires(), now + 40L));
		}
	}

	private static void tickClass(ServerPlayer player, ClassType type, long now) {
		switch (type) {
			case ROGUE -> { }
			case BERSERKER -> { }
			case MERCHANT -> EffectStacking.applyContinuous(player, MobEffects.HERO_OF_THE_VILLAGE, 60, 1, "merchant_hero");
			case MINER -> {
				boolean deep = player.getY() < 32;
				if (deep) EffectStacking.applyContinuous(player, MobEffects.HASTE, 45, 0, "miner_haste");
				else EffectStacking.clearSource(player, "miner_haste");
				if (deep && !DEEP_ACTIVE.getOrDefault(player.getUUID(), false)) trigger(player, type, "Deep Delver");
				DEEP_ACTIVE.put(player.getUUID(), deep);
			}
			case FARMER -> { }
			case DROWNED -> {
				if (player.isInWater()) {
					EffectStacking.applyContinuous(player, MobEffects.SPEED, 40, 0, "drowned_water_speed");
					EffectStacking.applyContinuous(player, MobEffects.CONDUIT_POWER, 40, 0, "drowned_conduit");
				} else {
					EffectStacking.clearSource(player, "drowned_water_speed");
					EffectStacking.clearSource(player, "drowned_conduit");
				}
				boolean wasWater = WAS_IN_WATER.getOrDefault(player.getUUID(), player.isInWater());
				if (wasWater && !player.isInWater()) {
					EffectStacking.applyOnce(player, MobEffects.SPEED, 200, 0);
					trigger(player, type, "Tide Walker");
				}
				WAS_IN_WATER.put(player.getUUID(), player.isInWater());
			}
		}
	}

	private static void tickContinuous(ServerPlayer player, ClassType type, long now) {
		if (type != ClassType.ROGUE) {
			clearRogueState(player);
			return;
		}

		var sneakingSpeed = player.getAttribute(Attributes.SNEAKING_SPEED);
		if (sneakingSpeed != null && !sneakingSpeed.hasModifier(RogueStealthState.SNEAK_SPEED_MODIFIER_ID)) {
			sneakingSpeed.addTransientModifier(ROGUE_SNEAK_SPEED);
		}

		UUID id = player.getUUID();
		if (player.isCrouching()) {
			if (ROGUE_STEALTH_ACTIVE.add(id)) trigger(player, type, "Shadowmeld");
			MobEffectInstance invisibility = player.getEffect(MobEffects.INVISIBILITY);
			if (invisibility == null || invisibility.getDuration() < 10) {
				player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 40, 0, false, true, false));
			}
			updateRogueStealthArmor(player);
			// The one-shot Shadowmeld passive packet supplies the stealth visual. Avoid
			// server-side particles here so every client can honor its own effects and
			// density settings.
		} else {
			removeRogueStealth(player);
		}
	}

	private static void updateRogueStealthArmor(ServerPlayer player) {
		var armor = player.getAttribute(Attributes.ARMOR);
		if (armor == null || Math.abs(armor.getValue() - ROGUE_STEALTH_ARMOR_POINTS) < 1.0E-4D) return;
		armor.removeModifier(ROGUE_STEALTH_ARMOR_ID);
		double adjustment = ROGUE_STEALTH_ARMOR_POINTS - armor.getValue();
		armor.addTransientModifier(new AttributeModifier(ROGUE_STEALTH_ARMOR_ID, adjustment, AttributeModifier.Operation.ADD_VALUE));
	}

	private static void clearRogueState(ServerPlayer player) {
		var sneakingSpeed = player.getAttribute(Attributes.SNEAKING_SPEED);
		if (sneakingSpeed != null) sneakingSpeed.removeModifier(RogueStealthState.SNEAK_SPEED_MODIFIER_ID);
		removeRogueStealth(player);
	}

	private static void removeRogueStealth(ServerPlayer player) {
		var armor = player.getAttribute(Attributes.ARMOR);
		if (armor != null) armor.removeModifier(ROGUE_STEALTH_ARMOR_ID);
		if (!ROGUE_STEALTH_ACTIVE.remove(player.getUUID())) return;
		MobEffectInstance invisibility = player.getEffect(MobEffects.INVISIBILITY);
		if (invisibility != null && invisibility.getAmplifier() == 0
			&& invisibility.getDuration() <= 40) {
			player.removeEffect(MobEffects.INVISIBILITY);
		}
	}

	private static void tickShieldPassive(ServerPlayer player, ShieldType shield, long now) {
		if (shield == null) return;
		ShieldCondition condition = ((HonorPlayerData) player).honorshields$getShieldCondition();
		if (!condition.usable()) return;
		float strength = condition.passiveMultiplier();
		ServerLevel level = (ServerLevel) player.level();
		switch (shield) {
			case CINDER -> {
				if (player.isInLava()) player.heal(0.25F * strength);
				BlockPos center = player.blockPosition();
				for (BlockPos pos : BlockPos.betweenClosed(center.offset(-2, -1, -2), center.offset(2, 1, 2))) {
					BlockState state = level.getBlockState(pos);
					if ((state.is(Blocks.ICE) || state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK)) && player.getRandom().nextInt(20) == 0) level.destroyBlock(pos, false);
				}
			}
			case RIME -> {
				BlockState feet = level.getBlockState(player.blockPosition());
				BlockState below = level.getBlockState(player.blockPosition().below());
				boolean restorativeSurface = isRimeHealingBlock(feet) || isRimeHealingBlock(below);
				if (player.isInPowderSnow) {
					player.removeEffect(MobEffects.SLOWNESS);
					player.setIsInPowderSnow(false);
					player.heal(0.25F * strength);
				} else if (restorativeSurface) player.heal(0.25F * strength);
			}
			case TEMPEST -> EffectStacking.applyContinuous(player, MobEffects.SPEED, 40, 0, "tempest_lightfoot");
			case THUNDER -> { }
			case DAWN -> {
				player.heal(0.15F * strength);
				for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(4.0), entity -> entity != player && entity.isInvertedHealAndHarm())) {
					float mobMultiplier = target instanceof Mob ? 2.0F : 1.0F;
					target.hurtServer(level, AbilityDamage.source(level, player, AbilityDamage.Kind.DAWN_RADIANT_PASSIVE),
						1.0F * strength * mobMultiplier);
				}
				for (ServerPlayer ally : level.getPlayers(candidate -> candidate != player && candidate.distanceToSqr(player) <= 16.0 && TrustManager.isMutualTrust(player, candidate))) ally.heal(0.1F * strength);
			}
			case BOULDER -> { }
			case MONSOON -> {
				if (player.isInWater()) EffectStacking.applyContinuous(player, MobEffects.REGENERATION, 40, 0, "monsoon_regeneration");
			}
			case VOID -> { }
			case OAK -> {
				for (Animal animal : level.getEntitiesOfClass(Animal.class, player.getBoundingBox().inflate(8.0), Animal::isAlive)) {
					if (animal.distanceToSqr(player) > 4.0) animal.getNavigation().moveTo(player, 1.05);
				}
				for (net.minecraft.world.entity.animal.wolf.Wolf wolf : level.getEntitiesOfClass(net.minecraft.world.entity.animal.wolf.Wolf.class, player.getBoundingBox().inflate(12.0))) {
					if (wolf.getTarget() == player) wolf.setTarget(null);
				}
			}
			case STONE -> {
				if (player.getY() < 32.0) EffectStacking.applyContinuous(player, MobEffects.HASTE, 40, 0, "stone_haste");
			}
			case PLOW -> {
				if (!player.isShiftKeyDown() || now % 20 != 0) break;
				BlockPos center = player.blockPosition();
				int verdancy = ((HonorPlayerData) player).honorshields$getVerdancy();
				int chance = verdancy == 100 ? 100 : verdancy >= 50 ? 50 : 25;
				for (BlockPos pos : BlockPos.betweenClosed(center.offset(-3, -1, -3), center.offset(3, 1, 3))) {
					BlockState state = level.getBlockState(pos);
					if (state.getBlock() instanceof net.minecraft.world.level.block.CropBlock crop && !crop.isMaxAge(state)
						&& player.getRandom().nextInt(100) < chance) {
						level.setBlock(pos, state.setValue(net.minecraft.world.level.block.CropBlock.AGE,
							Math.min(crop.getMaxAge(), state.getValue(net.minecraft.world.level.block.CropBlock.AGE) + 1)), 3);
						HonorShieldsPackets.abilityEffectFrom(player, ShieldType.PLOW, 0, null, 1, Vec3.atCenterOf(pos));
					}
				}
			}
			case ANGLER -> { }
			case VAGABOND -> {
				if (level.getBlockState(player.blockPosition().below()).is(Blocks.DIRT_PATH)) EffectStacking.applyContinuous(player, MobEffects.RESISTANCE, 30, 0, "vagabond_path");
			}
			case WARDEN -> { }
		}
	}

	private static void updateMonsoonDepthStrider(ServerPlayer player, boolean active) {
		var waterMovement = player.getAttribute(Attributes.WATER_MOVEMENT_EFFICIENCY);
		if (waterMovement == null) return;
		if (active) {
			if (!waterMovement.hasModifier(MONSOON_DEPTH_STRIDER_ID)) waterMovement.addTransientModifier(MONSOON_DEPTH_STRIDER);
		} else {
			waterMovement.removeModifier(MONSOON_DEPTH_STRIDER_ID);
		}
	}

	private static boolean isRimeHealingBlock(BlockState state) {
		return state.is(Blocks.SNOW) || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.ICE)
			|| state.is(Blocks.PACKED_ICE) || state.is(Blocks.BLUE_ICE);
	}

	private static void onDeath(LivingEntity dead, net.minecraft.world.damagesource.DamageSource source) {
		SeasonTwoGameplay.onLivingDeath(dead, source);
		if (!(source.getEntity() instanceof ServerPlayer killer)) return;
		ClassType type = ((HonorPlayerData) killer).honorshields$getClassType();
		if (type == null) return;
		Vec3 deathOrigin = dead.position().add(0.0, dead.getBbHeight() * 0.52, 0.0);
		Vec3 attackDirection = deathOrigin.subtract(killer.position());
		switch (type) {
			case ROGUE -> {
				EffectStacking.applyOnce(killer, MobEffects.SPEED, 100, 0);
				triggerAt(killer, type, "Shadow's Grace", deathOrigin, attackDirection);
			}
			case BERSERKER -> {
				killer.getFoodData().eat(1, 1.0F);
				triggerAt(killer, type, "Unending Fury", deathOrigin, attackDirection);
			}
			case DROWNED -> {
				if (dead instanceof Drowned && killer.getRandom().nextFloat() < 0.10F) {
					dead.spawnAtLocation((ServerLevel) dead.level(), new ItemStack(Items.TRIDENT));
					triggerAt(killer, type, "Trident Hunter", deathOrigin, attackDirection);
				}
			}
			default -> { }
		}
	}

	private static void onAfterDamage(LivingEntity damaged, net.minecraft.world.damagesource.DamageSource source,
		float baseDamage, float damageTaken, boolean blocked) {
		if (!(damaged instanceof ServerPlayer player) || !player.isAlive() || damageTaken <= 0.0F
			|| ((HonorPlayerData) player).honorshields$getClassType() != ClassType.BERSERKER) return;
		float healthAfter = player.getHealth();
		float healthBefore = healthAfter + damageTaken;
		if (healthAfter >= 12.0F || healthBefore < 12.0F) return;
		long now = ((ServerLevel) player.level()).getGameTime();
		HonorPlayerData data = (HonorPlayerData) player;
		if (now < data.honorshields$getBerserkerResolveReadyAt()) return;
		data.honorshields$setBerserkerResolveReadyAt(now + 1_200L);
		EffectStacking.applyOnce(player, MobEffects.RESISTANCE, 200, 1);
		trigger(player, ClassType.BERSERKER, "Iron Resolve");
	}

	public static void onFoodConsumed(ServerPlayer player, ItemStack consumed, float saturationBefore) {
		ClassType type = ((HonorPlayerData) player).honorshields$getClassType();
		if (type == ClassType.BERSERKER && consumed.is(Items.GOLDEN_APPLE)) {
			MobEffectInstance absorption = player.getEffect(MobEffects.ABSORPTION);
			// Vanilla's golden apple already grants Absorption I. Berserker adds its
			// authored two hearts by raising that one effect to Absorption II, but a
			// later apple must never keep increasing the amplifier indefinitely.
			int duration = absorption == null ? 2_400 : Math.max(2_400, absorption.getDuration());
			int amplifier = absorption == null ? 1 : Math.max(1, absorption.getAmplifier());
			player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, duration, amplifier));
			trigger(player, ClassType.BERSERKER, "Golden Vitality");
			return;
		}
		if (type != ClassType.FARMER) return;
		boolean basic = consumed.is(Items.BREAD) || consumed.is(Items.CARROT) || consumed.is(Items.POTATO)
			|| consumed.is(Items.BAKED_POTATO) || consumed.is(Items.MUSHROOM_STEW);
		boolean golden = consumed.is(Items.GOLDEN_CARROT) || consumed.is(Items.GOLDEN_APPLE) || consumed.is(Items.ENCHANTED_GOLDEN_APPLE);
		if (!basic && !golden) return;
		float gained = Math.max(0.0F, player.getFoodData().getSaturationLevel() - saturationBefore);
		player.getFoodData().setSaturation(Math.min(player.getFoodData().getFoodLevel(), player.getFoodData().getSaturationLevel() + gained));
	}

	public static void trigger(ServerPlayer player, ClassType type, String title) {
		HonorShieldsPackets.passive(player, type, title);
	}

	public static void triggerAt(ServerPlayer player, ClassType type, String title, Vec3 origin, Vec3 direction) {
		HonorShieldsPackets.passiveAt(player, type, title, origin, direction);
	}

	public static void resetPlayer(ServerPlayer player) {
		UUID id = player.getUUID();
		clearRogueState(player);
		WAS_IN_WATER.remove(id);
		DEEP_ACTIVE.remove(id);
		HEMORRHAGE_READY.remove(id);
		HEMORRHAGES.remove(id);
		HEMORRHAGES.entrySet().removeIf(entry -> entry.getValue().owner().equals(id));
		updateMonsoonDepthStrider(player, false);
		EffectStacking.clear(player);
	}

	private static Vec3 blockCenter(BlockPos pos) {
		return new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
	}

	private PassiveTriggerHandler() {}
}
