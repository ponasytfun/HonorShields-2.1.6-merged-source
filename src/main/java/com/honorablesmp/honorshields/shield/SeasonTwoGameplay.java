package com.honorablesmp.honorshields.shield;

import com.honorablesmp.honorshields.classsystem.ClassType;
import com.honorablesmp.honorshields.classsystem.EffectStacking;
import com.honorablesmp.honorshields.classsystem.PassiveTriggerHandler;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.Weapon;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Season-two mechanics kept outside the already-large ability dispatcher. */
public final class SeasonTwoGameplay {
	private static final Set<UUID> INTERNAL_MINING = new HashSet<>();
	private static final Map<HitKey, HitChain> RIME_HITS = new HashMap<>();
	private static final Map<UUID, HitChain> OAK_HITS = new HashMap<>();
	private static final Map<UUID, HitChain> THUNDER_HITS = new HashMap<>();
	private static final Map<UUID, Long> DROWNED_WHIRLPOOL_READY = new HashMap<>();
	private static final List<Whirlpool> WHIRLPOOLS = new ArrayList<>();
	private static final List<SmeltScan> SMELT_SCANS = new ArrayList<>();
	private static final Map<UUID, Bulwark> BULWARKS = new HashMap<>();
	private static final Map<UUID, Rooted> ROOTED = new HashMap<>();

	private record HitKey(UUID attacker, UUID target) {}
	private static final class HitChain { int hits; long readyAt; long lastHit; }
	private record Whirlpool(UUID owner, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
		Vec3 center, long endsAt) {}
	private record SmeltScan(UUID owner, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension, Vec3 center, long at, boolean foodOnly) {}
	private record Placed(BlockPos pos, BlockState previous) {}
	private record Rooted(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension, long endsAt) { }
	private static final class Bulwark {
		final net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension;
		final Vec3 center;
		final long endsAt;
		final List<Placed> blocks;
		Bulwark(ServerLevel level, Vec3 center, long endsAt, List<Placed> blocks) {
			this.dimension = level.dimension(); this.center = center; this.endsAt = endsAt; this.blocks = blocks;
		}
	}

