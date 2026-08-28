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
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Server-owned Plow resource, crop and field mechanics. Cosmetic packets are emitted only after a cast succeeds. */
public final class PlowHandler {
	public static final int MAX_VERDANCY = 100;
	private static final long TILLER_COOLDOWN = 100L;
	private static final long FURROW_COOLDOWN = 600L;
	private static final long HARVEST_WARD_COOLDOWN = 1_200L;
	private static final long BOUNTIFUL_COOLDOWN = 1_900L;
	private static final long EDEN_COOLDOWN = 1_200L;
	private static final long FURROW_LIFETIME = 30L;
	private static final Map<UUID, Long> TILLER_READY = new HashMap<>();
	private static final Map<UUID, Long> FURROW_READY = new HashMap<>();
	private static final Map<UUID, Long> WARD_READY = new HashMap<>();
	private static final Map<UUID, Long> BOUNTIFUL_READY = new HashMap<>();
	private static final Map<UUID, Furrow> FURROWS = new HashMap<>();
	private static final List<FieldZone> ZONES = new ArrayList<>();
	private static final Set<UUID> EDEN_GUARD = new HashSet<>();
	private static final Set<UUID> OVERFLOW_REGEN_ACTIVE = new HashSet<>();

	private record Furrow(UUID owner, ServerLevel level, Vec3 origin, Vec3 direction, long started,
		double distance, Set<UUID> hitEntities, Set<BlockPos> advancedCrops) {}
	private record FieldZone(UUID owner, ServerLevel level, Vec3 center, long endsAt, boolean ultimate,
		List<PlacedBlock> treeBlocks, long nextPulse) {}
	private record PlacedBlock(BlockPos pos, BlockState previous, BlockState replacement) {}

	public static void registerEvents() {
		UseBlockCallback.EVENT.register(PlowHandler::onUseBlock);
	}

	private static InteractionResult onUseBlock(net.minecraft.world.entity.player.Player raw, Level rawLevel,
		InteractionHand hand, BlockHitResult hit) {
		if (rawLevel.isClientSide() || !(raw instanceof ServerPlayer player) || !active(player)
			|| !player.isShiftKeyDown()) return InteractionResult.PASS;
		ItemStack stack = player.getItemInHand(hand);
		long now = ((ServerLevel) player.level()).getGameTime();
		if (isSeed(stack)) return tillerGrace(player, hand, hit, now) ? InteractionResult.SUCCESS : InteractionResult.PASS;
		BlockPos clicked = hit.getBlockPos();
		if (isCrop(player.level().getBlockState(clicked)))
			return advanceCrop(player, clicked, now) ? InteractionResult.SUCCESS : InteractionResult.PASS;
		return InteractionResult.PASS;
	}

	public static boolean furrowbreaker(ServerPlayer player) {
		if (!active(player) || !ready(player, FURROW_READY, 30L)) return false;
		ServerLevel level = (ServerLevel) player.level();
		Vec3 direction = new Vec3(player.getLookAngle().x, 0.0, player.getLookAngle().z);
		if (direction.lengthSqr() < 1.0E-6) return false;
		if (!consume(player, 5)) return false;
		direction = direction.normalize();
		long now = level.getGameTime();
		FURROW_READY.put(player.getUUID(), now + FURROW_COOLDOWN);
		FURROWS.put(player.getUUID(), new Furrow(player.getUUID(), level,
			player.position().add(0.0, 0.15, 0.0), direction, now, 0.0, new HashSet<>(), new HashSet<>()));
		HonorShieldsPackets.abilityEffectFrom(player, ShieldType.PLOW, 1, null, 0, player.position());
		return true;
	}

	public static boolean harvestWard(ServerPlayer player) {
		if (!active(player) || !ready(player, WARD_READY, 60L) || !consume(player, 5)) return false;
		ServerLevel level = (ServerLevel) player.level();
		long now = level.getGameTime();
		WARD_READY.put(player.getUUID(), now + HARVEST_WARD_COOLDOWN);
		List<ServerPlayer> allies = mutualAllies(player, player.position(), 8.0);
		int allyCount = allies.size();
		EffectStacking.applyOnce(player, MobEffects.REGENERATION, 140, allyCount >= 1 ? 1 : 0);
		cleanse(player);
		if (allyCount >= 2) player.getFoodData().eat(2, 2.0F);
		for (ServerPlayer ally : allies) {
			EffectStacking.applyOnce(ally, MobEffects.REGENERATION, 140, allyCount >= 1 ? 1 : 0);
			if (allyCount >= 2) ally.getFoodData().eat(2, 2.0F);
			gainVerdancy(player, 3);
		}
		for (LivingEntity enemy : enemies(player, player.position(), 8.0)) {
			if (allyCount >= 2) EffectStacking.applyOnce(enemy, MobEffects.POISON, 100, 0);
			else EffectStacking.applyOnce(enemy, MobEffects.SLOWNESS, 100, 0);
		}
		ZONES.add(new FieldZone(player.getUUID(), level, player.position(), now + 140L, false, List.of(), now));
		HonorShieldsPackets.abilityEffectFrom(player, ShieldType.PLOW, 2, null, 0, player.position());
		return true;
	}

	public static boolean bountifulHarvest(ServerPlayer player) {
		if (!active(player) || !ready(player, BOUNTIFUL_READY, 95L) || !consume(player, 40)) return false;
		ServerLevel level = (ServerLevel) player.level();
		long now = level.getGameTime();
		HonorPlayerData data = (HonorPlayerData) player;
		int previousVerdancy = data.honorshields$getVerdancy();
		int previousOverflow = data.honorshields$getVerdancyOverflow();
		BOUNTIFUL_READY.put(player.getUUID(), now + BOUNTIFUL_COOLDOWN);
		BlockPos treeOrigin = aimedTreeOrigin(player);
		List<PlacedBlock> tree = placeOakTree(player, treeOrigin);
		if (tree.isEmpty()) {
			BOUNTIFUL_READY.remove(player.getUUID());
			// Roll back the resource transaction exactly. Re-adding the cost through
			// gainVerdancy could move points from Overflow back into main Verdancy.
			data.honorshields$setVerdancy(previousVerdancy);
			data.honorshields$setVerdancyOverflow(previousOverflow);
			gainSync(player);
			return false;
		}
		Vec3 center = Vec3.atCenterOf(treeOrigin);
		ZONES.add(new FieldZone(player.getUUID(), level, center, now + 400L, true, tree, now));
		for (ServerPlayer ally : mutualAlliesSquare(player, center, 6.0))
			EffectStacking.applyOnce(ally, MobEffects.RESISTANCE, 400, 0);
		for (LivingEntity enemy : enemiesSquare(player, center, 6.0)) {
			EffectStacking.applyOnce(enemy, MobEffects.SLOWNESS, 100, 1);
			EffectStacking.applyOnce(enemy, MobEffects.POISON, 100, 0);
			EffectStacking.applyOnce(enemy, MobEffects.WEAKNESS, 100, 0);
		}
		HonorShieldsPackets.abilityEffectFrom(player, ShieldType.PLOW, 3, null, 0, center);
		return true;
	}