	public static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		tickWhirlpools(server, now);
		tickSmelting(server, now);
		tickRoots(server, now);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) validateCrystalBulwark(player);
		RIME_HITS.values().removeIf(hit -> now - hit.lastHit > 1_200L && now >= hit.readyAt);
		OAK_HITS.values().removeIf(hit -> now - hit.lastHit > 1_200L && now >= hit.readyAt);
		THUNDER_HITS.values().removeIf(hit -> now - hit.lastHit > 200L);
	}

	public static void onBlockBreak(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state, BlockEntity blockEntity) {
		if (INTERNAL_MINING.contains(player.getUUID())) return;
		PlowHandler.onCropHarvest(player, state, pos, blockEntity);
		HonorPlayerData data = (HonorPlayerData) player;
		boolean miner = data.honorshields$getClassType() == ClassType.MINER;
		if (miner && player.getMainHandItem().is(ItemTags.PICKAXES)
			&& state.is(BlockTags.MINEABLE_WITH_PICKAXE) && state.is(net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags.ORES)) {
			midas(level, player, pos);
			if (data.honorshields$getShieldCondition().ordinal() <= ShieldCondition.BLESSED.ordinal()
				&& player.getRandom().nextBoolean()) {
				for (ItemStack drop : Block.getDrops(state, level, pos, blockEntity, player, player.getMainHandItem()))
					Block.popResource(level, pos, drop.copy());
				PassiveTriggerHandler.triggerAt(player, ClassType.MINER, "Vein Seeker", Vec3.atCenterOf(pos), player.getLookAngle());
			}
		}
		if (miner && player.isShiftKeyDown()) breakThreeByThree(level, player, pos, state);
		ShieldType active = ShieldResourceManager.activeShield(player);
		if (active == ShieldType.CINDER && state.is(net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags.ORES))
			SMELT_SCANS.add(new SmeltScan(player.getUUID(), level.dimension(), Vec3.atCenterOf(pos), level.getGameTime() + 1L, false));
	}

	private static void midas(ServerLevel level, ServerPlayer player, BlockPos pos) {
		int roll = player.getRandom().nextInt(10_000);
		ItemStack reward;
		boolean rare = false;
		if (roll < 50) { reward = new ItemStack(Items.ENCHANTED_GOLDEN_APPLE); rare = true; }
		else if (roll < 300) {
			var key = player.getRandom().nextBoolean() ? Enchantments.SWIFT_SNEAK : Enchantments.SOUL_SPEED;
			var enchantment = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
			reward = EnchantmentHelper.createBook(new EnchantmentInstance(enchantment, enchantment.value().getMaxLevel()));
			rare = true;
		}
		else if (roll < 700) { reward = new ItemStack(Items.ANCIENT_DEBRIS, 1 + player.getRandom().nextInt(2)); rare = true; }
		else if (roll < 2_000) { reward = new ItemStack(Items.DIAMOND, 1 + player.getRandom().nextInt(3)); rare = true; }
		else if (roll < 4_000) reward = new ItemStack(Items.GOLD_INGOT, 1 + player.getRandom().nextInt(10));
		else if (roll < 6_500) reward = new ItemStack(Items.IRON_INGOT, 1 + player.getRandom().nextInt(5));
		else reward = new ItemStack(Items.COAL, 1 + player.getRandom().nextInt(20));
		Block.popResource(level, pos, reward);
		PassiveTriggerHandler.triggerAt(player, ClassType.MINER, rare ? "Midas' Blessing!" : "Midas' Blessing", Vec3.atCenterOf(pos), player.getLookAngle());
	}

	private static void breakThreeByThree(ServerLevel level, ServerPlayer player, BlockPos origin, BlockState original) {
		ItemStack tool = player.getMainHandItem();
		if (tool.isEmpty() || !tool.isCorrectToolForDrops(original)) return;
		Direction.Axis normal = Direction.getApproximateNearest(player.getLookAngle()).getAxis();
		boolean brokeExtra = false;
		INTERNAL_MINING.add(player.getUUID());
		try {
			for (int a = -1; a <= 1; a++) for (int b = -1; b <= 1; b++) {
				BlockPos target = switch (normal) {
					case X -> origin.offset(0, a, b);
					case Y -> origin.offset(a, 0, b);
					case Z -> origin.offset(a, b, 0);
				};
				if (target.equals(origin)) continue;
				BlockState state = level.getBlockState(target);
				if (!state.isAir() && state.getDestroySpeed(level, target) >= 0.0F && tool.isCorrectToolForDrops(state))
					brokeExtra |= player.gameMode.destroyBlock(target);
			}
		} finally { INTERNAL_MINING.remove(player.getUUID()); }
		if (brokeExtra) PassiveTriggerHandler.triggerAt(player, ClassType.MINER, "Wide Bore", Vec3.atCenterOf(origin), player.getLookAngle());
	}

	public static void onLivingDeath(LivingEntity dead, DamageSource source) {
		if (source.getEntity() instanceof ServerPlayer killer) PlowHandler.onPlayerKill(killer, dead);
		if (source.getEntity() instanceof ServerPlayer killer && ShieldResourceManager.activeShield(killer) == ShieldType.CINDER)
			SMELT_SCANS.add(new SmeltScan(killer.getUUID(), dead.level().dimension(), dead.position(), ((ServerLevel) dead.level()).getGameTime() + 1L, true));
	}

	private static void tickSmelting(MinecraftServer server, long now) {
		Iterator<SmeltScan> iterator = SMELT_SCANS.iterator();
		while (iterator.hasNext()) {
			SmeltScan scan = iterator.next();
			if (now < scan.at()) continue;
			iterator.remove();
			ServerLevel level = server.getLevel(scan.dimension());
			if (level == null) continue;
			for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, AABB.ofSize(scan.center(), 4, 4, 4), item -> item.getAge() < 10)) {
				ItemStack cooked = cooked(level, entity.getItem(), scan.foodOnly());
				if (!cooked.isEmpty()) {
					entity.setItem(cooked);
					// Reuse Cinder's tight ember choreography at the transformed drop.
					ServerPlayer owner = server.getPlayerList().getPlayer(scan.owner());
					if (owner != null && owner.level() == level)
						HonorShieldsPackets.abilityEffectFrom(owner, ShieldType.CINDER, 0, entity, 2, entity.position());
				}
			}
		}
	}

	private static ItemStack cooked(ServerLevel level, ItemStack input, boolean foodOnly) {
		if (input.isEmpty() || foodOnly && !input.has(DataComponents.FOOD)) return ItemStack.EMPTY;
		SingleRecipeInput recipeInput = new SingleRecipeInput(input.copyWithCount(1));
		return level.getServer().getRecipeManager().getRecipeFor(RecipeType.SMELTING, recipeInput, level)
			.map(holder -> {
				ItemStack result = holder.value().assemble(recipeInput);
				if (result.isEmpty() || result.is(input.getItem()) || foodOnly && !result.has(DataComponents.FOOD)) return ItemStack.EMPTY;
				result.setCount(result.getCount() * input.getCount());
				return result;
			}).orElse(ItemStack.EMPTY);
	}

	public static void onSuccessfulDamage(LivingEntity damaged, DamageSource source, float damageTaken) {
		// Crystal Bulwark must react to any incoming damage, not just PvP. The old
		// early return below silently made mobs and environmental sources unable to arm it.
		if (damaged instanceof ServerPlayer stone && ShieldResourceManager.activeShield(stone) == ShieldType.STONE
			&& ((HonorPlayerData) stone).honorshields$getShieldCondition() == ShieldCondition.EXALTED
			&& stone.getHealth() < 6.0F && stone.getHealth() + damageTaken >= 6.0F) startCrystalBulwark(stone);
		if (!(source.getEntity() instanceof ServerPlayer attacker) || !ShieldResourceManager.isEnemy(attacker, damaged)) return;
		MonsoonHandler.onSuccessfulAttack(damaged, source);
		if (((HonorPlayerData) attacker).honorshields$getClassType() == ClassType.DROWNED
			&& source.getDirectEntity() == attacker && attacker.getMainHandItem().is(Items.TRIDENT))
			disableShieldWithTrident(attacker, damaged);
		long now = ((ServerLevel) attacker.level()).getGameTime();
		ShieldType shield = ShieldResourceManager.activeShield(attacker);
		if (shield == ShieldType.THUNDER
			&& ((HonorPlayerData) attacker).honorshields$getShieldCondition() == ShieldCondition.EXALTED) {
			HitChain chain = THUNDER_HITS.computeIfAbsent(attacker.getUUID(), ignored -> new HitChain());
			chain.lastHit = now;
			chain.hits++;
			if (chain.hits >= 3) {
				chain.hits = 0;
				ShieldAbilityHandler.broadcastThunderStrike(attacker, damaged);
			} else {
				HonorShieldsPackets.abilityEffectFrom(attacker, ShieldType.THUNDER, 4, damaged, 1, damaged.position());
			}
		}
		if (shield == ShieldType.RIME) {
			HitChain chain = RIME_HITS.computeIfAbsent(new HitKey(attacker.getUUID(), damaged.getUUID()), ignored -> new HitChain());
			if (now >= chain.readyAt) {
				chain.hits++; chain.lastHit = now;
				HonorShieldsPackets.abilityEffect(attacker, ShieldType.RIME, chain.hits >= 5 ? 4 : 0, damaged, 2);
				if (chain.hits >= 5) { chain.hits = 0; chain.readyAt = now + 600L; EffectStacking.applyOnce(damaged, MobEffects.SLOWNESS, 100, 0); }
			}
		}
		if (shield == ShieldType.OAK) {
			HitChain chain = OAK_HITS.computeIfAbsent(attacker.getUUID(), ignored -> new HitChain());
			if (now >= chain.readyAt) {
				chain.hits++; chain.lastHit = now;
				if (chain.hits >= 5) {
					chain.hits = 0; chain.readyAt = now + 600L;
					ServerLevel level = (ServerLevel) attacker.level();
					for (LivingEntity target : ShieldResourceManager.enemies(attacker, damaged.position(), 4.0)) {
						target.hurtServer(level, AbilityDamage.source(level, attacker, AbilityDamage.Kind.OAK_OVERGROWTH),
								target instanceof ServerPlayer ? 4.0F : 8.0F);
						root(target, 60L);
					}
					EffectStacking.applyOnce(attacker, MobEffects.ABSORPTION, 600, 0);
					EffectStacking.applyOnce(damaged, MobEffects.SLOWNESS, 60, 5);
					HonorShieldsPackets.abilityEffectFrom(attacker, ShieldType.OAK, 4, null, 1, damaged.position());
				}
			}
		}
	}

	private static void startCrystalBulwark(ServerPlayer player) {
		HonorPlayerData data = (HonorPlayerData) player;
		ServerLevel level = (ServerLevel) player.level();
		long now = level.getGameTime();
		if (now < data.honorshields$getStoneBulwarkReadyAt()) return;
		data.honorshields$setStoneBulwarkReadyAt(now + 72_000L);
		ShieldResourceManager.exaltedPassiveCooldownStarted(player);
		player.heal(10.0F);
		EffectStacking.applyOnce(player, MobEffects.REGENERATION, 200, 0);
		EffectStacking.applyOnce(player, MobEffects.RESISTANCE, 600, 0);
		for (LivingEntity target : ShieldResourceManager.enemies(player, player.position(), 5.0)) {
			if (ShieldBlockingHandler.blocksKnockback(target)) continue;
			Vec3 push = target.position().subtract(player.position()).normalize().scale(3.5);
			target.setDeltaMovement(push.x, 0.7, push.z); target.hurtMarked = true;
		}
		List<Placed> placed = new ArrayList<>();
		BlockPos center = player.blockPosition().above();
		for (int x=-3;x<=3;x++) for (int y=-3;y<=3;y++) for (int z=-3;z<=3;z++) {
			double distance = Math.sqrt(x*x+y*y+z*z);
			if (distance < 2.5 || distance > 3.4) continue;
			BlockPos pos = center.offset(x,y,z); BlockState old = level.getBlockState(pos);
			if (!old.canBeReplaced() || level.getBlockEntity(pos) != null) continue;
			placed.add(new Placed(pos.immutable(), old)); level.setBlock(pos, CrystalBulwarkBlock.BLOCK.defaultBlockState(), 3);
		}
		BULWARKS.put(player.getUUID(), new Bulwark(level, player.position(), now + 600L, placed));
		HonorShieldsPackets.abilityEffect(player, ShieldType.STONE, 4, null, 0);
	}

	public static void applyCrystalThorns(LivingEntity damaged, DamageSource source) {
		if (!(damaged instanceof ServerPlayer player) || !(source.getEntity() instanceof LivingEntity attacker)) return;
		Bulwark bulwark = BULWARKS.get(player.getUUID());
		if (bulwark == null || !ShieldResourceManager.isEnemy(player, attacker)
			|| attacker.position().distanceToSqr(bulwark.center) > 16.0) return;
		int damage = 0;
		for (int i=0;i<4;i++) if (player.getRandom().nextFloat() < 0.45F) damage += 1 + player.getRandom().nextInt(4);
		if (damage > 0) attacker.hurtServer((ServerLevel) player.level(),
			AbilityDamage.source((ServerLevel) player.level(), player, AbilityDamage.Kind.STONE_CRYSTAL_THORNS),
			damage * (attacker instanceof Mob ? 2.0F : 1.0F));
	}

	public static void validateCrystalBulwark(ServerPlayer player) {
		Bulwark bulwark = BULWARKS.get(player.getUUID());
		if (bulwark == null) return;
		ServerLevel level = (ServerLevel) player.level();
		if (!level.dimension().equals(bulwark.dimension) || level.getGameTime() >= bulwark.endsAt
			|| player.position().distanceToSqr(bulwark.center) > 400.0 || ShieldResourceManager.activeShield(player) != ShieldType.STONE
			|| ((HonorPlayerData) player).honorshields$getShieldCondition() != ShieldCondition.EXALTED)
			restoreBulwark(player.getUUID(), level.getServer());
	}

	private static void restoreBulwark(UUID owner, MinecraftServer server) {
		Bulwark bulwark = BULWARKS.remove(owner); if (bulwark == null) return;
		ServerLevel level = server.getLevel(bulwark.dimension); if (level == null) return;
		for (Placed placed : bulwark.blocks) if (level.getBlockState(placed.pos).is(CrystalBulwarkBlock.BLOCK))
			level.setBlock(placed.pos, placed.previous, 3);
	}

	public static boolean onFall(ServerPlayer player, double distance, float modifier) {
		ShieldType shield = ShieldResourceManager.activeShield(player);
		if (shield != ShieldType.BOULDER && shield != ShieldType.TEMPEST) return false;
		ServerLevel level = (ServerLevel) player.level();
		if (shield == ShieldType.BOULDER && distance > 11.0) {
			double radius = 6.0;
			// 2 hearts above eleven blocks, then one extra heart for every additional five,
			// capped at 6 hearts. Use true damage so armor cannot erase the landing.
			float damage = Math.min(12.0F, 4.0F + (float) Math.floor((distance - 11.0) / 5.0) * 2.0F);
			for (LivingEntity target : ShieldResourceManager.enemies(player, player.position(), radius)) {
				target.hurtServer(level, AbilityDamage.source(level, player, AbilityDamage.Kind.BOULDER_GROUND_SLAM),
						target instanceof ServerPlayer ? damage : damage * 2.0F);
				if (!ShieldBlockingHandler.blocksKnockback(target)) {
					Vec3 push = target.position().subtract(player.position()).multiply(1.0, 0.0, 1.0);
					if (push.lengthSqr() > 1.0E-6) {
						push = push.normalize().scale(4.0);
						target.setDeltaMovement(target.getDeltaMovement().add(push.x, 0.55, push.z));
						target.hurtMarked = true;
					}
				}
			}
			ShieldAbilityHandler.startEarthquakeReplica(player, radius);
			ShieldAbilityHandler.addAllCooldowns(player, ShieldType.BOULDER, 5);
			HonorShieldsPackets.abilityEffect(player, ShieldType.BOULDER, 4, null, 1);
			return true;
		} else if (shield == ShieldType.TEMPEST
			&& ((HonorPlayerData) player).honorshields$getShieldCondition() == ShieldCondition.EXALTED) {
			// The fall passive is Exalted-only. Exalted Tempest still ignores ordinary
			// fall damage, while landings above four blocks also create the authored
			// full-distance launch. Slow Falling suppresses only the launch, not the
			// Exalted fall-damage protection.
			if (distance > 4.0 && !player.hasEffect(MobEffects.SLOW_FALLING)) {
				for (LivingEntity target : ShieldResourceManager.enemies(player, player.position(), 5.0)) {
					target.hurtServer(level, AbilityDamage.source(level, player, AbilityDamage.Kind.TEMPEST_WINDBORNE_IMPACT),
						target instanceof ServerPlayer ? 4.0F : 8.0F);
					if (ShieldBlockingHandler.blocksKnockback(target)) continue;
					Vec3 motion = target.getDeltaMovement();
					target.setDeltaMovement(motion.x, Math.max(motion.y, velocityForHeight(distance)), motion.z);
					target.hurtMarked = true;
				}
				HonorShieldsPackets.abilityEffect(player, ShieldType.TEMPEST, 4, null, 1);
			}
			return true;
		}
		// Boulder landings at or below the trigger threshold and non-Exalted
		// Tempest players use vanilla fall damage; there is no hidden immunity.
		return false;
	}

	/** Roots are server-authoritative horizontal movement locks, not just a cosmetic Slowness effect. */
	private static void root(LivingEntity target, long duration) {
		ROOTED.put(target.getUUID(), new Rooted(target.level().dimension(), target.level().getGameTime() + duration));
		if (target instanceof Mob mob) mob.getNavigation().stop();
	}

	private static void tickRoots(MinecraftServer server, long now) {
		Iterator<Map.Entry<UUID, Rooted>> iterator = ROOTED.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<UUID, Rooted> entry = iterator.next();
			Rooted root = entry.getValue();
			ServerLevel level = server.getLevel(root.dimension());
			var entity = level == null ? null : level.getEntity(entry.getKey());
			if (!(entity instanceof LivingEntity target) || !target.isAlive() || now >= root.endsAt()) { iterator.remove(); continue; }
			Vec3 motion = target.getDeltaMovement();
			target.setDeltaMovement(0.0, motion.y, 0.0);
			target.hurtMarked = true;
			if (target instanceof Mob mob) mob.getNavigation().stop();
		}
	}

	/** Inverts vanilla's airborne gravity/drag with a tiny bounded simulation. */
	private static double velocityForHeight(double desiredHeight) {
		double low = 0.0, high = 3.5;
		for (int search = 0; search < 18; search++) {
			double velocity = (low + high) * 0.5, y = 0.0, current = velocity;
			for (int tick = 0; tick < 100 && current > 0.0; tick++) {
				y += current;
				current = (current - 0.08) * 0.98;
			}
			if (y < desiredHeight) low = velocity; else high = velocity;
		}
		return high;
	}

	public static boolean tryWhirlpool(ServerPlayer owner, Vec3 center) {
		if (((HonorPlayerData) owner).honorshields$getClassType() != ClassType.DROWNED) return false;
		ServerLevel level = (ServerLevel) owner.level(); long now = level.getGameTime();
		if (now < DROWNED_WHIRLPOOL_READY.getOrDefault(owner.getUUID(), 0L)) return false;
		DROWNED_WHIRLPOOL_READY.put(owner.getUUID(), now + 200L);
		WHIRLPOOLS.add(new Whirlpool(owner.getUUID(), level.dimension(), center, now + 30L));
		for (LivingEntity target : ShieldResourceManager.enemies(owner, center, 5.0))
			target.hurtServer(level, AbilityDamage.source(level, owner, AbilityDamage.Kind.DROWNED_WHIRLPOOL),
					target instanceof ServerPlayer ? 3.0F : 6.0F);
		PassiveTriggerHandler.triggerAt(owner, ClassType.DROWNED, "Whirlpool", center, owner.getLookAngle());
		HonorShieldsPackets.abilityEffectFrom(owner, ShieldType.MONSOON, 4, null, 1, center);
		return true;
	}

	public static void disableShieldWithTrident(ServerPlayer owner, LivingEntity target) {
		if (((HonorPlayerData) owner).honorshields$getClassType() != ClassType.DROWNED || !target.isBlocking()) return;
		ItemStack blocking = target.getItemBlockingWith();
		var blocks = blocking.get(DataComponents.BLOCKS_ATTACKS);
		if (blocks != null) blocks.disable((ServerLevel) target.level(), target, Weapon.AXE_DISABLES_BLOCKING_FOR_SECONDS, blocking);
	}

	private static void tickWhirlpools(MinecraftServer server, long now) {
		Iterator<Whirlpool> iterator = WHIRLPOOLS.iterator();
		while (iterator.hasNext()) {
			Whirlpool pool = iterator.next(); if (now >= pool.endsAt) { iterator.remove(); continue; }
			ServerPlayer owner = server.getPlayerList().getPlayer(pool.owner); ServerLevel level = server.getLevel(pool.dimension);
			if (owner == null || level == null) { iterator.remove(); continue; }
			for (LivingEntity target : ShieldResourceManager.enemies(owner, pool.center, 5.0)) {
				Vec3 pull = pool.center.subtract(target.position());
				if (pull.lengthSqr() > 0.05 && !ShieldBlockingHandler.blocksKnockback(target)) target.push(pull.x * 0.035, 0.02, pull.z * 0.035);
			}
		}
	}

	public static void resetPlayer(ServerPlayer player) {
		UUID id = player.getUUID();
		DROWNED_WHIRLPOOL_READY.remove(id); OAK_HITS.remove(id); THUNDER_HITS.remove(id);
		ROOTED.remove(id);
		ROOTED.keySet().removeIf(target -> target.equals(id));
		RIME_HITS.keySet().removeIf(key -> key.attacker.equals(id) || key.target.equals(id));
		WHIRLPOOLS.removeIf(pool -> pool.owner.equals(id));
		SMELT_SCANS.removeIf(scan -> scan.owner.equals(id));
		restoreBulwark(id, player.level().getServer());
	}

	public static void restoreAll(MinecraftServer server) {
		for (UUID owner : List.copyOf(BULWARKS.keySet())) restoreBulwark(owner, server);
		WHIRLPOOLS.clear(); SMELT_SCANS.clear(); ROOTED.clear();
	}

	private SeasonTwoGameplay() {}
}