	/** Called by the existing block-break callback with the pre-break state. */
	public static void onCropHarvest(ServerPlayer player, BlockState state, BlockPos pos, net.minecraft.world.level.block.entity.BlockEntity blockEntity) {
		if (!active(player)) return;
		ServerLevel level = (ServerLevel) player.level();
		boolean matureCrop = state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state);
		boolean harvestableMelon = state.is(Blocks.MELON);
		if (matureCrop || harvestableMelon) {
			gainVerdancy(player, 1);
			if (((HonorPlayerData) player).honorshields$getVerdancy() == MAX_VERDANCY) {
				// Full Verdancy grants one extra normal drop and a sensible golden
				// counterpart where vanilla has one. This is emitted directly so the
				// bonus cannot recursively trigger another crop-break event.
				for (ItemStack drop : Block.getDrops(state, level, pos, blockEntity, player, player.getMainHandItem())) {
					Block.popResource(level, pos, drop.copy());
				}
				if (state.is(Blocks.CARROTS)) Block.popResource(level, pos, new ItemStack(Items.GOLDEN_CARROT));
				if (harvestableMelon) Block.popResource(level, pos, new ItemStack(Items.GLISTERING_MELON_SLICE));
				HonorShieldsPackets.abilityEffectFrom(player, ShieldType.PLOW, 0, null, 1, Vec3.atCenterOf(pos));
			}
		} else if (state.is(BlockTags.LEAVES)) {
			float chance = player.getMainHandItem().is(Items.SHEARS) ? 0.50F : 0.33F;
			if (player.getRandom().nextFloat() < chance) {
				Block.popResource(level, pos, new ItemStack(Items.APPLE));
				if (((HonorPlayerData) player).honorshields$getVerdancy() == MAX_VERDANCY)
					Block.popResource(level, pos, new ItemStack(Items.GOLDEN_APPLE));
				HonorShieldsPackets.abilityEffectFrom(player, ShieldType.PLOW, 0, null, 1, Vec3.atCenterOf(pos));
			}
		}
	}

	/** Mature-player kills are the final authored Verdancy source. */
	public static void onPlayerKill(ServerPlayer player, LivingEntity dead) {
		if (active(player) && dead instanceof ServerPlayer) gainVerdancy(player, 10);
	}

	public static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		Iterator<Map.Entry<UUID, Furrow>> furrows = FURROWS.entrySet().iterator();
		while (furrows.hasNext()) {
			Map.Entry<UUID, Furrow> entry = furrows.next();
			Furrow furrow = entry.getValue();
			ServerPlayer owner = server.getPlayerList().getPlayer(furrow.owner());
			if (owner == null || owner.level() != furrow.level() || now - furrow.started() >= FURROW_LIFETIME) {
				furrows.remove();
				continue;
			}
			double nextDistance = Math.min(12.0, furrow.distance() + 1.0);
			Vec3 point = furrow.origin().add(furrow.direction().scale(nextDistance));
			Set<UUID> hits = new HashSet<>(furrow.hitEntities());
			Set<BlockPos> crops = new HashSet<>(furrow.advancedCrops());
			for (LivingEntity target : enemies(owner, point, 1.0)) if (hits.add(target.getUUID())) {
					damage(owner, target, 3.0F, AbilityDamage.Kind.PLOW_FURROWBREAKER);
				EffectStacking.applyOnce(target, MobEffects.SLOWNESS, 200, 1);
				Vec3 motion = target.getDeltaMovement();
				target.setDeltaMovement(motion.x, Math.max(motion.y, 0.65), motion.z);
				gainVerdancy(owner, 5);
				HonorShieldsPackets.abilityEffectFrom(owner, ShieldType.PLOW, 1, target, 2, point);
			}
			advanceCrops(owner, point, crops);
			if (now % 2L == 0L) HonorShieldsPackets.abilityEffectFrom(owner, ShieldType.PLOW, 1, null, 1, point);
			if (nextDistance >= 12.0) furrows.remove();
			else entry.setValue(new Furrow(furrow.owner(), furrow.level(), furrow.origin(), furrow.direction(),
				furrow.started(), nextDistance, hits, crops));
		}

		java.util.ListIterator<FieldZone> zones = ZONES.listIterator();
		while (zones.hasNext()) {
			FieldZone zone = zones.next();
			ServerPlayer owner = server.getPlayerList().getPlayer(zone.owner());
			if (owner == null || owner.level() != zone.level()) {
				restoreTree(zone);
				zones.remove();
				continue;
			}
			if (now >= zone.endsAt()) {
				restoreTree(zone);
				zones.remove();
				continue;
			}
			if (!zone.ultimate() && now >= zone.nextPulse()) {
				List<ServerPlayer> allies = mutualAllies(owner, zone.center(), 8.0);
				int count = allies.size();
				EffectStacking.applyOnce(owner, MobEffects.REGENERATION, 45, count > 0 ? 1 : 0);
				cleanse(owner);
				for (LivingEntity enemy : enemies(owner, zone.center(), 8.0))
					EffectStacking.applyOnce(enemy, count >= 2 ? MobEffects.POISON : MobEffects.SLOWNESS, 45, 0);
				HonorShieldsPackets.abilityEffectFrom(owner, ShieldType.PLOW, 2, null, 1, zone.center());
				zones.set(new FieldZone(zone.owner(), zone.level(), zone.center(), zone.endsAt(), false, zone.treeBlocks(), now + 20L));
			}
			if (zone.ultimate()) {
				if (now % 100L == 0L) growOrPlantWheat(zone);
				if (now % 20L == 0L) {
				for (LivingEntity enemy : enemiesSquare(owner, zone.center(), 6.0)) {
						EffectStacking.applyOnce(enemy, MobEffects.SLOWNESS, 45, 1);
						EffectStacking.applyOnce(enemy, MobEffects.POISON, 45, 0);
						EffectStacking.applyOnce(enemy, MobEffects.WEAKNESS, 45, 0);
					}
				}
				if (now % 80L == 0L) for (LivingEntity enemy : enemiesSquare(owner, zone.center(), 6.0))
					damage(owner, enemy, 1.0F, AbilityDamage.Kind.PLOW_BOUNTIFUL_HARVEST);
			}
		}

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			boolean fullOverflow = active(player)
				&& ((HonorPlayerData) player).honorshields$getShieldCondition() == ShieldCondition.EXALTED
				&& ((HonorPlayerData) player).honorshields$getVerdancy() == MAX_VERDANCY
				&& ((HonorPlayerData) player).honorshields$getVerdancyOverflow() == MAX_VERDANCY;
			if (fullOverflow) {
				OVERFLOW_REGEN_ACTIVE.add(player.getUUID());
				EffectStacking.applyContinuous(player, MobEffects.REGENERATION, 40, 0, "plow_overflow_regeneration");
			} else if (OVERFLOW_REGEN_ACTIVE.remove(player.getUUID())) {
				MobEffectInstance regeneration = player.getEffect(MobEffects.REGENERATION);
				if (regeneration != null && regeneration.getAmplifier() == 0) player.removeEffect(MobEffects.REGENERATION);
			}
			if (now % 5L == 0L) tryEdenIntervention(player);
		}
		retainOnline(server);
	}

	/** Attempts the Exalted rescue before a lethal event or on a low-health tick. */
	public static boolean tryEdenIntervention(ServerPlayer caster) {
		if (!active(caster) || ((HonorPlayerData) caster).honorshields$getShieldCondition() != ShieldCondition.EXALTED
			|| ((HonorPlayerData) caster).honorshields$getVerdancy() < MAX_VERDANCY
			|| EDEN_GUARD.contains(caster.getUUID())
			|| ((HonorPlayerData) caster).honorshields$getEdenInterventionReadyAt() > ((ServerLevel) caster.level()).getGameTime()) return false;
		List<ServerPlayer> rescued = new ArrayList<>();
		if (caster.getHealth() <= 6.0F) rescued.add(caster);
		for (ServerPlayer ally : mutualAllies(caster, caster.position(), 20.0)) if (ally.getHealth() <= 6.0F) rescued.add(ally);
		if (rescued.isEmpty()) return false;
		EDEN_GUARD.add(caster.getUUID());
		try {
			consumeAll(caster);
			for (ServerPlayer player : rescued) {
				player.setHealth(Math.min(player.getMaxHealth(), 10.0F));
				cleanse(player);
				EffectStacking.applyOnce(player, MobEffects.STRENGTH, 300, 1);
				EffectStacking.applyOnce(player, MobEffects.SPEED, 400, 1);
				EffectStacking.applyOnce(player, MobEffects.RESISTANCE, 100, 1);
				EffectStacking.applyOnce(player, MobEffects.REGENERATION, 200, 1);
			}
			for (LivingEntity enemy : enemies(caster, caster.position(), 20.0)) {
				EffectStacking.applyOnce(enemy, MobEffects.GLOWING, 200, 0);
				EffectStacking.applyOnce(enemy, MobEffects.SLOWNESS, 200, 1);
			}
			((HonorPlayerData) caster).honorshields$setEdenInterventionReadyAt(
				((ServerLevel) caster.level()).getGameTime() + EDEN_COOLDOWN);
			HonorShieldsPackets.abilityEffectFrom(caster, ShieldType.PLOW, 4, null, 0, caster.position());
			return true;
		} finally {
			EDEN_GUARD.remove(caster.getUUID());
		}
	}

	public static boolean preventLethal(ServerPlayer player) { return tryEdenIntervention(player); }

	public static int verdancy(ServerPlayer player) { return ((HonorPlayerData) player).honorshields$getVerdancy(); }
	public static int overflow(ServerPlayer player) { return ((HonorPlayerData) player).honorshields$getVerdancyOverflow(); }

	public static void gainVerdancy(ServerPlayer player, int amount) {
		if (!active(player) || amount <= 0) return;
		HonorPlayerData data = (HonorPlayerData) player;
		int current = data.honorshields$getVerdancy();
		int toMain = Math.min(amount, MAX_VERDANCY - current);
		data.honorshields$setVerdancy(current + toMain);
		int extra = amount - toMain;
		if (extra > 0 && data.honorshields$getShieldCondition() == ShieldCondition.EXALTED)
			data.honorshields$setVerdancyOverflow(Math.min(MAX_VERDANCY, data.honorshields$getVerdancyOverflow() + extra));
		HonorShieldsPackets.shieldResource(player, "verdancy", data.honorshields$getVerdancy(), MAX_VERDANCY, false);
		if (data.honorshields$getShieldCondition() == ShieldCondition.EXALTED)
			HonorShieldsPackets.shieldResource(player, "verdancy_overflow", data.honorshields$getVerdancyOverflow(), MAX_VERDANCY, false);
	}

	private static boolean consume(ServerPlayer player, int amount) {
		HonorPlayerData data = (HonorPlayerData) player;
		int available = data.honorshields$getVerdancy() + (data.honorshields$getShieldCondition() == ShieldCondition.EXALTED
			? data.honorshields$getVerdancyOverflow() : 0);
		if (available < amount) return false;
		int overflow = Math.min(amount, data.honorshields$getVerdancyOverflow());
		data.honorshields$setVerdancyOverflow(data.honorshields$getVerdancyOverflow() - overflow);
		data.honorshields$setVerdancy(data.honorshields$getVerdancy() - (amount - overflow));
		gainSync(player);
		return true;
	}

	private static void consumeAll(ServerPlayer player) {
		HonorPlayerData data = (HonorPlayerData) player;
		data.honorshields$setVerdancy(0);
		data.honorshields$setVerdancyOverflow(0);
		gainSync(player);
	}

	private static void gainSync(ServerPlayer player) {
		HonorPlayerData data = (HonorPlayerData) player;
		HonorShieldsPackets.shieldResource(player, "verdancy", data.honorshields$getVerdancy(), MAX_VERDANCY, false);
		if (data.honorshields$getShieldCondition() == ShieldCondition.EXALTED)
			HonorShieldsPackets.shieldResource(player, "verdancy_overflow", data.honorshields$getVerdancyOverflow(), MAX_VERDANCY, false);
	}

	private static boolean tillerGrace(ServerPlayer player, InteractionHand hand, BlockHitResult hit, long now) {
		if (!ready(player, TILLER_READY, 5L)) return false;
		BlockPos cropPos = hit.getBlockPos().relative(hit.getDirection());
		if (!player.level().getBlockState(cropPos).isAir()) cropPos = hit.getBlockPos();
		BlockPos support = cropPos.below();
		BlockState supportState = player.level().getBlockState(support);
		Block crop = cropFor(player.getItemInHand(hand));
		if (crop == null || !player.level().getBlockState(cropPos).isAir() || player.level().getBlockEntity(cropPos) != null
			|| !player.level().mayInteract(player, cropPos) || !player.level().mayInteract(player, support)
			|| !supportState.is(BlockTags.DIRT)
			|| supportState.is(Blocks.REINFORCED_DEEPSLATE) || supportState.is(Blocks.BEDROCK) || supportState.is(Blocks.OBSIDIAN)) return false;
		ServerLevel level = (ServerLevel) player.level();
		level.setBlock(support, Blocks.FARMLAND.defaultBlockState(), 3);
		BlockState planted = crop.defaultBlockState();
		if (planted.hasProperty(CropBlock.AGE)) planted = planted.setValue(CropBlock.AGE, CropBlock.AGE.getPossibleValues().stream().max(Integer::compareTo).orElse(0));
		level.setBlock(cropPos, planted, 3);
		if (!player.isCreative()) player.getItemInHand(hand).shrink(1);
		TILLER_READY.put(player.getUUID(), now + TILLER_COOLDOWN);
		HonorShieldsPackets.abilityEffectFrom(player, ShieldType.PLOW, 0, null, 1, Vec3.atCenterOf(cropPos));
		return true;
	}

	private static boolean advanceCrop(ServerPlayer player, BlockPos pos, long now) {
		if (!ready(player, TILLER_READY, 5L)) return false;
		BlockState state = player.level().getBlockState(pos);
		if (!(state.getBlock() instanceof CropBlock crop) || crop.isMaxAge(state)) return false;
		int chance = ((HonorPlayerData) player).honorshields$getVerdancy() >= 50 ? 50 : 25;
		if (player.getRandom().nextInt(100) >= chance) return false;
		int age = state.getValue(CropBlock.AGE);
		player.level().setBlock(pos, state.setValue(CropBlock.AGE, Math.min(crop.getMaxAge(), age + 1)), 3);
		TILLER_READY.put(player.getUUID(), now + TILLER_COOLDOWN);
		HonorShieldsPackets.abilityEffectFrom(player, ShieldType.PLOW, 0, null, 1, Vec3.atCenterOf(pos));
		return true;
	}

	private static void advanceCrops(ServerPlayer player, Vec3 point, Set<BlockPos> already) {
		BlockPos center = BlockPos.containing(point);
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-1, -1, -1), center.offset(1, 1, 1))) {
			BlockState state = player.level().getBlockState(pos);
			if (!(state.getBlock() instanceof CropBlock crop) || already.contains(pos) || crop.isMaxAge(state)) continue;
			int age = state.getValue(CropBlock.AGE);
			player.level().setBlock(pos, state.setValue(CropBlock.AGE, Math.min(crop.getMaxAge(), age + 12)), 3);
			already.add(pos.immutable());
		}
	}

	private static BlockPos aimedTreeOrigin(ServerPlayer player) {
		ServerLevel level = (ServerLevel) player.level();
		Vec3 start = player.getEyePosition();
		Vec3 end = start.add(player.getLookAngle().normalize().scale(20.0));
		var hit = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		return hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK
			? hit.getBlockPos().relative(hit.getDirection()) : BlockPos.containing(end.x, end.y - 1.0, end.z);
	}

	/** Spawns one fully grown wheat crop every five seconds, or matures an existing crop. */
	private static void growOrPlantWheat(FieldZone zone) {
		ServerPlayer owner = zone.level().getServer().getPlayerList().getPlayer(zone.owner());
		if (owner == null) return;
		BlockPos center = BlockPos.containing(zone.center());
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-6, -1, -6), center.offset(5, 1, 5))) {
			BlockState state = zone.level().getBlockState(pos);
			if (state.getBlock() instanceof CropBlock crop && !crop.isMaxAge(state)
				&& zone.level().mayInteract(owner, pos)) {
				zone.level().setBlock(pos, state.setValue(CropBlock.AGE, crop.getMaxAge()), 3);
				HonorShieldsPackets.abilityEffectFrom(owner, ShieldType.PLOW, 3, null, 1, Vec3.atCenterOf(pos));
				return;
			}
		}
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-6, -1, -6), center.offset(5, 1, 5))) {
			BlockPos cropPos = pos.above();
			BlockState support = zone.level().getBlockState(pos);
			if (!support.is(BlockTags.DIRT) || !zone.level().getBlockState(cropPos).isAir()
				|| zone.level().getBlockEntity(cropPos) != null || !zone.level().mayInteract(owner, cropPos)) continue;
			zone.level().setBlock(pos, Blocks.FARMLAND.defaultBlockState(), 3);
			BlockState wheat = Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7);
			zone.level().setBlock(cropPos, wheat, 3);
			HonorShieldsPackets.abilityEffectFrom(owner, ShieldType.PLOW, 3, null, 1, Vec3.atCenterOf(cropPos));
			return;
		}
	}

	private static List<PlacedBlock> placeOakTree(ServerPlayer player, BlockPos origin) {
		ServerLevel level = (ServerLevel) player.level();
		BlockPos support = origin.below();
		if (!level.hasChunkAt(support) || !level.getBlockState(support).is(BlockTags.DIRT)
			|| !level.mayInteract(player, support)) return List.of();
		List<PlacedBlock> placed = new ArrayList<>();
		int height = 4;
		for (int y = 0; y < height; y++) {
			BlockPos pos = origin.above(y);
			if (!safeTreeBlock(level, pos)) return List.of();
		}
		for (int x = -2; x <= 2; x++) for (int y = 2; y <= 4; y++) for (int z = -2; z <= 2; z++) {
			if (Math.abs(x) + Math.abs(z) > 3) continue;
			BlockPos pos = origin.offset(x, y, z);
			if (!safeTreeBlock(level, pos)) continue;
			BlockState replacement = y == 4 ? Blocks.OAK_LEAVES.defaultBlockState() : Blocks.OAK_LOG.defaultBlockState();
			placed.add(new PlacedBlock(pos.immutable(), level.getBlockState(pos), replacement));
			level.setBlock(pos, replacement, 3);
		}
		for (int y = 0; y < height; y++) {
			BlockPos pos = origin.above(y);
			if (safeTreeBlock(level, pos)) {
				BlockState replacement = Blocks.OAK_LOG.defaultBlockState();
				placed.add(new PlacedBlock(pos.immutable(), level.getBlockState(pos), replacement));
				level.setBlock(pos, replacement, 3);
			}
		}
		return placed;
	}

	private static boolean safeTreeBlock(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return level.hasChunkAt(pos) && level.getBlockEntity(pos) == null && state.isAir()
			&& !state.is(Blocks.REINFORCED_DEEPSLATE) && !state.is(Blocks.BEDROCK) && !state.is(Blocks.OBSIDIAN);
	}

	private static void restoreTree(FieldZone zone) {
		for (PlacedBlock block : zone.treeBlocks()) if (zone.level().getBlockState(block.pos()).equals(block.replacement()))
			zone.level().setBlock(block.pos(), block.previous(), 3);
	}

	private static void damage(ServerPlayer owner, LivingEntity target, float hearts, AbilityDamage.Kind kind) {
		target.hurtServer((ServerLevel) owner.level(), AbilityDamage.source((ServerLevel) owner.level(), owner, kind),
			hearts * (target instanceof Mob ? 2.0F : 1.0F));
	}

	private static List<LivingEntity> enemies(ServerPlayer owner, Vec3 center, double radius) {
		return owner.level().getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius), target ->
			target != owner && target.isAlive() && target.position().distanceToSqr(center) <= radius * radius
				&& ShieldResourceManager.isEnemy(owner, target));
	}

	/** The ultimate's 12x12 field is square, matching its boundary VFX and crop grid. */
	private static List<LivingEntity> enemiesSquare(ServerPlayer owner, Vec3 center, double halfSize) {
		return owner.level().getEntitiesOfClass(LivingEntity.class,
			new AABB(center.x - halfSize, center.y - halfSize, center.z - halfSize,
				center.x + halfSize, center.y + halfSize, center.z + halfSize),
			target -> target != owner && target.isAlive() && ShieldResourceManager.isEnemy(owner, target));
	}

	private static List<ServerPlayer> mutualAllies(ServerPlayer owner, Vec3 center, double radius) {
		return ((ServerLevel) owner.level()).getPlayers(candidate -> candidate != owner
			&& candidate.position().distanceToSqr(center) <= radius * radius && TrustManager.isMutualTrust(owner, candidate));
	}

	private static List<ServerPlayer> mutualAlliesSquare(ServerPlayer owner, Vec3 center, double halfSize) {
		return ((ServerLevel) owner.level()).getPlayers(candidate -> candidate != owner
			&& Math.abs(candidate.getX() - center.x) <= halfSize
			&& Math.abs(candidate.getY() - center.y) <= halfSize
			&& Math.abs(candidate.getZ() - center.z) <= halfSize
			&& TrustManager.isMutualTrust(owner, candidate));
	}

	private static void cleanse(ServerPlayer player) {
		for (MobEffectInstance effect : List.copyOf(player.getActiveEffects()))
			if (!effect.getEffect().value().isBeneficial()) player.removeEffect(effect.getEffect());
	}

	private static boolean ready(ServerPlayer player, Map<UUID, Long> map, long seconds) {
		long now = ((ServerLevel) player.level()).getGameTime();
		long readyAt = map.getOrDefault(player.getUUID(), 0L);
		if (readyAt > now) {
			player.sendOverlayMessage(Component.literal("Cooldown: %.1fs".formatted((readyAt - now) / 20.0)).withStyle(ChatFormatting.RED));
			return false;
		}
		return true;
	}

	private static boolean active(ServerPlayer player) {
		HonorPlayerData data = (HonorPlayerData) player;
		return data.honorshields$getShieldType() == ShieldType.PLOW
			&& data.honorshields$getShieldCondition().usable()
			&& ShieldType.fromStack(player.getOffhandItem()) == ShieldType.PLOW;
	}

	private static boolean isSeed(ItemStack stack) {
		return stack.is(Items.WHEAT_SEEDS) || stack.is(Items.BEETROOT_SEEDS) || stack.is(Items.CARROT) || stack.is(Items.POTATO);
	}

	private static Block cropFor(ItemStack stack) {
		if (stack.is(Items.WHEAT_SEEDS)) return Blocks.WHEAT;
		if (stack.is(Items.BEETROOT_SEEDS)) return Blocks.BEETROOTS;
		if (stack.is(Items.CARROT)) return Blocks.CARROTS;
		if (stack.is(Items.POTATO)) return Blocks.POTATOES;
		return null;
	}

	private static boolean isCrop(BlockState state) { return state.getBlock() instanceof CropBlock; }

	private static void retainOnline(MinecraftServer server) {
		Set<UUID> online = server.getPlayerList().getPlayers().stream().map(ServerPlayer::getUUID).collect(java.util.stream.Collectors.toSet());
		TILLER_READY.keySet().retainAll(online); FURROW_READY.keySet().retainAll(online); WARD_READY.keySet().retainAll(online);
		BOUNTIFUL_READY.keySet().retainAll(online); EDEN_GUARD.retainAll(online); OVERFLOW_REGEN_ACTIVE.retainAll(online);
	}

	public static void resetPlayer(ServerPlayer player) {
		UUID id = player.getUUID();
		TILLER_READY.remove(id); FURROW_READY.remove(id); WARD_READY.remove(id); BOUNTIFUL_READY.remove(id);
		FURROWS.remove(id);
		Iterator<FieldZone> iterator = ZONES.iterator();
		while (iterator.hasNext()) { FieldZone zone = iterator.next(); if (zone.owner().equals(id)) { restoreTree(zone); iterator.remove(); } }
		EDEN_GUARD.remove(id);
		if (OVERFLOW_REGEN_ACTIVE.remove(id)) {
			MobEffectInstance regeneration = player.getEffect(MobEffects.REGENERATION);
			if (regeneration != null && regeneration.getAmplifier() == 0) player.removeEffect(MobEffects.REGENERATION);
		}
	}

	public static void restoreAll() { for (FieldZone zone : List.copyOf(ZONES)) restoreTree(zone); ZONES.clear(); FURROWS.clear(); }

	private PlowHandler() { }
}
