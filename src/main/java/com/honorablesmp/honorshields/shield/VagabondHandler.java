package com.honorablesmp.honorshields.shield;

import com.honorablesmp.honorshields.classsystem.EffectStacking;
import com.honorablesmp.honorshields.classsystem.RogueStealthState;
import com.honorablesmp.honorshields.classsystem.TrustManager;
import com.honorablesmp.honorshields.data.HonorPlayerData;
import com.honorablesmp.honorshields.network.HonorShieldsPackets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Server-authoritative Vagabond passives and their short-lived projectile state. */
public final class VagabondHandler {
	private static final long BLOWDART_COOLDOWN = 1_200L;
	private static final long SMOKE_COOLDOWN = 400L;
	private static final long FLARE_COOLDOWN = 1_200L;
	private static final long DRAW_COOLDOWN = 400L;
	private static final long MINE_COOLDOWN = 600L;
	private static final Map<UUID, Long> BLOWDART_READY = new HashMap<>();
	private static final Map<UUID, Long> SMOKE_READY = new HashMap<>();
	private static final Map<UUID, Long> FLARE_READY = new HashMap<>();
	private static final Map<UUID, Long> DRAW_READY = new HashMap<>();
	private static final Map<UUID, Long> MINE_READY = new HashMap<>();
	private static final Map<UUID, Dart> DARTS = new HashMap<>();
	private static final Map<UUID, DartVolley> VOLLEYS = new HashMap<>();
	private static final Map<UUID, Mine> MINES = new HashMap<>();
	private static final Map<UUID, Long> SATURATION_BLOCKED_UNTIL = new HashMap<>();
	private static final Map<UUID, Long> STEALTH_UNTIL = new HashMap<>();
	private static final AttributeModifier VAGABOND_STEALTH_MARKER = new AttributeModifier(
		RogueStealthState.VAGABOND_STEALTH_MARKER_ID, 0.0D, AttributeModifier.Operation.ADD_VALUE);
	private static final Map<UUID, List<Hit>> RECENT_HITS = new HashMap<>();
	private static final List<PendingMineBlast> PENDING_MINE_BLASTS = new ArrayList<>();

	private enum DartType { SLEEP, POISON, STATIC }
	private record Dart(UUID owner, DartType type, UUID volley, long expires) { }
	private record DartVolley(UUID owner, List<DartType> types, int next, long nextShot, long expires,
		boolean counted) { }
	/** An armed mine is a logical marker on a block, never a placed TNT block. */
	private record Mine(UUID owner, ServerLevel level, BlockPos pos) { }
	private record PendingMineBlast(UUID owner, ServerLevel level, BlockPos pos, long detonatesAt) { }
	private record Hit(String action, long tick) { }

	public static void registerEvents() {
		UseItemCallback.EVENT.register(VagabondHandler::onUseItem);
		UseBlockCallback.EVENT.register(VagabondHandler::onUseBlock);
	}

	private static InteractionResult onUseItem(net.minecraft.world.entity.player.Player raw, Level rawLevel,
		InteractionHand hand) {
		if (rawLevel.isClientSide() || !(raw instanceof ServerPlayer player)) return InteractionResult.PASS;
		ItemStack stack = player.getItemInHand(hand);
		if (!active(player)) return InteractionResult.PASS;
		long now = ((ServerLevel) player.level()).getGameTime();
		if (!player.isShiftKeyDown()) return InteractionResult.PASS;
		if (stack.is(Items.BAMBOO)) return startBlowdart(player, now);
		if (stack.is(Items.GUNPOWDER)) return smokeBomb(player, now);
		if (stack.is(Items.PAPER)) return luckOfTheDraw(player, now);
		if (stack.is(Items.TNT) && MINES.containsKey(player.getUUID())) return detonateMine(player, now);
		return InteractionResult.PASS;
	}

	private static InteractionResult onUseBlock(net.minecraft.world.entity.player.Player raw, Level rawLevel,
		InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
		if (rawLevel.isClientSide() || !(raw instanceof ServerPlayer player) || !active(player)) return InteractionResult.PASS;
		ItemStack stack = player.getItemInHand(hand);
		long now = ((ServerLevel) player.level()).getGameTime();
		if (stack.is(Items.FIREWORK_ROCKET)) return signalFlare(player, hit.getLocation(), now);
		if (player.isShiftKeyDown() && stack.is(Items.TNT)) {
			if (MINES.containsKey(player.getUUID())) return detonateMine(player, now);
			return placeMine(player, hand, hit, now);
		}
		return InteractionResult.PASS;
	}

	private static InteractionResult startBlowdart(ServerPlayer player, long now) {
		if (!ready(player, BLOWDART_READY, now, "Blowdart")) return InteractionResult.FAIL;
		int count = ((HonorPlayerData) player).honorshields$getShieldCondition() == ShieldCondition.EXALTED ? 3 : 1;
		List<DartType> types = new ArrayList<>(count);
		DartType[] available = DartType.values();
		for (int i = 0; i < count; i++) types.add(available[player.getRandom().nextInt(available.length)]);
		BLOWDART_READY.put(player.getUUID(), now + BLOWDART_COOLDOWN);
		VOLLEYS.put(player.getUUID(), new DartVolley(player.getUUID(), types, 0, now, now + 120L, false));
		player.sendOverlayMessage(Component.literal("Passive: Blowdart").withStyle(ChatFormatting.AQUA));
		HonorShieldsPackets.abilityEffect(player, ShieldType.VAGABOND, 1, null, 0);
		return InteractionResult.SUCCESS;
	}

	private static InteractionResult smokeBomb(ServerPlayer player, long now) {
		if (!ready(player, SMOKE_READY, now, "Smoke Bomb")) return InteractionResult.FAIL;
		SMOKE_READY.put(player.getUUID(), now + SMOKE_COOLDOWN);
		ServerLevel level = (ServerLevel) player.level();
		Vec3 center = player.position();
		level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), player.getSoundSource(), 0.65F, 1.4F);
		for (LivingEntity target : entities(level, center, 10.0)) {
			if (enemy(player, target)) {
				EffectStacking.applyOnce(target, MobEffects.BLINDNESS, 40, 0);
				EffectStacking.applyOnce(target, MobEffects.SLOWNESS, 40, 1);
				recordHit(player, "Smoke Bomb", now);
			} else if (target instanceof ServerPlayer ally && (ally == player || TrustManager.isMutualTrust(player, ally))) {
				stealth(ally, 120);
			}
		}
		stealth(player, 120);
		HonorShieldsPackets.abilityEffectFrom(player, ShieldType.VAGABOND, 3, null, 0, center);
		return InteractionResult.SUCCESS;
	}

	private static InteractionResult signalFlare(ServerPlayer player, Vec3 at, long now) {
		if (!ready(player, FLARE_READY, now, "Signal Flare")) return InteractionResult.FAIL;
		FLARE_READY.put(player.getUUID(), now + FLARE_COOLDOWN);
		ServerLevel level = (ServerLevel) player.level();
		level.playSound(null, BlockPos.containing(at), SoundEvents.FIREWORK_ROCKET_LAUNCH, player.getSoundSource(), 0.9F, 1.0F);
		boolean hit = false;
		for (LivingEntity target : entities(level, at, 50.0)) {
			if (target instanceof ServerPlayer ally && (ally == player || TrustManager.isMutualTrust(player, ally))) {
				EffectStacking.applyOnce(ally, MobEffects.SPEED, 200, 1);
				EffectStacking.applyOnce(ally, MobEffects.STRENGTH, 200, 1);
			} else if (enemy(player, target)) {
				EffectStacking.applyOnce(target, MobEffects.GLOWING, 600, 0);
				hit = true;
			}
		}
		if (hit) recordHit(player, "Signal Flare", now);
		HonorShieldsPackets.abilityEffectFrom(player, ShieldType.VAGABOND, 3, null, 0, at);
		return InteractionResult.SUCCESS;
	}

	private static InteractionResult luckOfTheDraw(ServerPlayer player, long now) {
		if (!ready(player, DRAW_READY, now, "Luck of the Draw")) return InteractionResult.FAIL;
		DRAW_READY.put(player.getUUID(), now + DRAW_COOLDOWN);
		float roll = player.getRandom().nextFloat();
		if (roll < 0.05F) {
			pilgrimsPath(player, 400);
			player.sendOverlayMessage(Component.literal("Pilgrim's Path").withStyle(ChatFormatting.GOLD));
		} else if (roll < 0.20F) {
			var effects = List.of(MobEffects.WEAKNESS, MobEffects.SLOWNESS, MobEffects.MINING_FATIGUE, MobEffects.POISON);
			EffectStacking.applyOnce(player, effects.get(player.getRandom().nextInt(effects.size())), 300, 0);
			player.sendOverlayMessage(Component.literal("Luck of the Draw: setback").withStyle(ChatFormatting.RED));
		} else {
			var effects = List.of(MobEffects.SPEED, MobEffects.REGENERATION, MobEffects.HASTE, MobEffects.RESISTANCE, MobEffects.JUMP_BOOST);
			EffectStacking.applyOnce(player, effects.get(player.getRandom().nextInt(effects.size())), 400, 0);
			player.sendOverlayMessage(Component.literal("Luck of the Draw: fortune").withStyle(ChatFormatting.GREEN));
		}
		HonorShieldsPackets.abilityEffect(player, ShieldType.VAGABOND, 1, null, 0);
		return InteractionResult.SUCCESS;
	}

	private static InteractionResult placeMine(ServerPlayer player, InteractionHand hand,
		net.minecraft.world.phys.BlockHitResult hit, long now) {
		if (!ready(player, MINE_READY, now, "Sticky Mine")) return InteractionResult.FAIL;
		ServerLevel level = (ServerLevel) player.level();
		BlockPos pos = hit.getBlockPos().immutable();
		if (!level.mayInteract(player, pos) || !level.getWorldBorder().isWithinBounds(pos)) return InteractionResult.PASS;
		MINES.put(player.getUUID(), new Mine(player.getUUID(), level, pos));
		player.sendOverlayMessage(Component.literal("Sticky Mine armed").withStyle(ChatFormatting.RED));
		// The armed marker is world-visible but purely cosmetic; no TNT block is
		// placed and the inventory stack remains untouched.
		HonorShieldsPackets.abilityEffectFrom(player, ShieldType.VAGABOND, 3, null, 0, Vec3.atCenterOf(pos));
		return InteractionResult.SUCCESS;
	}

	private static InteractionResult detonateMine(ServerPlayer player, long now) {
		Mine mine = MINES.remove(player.getUUID());
		if (mine == null || !mine.level().hasChunkAt(mine.pos())) return InteractionResult.FAIL;
		MINE_READY.put(player.getUUID(), now + MINE_COOLDOWN);
		PENDING_MINE_BLASTS.add(new PendingMineBlast(player.getUUID(), mine.level(), mine.pos(), now + 10L));
		player.sendOverlayMessage(Component.literal("Sticky Mine detonating").withStyle(ChatFormatting.RED));
		return InteractionResult.SUCCESS;
	}

	/** Ability two: visual TNT impact, exact true damage, and TNT-like radial shove. */
	public static void demolition(ServerPlayer player) {
		ServerLevel level = (ServerLevel) player.level();
		Vec3 start = player.getEyePosition();
		Vec3 end = start.add(player.getLookAngle().normalize().scale(20.0));
		HitResult hit = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		Vec3 impact = hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
		level.playSound(null, BlockPos.containing(impact), SoundEvents.GENERIC_EXPLODE.value(), player.getSoundSource(), 0.95F, 1.0F);
		boolean hitEnemy = false;
		for (LivingEntity target : entities(level, impact, 8.0)) if (enemy(player, target)) {
			float damage = target instanceof Mob ? 16.0F : 8.0F;
			target.hurtServer(level, AbilityDamage.source(level, player, AbilityDamage.Kind.VAGABOND_DEMOLITION), damage);
			blastKnockback(impact, target, 1.35D);
			hitEnemy = true;
			HonorShieldsPackets.abilityEffect(player, ShieldType.VAGABOND, 2, target, 2);
		}
		if (hitEnemy) recordHit(player, "Demolition", level.getGameTime());
	}

	public static boolean handleDartHit(AbstractArrow arrow, EntityHitResult hit) {
		Dart dart = DARTS.remove(arrow.getUUID());
		if (dart == null) return false;
		arrow.discard();
		if (!(hit.getEntity() instanceof LivingEntity target) || !(arrow.level() instanceof ServerLevel level)
			|| !(level.getServer().getPlayerList().getPlayer(dart.owner()) instanceof ServerPlayer owner) || !enemy(owner, target)) return true;
		// One heart is the authored dart value. AbilityDamage keeps it true damage;
		// hostile mobs retain the established two-times-mob convention.
		target.hurtServer(level, AbilityDamage.source(level, owner, AbilityDamage.Kind.VAGABOND_BLOWDART),
			target instanceof Mob ? 2.0F : 1.0F);
		applyDartType(owner, target, dart.type(), level.getGameTime());
		DartVolley volley = VOLLEYS.get(dart.volley());
		if (volley != null && !volley.counted()) {
			VOLLEYS.put(dart.volley(), new DartVolley(volley.owner(), volley.types(), volley.next(), volley.nextShot(), volley.expires(), true));
			recordHit(owner, "Blowdart", level.getGameTime());
		}
		HonorShieldsPackets.abilityEffect(owner, ShieldType.VAGABOND, 1, target, 2);
		return true;
	}

	/** Used by the common arrow mixin to distinguish HonorShields darts from normal arrows. */
	public static boolean isCustomDart(AbstractArrow arrow) {
		return DARTS.containsKey(arrow.getUUID());
	}

	private static void applyDartType(ServerPlayer owner, LivingEntity target, DartType type, long now) {
		ServerLevel level = (ServerLevel) owner.level();
		switch (type) {
			case SLEEP -> {
				EffectStacking.applyOnce(target, MobEffects.SLOWNESS, 100, 1);
				EffectStacking.applyOnce(target, MobEffects.WEAKNESS, 200, 0);
				EffectStacking.applyOnce(target, MobEffects.NAUSEA, 300, 0);
				EffectStacking.applyOnce(target, MobEffects.BLINDNESS, 100, 0);
			}
			case POISON -> {
				EffectStacking.applyOnce(target, MobEffects.POISON, 200, 1);
				if (target instanceof ServerPlayer victim) {
					SATURATION_BLOCKED_UNTIL.put(victim.getUUID(), now + 60L);
					victim.getFoodData().setSaturation(0.0F);
				}
			}
			case STATIC -> {
				if (target instanceof ServerPlayer victim) {
					ShieldType shield = ShieldResourceManager.activeShield(victim);
					if (shield != null) ShieldAbilityHandler.addAllCooldowns(victim, shield, 10);
				}
			}
		}
		// The packet below is the sole ability VFX path, so each receiving client can
		// apply its own effects toggle and particle-density budget.
	}

	/** Compatibility hook for the old projectile mixin; Sticky Snow no longer exists. */
	public static boolean handleStickySnowHit(ThrowableProjectile projectile, HitResult hit) { return false; }

	public static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		Iterator<Map.Entry<UUID, DartVolley>> volleys = VOLLEYS.entrySet().iterator();
		while (volleys.hasNext()) {
			var entry = volleys.next();
			DartVolley volley = entry.getValue();
			ServerPlayer owner = server.getPlayerList().getPlayer(volley.owner());
			if (owner == null || now >= volley.expires()) { volleys.remove(); continue; }
			if (now >= volley.nextShot()) {
				shootDart(owner, volley.types().get(volley.next()), entry.getKey(), volley.next());
				int next = volley.next() + 1;
				if (next >= volley.types().size()) volleys.remove();
				else entry.setValue(new DartVolley(volley.owner(), volley.types(), next, now + 5L, volley.expires(), volley.counted()));
			}
		}
		DARTS.entrySet().removeIf(entry -> entry.getValue().expires() <= now);
		SATURATION_BLOCKED_UNTIL.entrySet().removeIf(entry -> {
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player != null && now < entry.getValue()) player.getFoodData().setSaturation(0.0F);
			return now >= entry.getValue() || player == null;
		});
		STEALTH_UNTIL.entrySet().removeIf(entry -> {
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player != null && now >= entry.getValue()) {
				var attribute = player.getAttribute(Attributes.SNEAKING_SPEED);
				if (attribute != null) attribute.removeModifier(RogueStealthState.VAGABOND_STEALTH_MARKER_ID);
			}
			return player == null || now >= entry.getValue();
		});
		tickMines(server, now);
		RECENT_HITS.entrySet().removeIf(entry -> {
			entry.getValue().removeIf(hit -> hit.tick() + 200L <= now);
			return entry.getValue().isEmpty();
		});
	}

	private static void shootDart(ServerPlayer player, DartType type, UUID volleyId, int index) {
		ServerLevel level = (ServerLevel) player.level();
		ItemStack dartStack = new ItemStack(Items.TIPPED_ARROW);
		Arrow arrow = new Arrow(level, player, dartStack, dartStack.copy());
		arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
		// Spawn from the caster's head and follow its current aim; the narrow spread is
		// visual only, so all three arrows still read as one deliberate blowpipe volley.
		arrow.setPos(player.getX(), player.getEyeY() - 0.10, player.getZ());
		arrow.setBaseDamage(0.0D); // hit handling supplies the fixed one-heart true damage.
		// 3.0 is Minecraft's fully charged vanilla bow launch speed.
		arrow.shootFromRotation(player, player.getXRot(), player.getYRot() + (index - 1) * 1.2F, 0.0F, 3.0F, 0.0F);
		level.addFreshEntity(arrow);
		DARTS.put(arrow.getUUID(), new Dart(player.getUUID(), type, volleyId, level.getGameTime() + 160L));
	}

	private static void tickMines(MinecraftServer server, long now) {
		MINES.entrySet().removeIf(entry -> {
			Mine mine = entry.getValue();
			if (!mine.level().hasChunkAt(mine.pos())) return false; // retain the marker until its chunk returns.
			return false;
		});
		Iterator<PendingMineBlast> blasts = PENDING_MINE_BLASTS.iterator();
		while (blasts.hasNext()) {
			PendingMineBlast blast = blasts.next();
			if (now < blast.detonatesAt()) continue;
			blasts.remove();
			if (!blast.level().hasChunkAt(blast.pos())) continue;
			ServerPlayer owner = server.getPlayerList().getPlayer(blast.owner());
			if (owner == null || owner.level() != blast.level()) continue;
			Vec3 center = Vec3.atCenterOf(blast.pos()).add(0.0, 0.55, 0.0);
			blast.level().playSound(null, blast.pos(), SoundEvents.GENERIC_EXPLODE.value(), owner.getSoundSource(), 1.0F, 0.9F);
			HonorShieldsPackets.abilityEffectFrom(owner, ShieldType.VAGABOND, 3, null, 0, center);
			boolean hit = false;
			for (LivingEntity target : entities(blast.level(), center, 8.0)) {
				if (target != owner && enemy(owner, target)) {
					target.hurtServer(blast.level(), AbilityDamage.source(blast.level(), owner, AbilityDamage.Kind.VAGABOND_STICKY_MINE),
						target instanceof Mob ? 16.0F : 8.0F);
					hit = true;
				}
				blastKnockback(center, target, 2.7D);
			}
			if (hit) recordHit(owner, "Sticky Mine", now);
		}
	}

	private static void blastKnockback(Vec3 center, LivingEntity target, double power) {
		if (ShieldBlockingHandler.blocksKnockback(target)) return;
		Vec3 direction = target.position().subtract(center);
		double distance = Math.max(0.4, direction.length());
		if (distance > 8.0) return;
		Vec3 velocity = direction.scale((power * (1.0 - distance / 8.0)) / distance);
		target.setDeltaMovement(target.getDeltaMovement().add(velocity.x, Math.max(0.18, velocity.y + 0.38), velocity.z));
		target.hurtMarked = true;
	}

	private static void recordHit(ServerPlayer player, String action, long now) {
		HonorPlayerData data = (HonorPlayerData) player;
		if (data.honorshields$getShieldCondition() != ShieldCondition.EXALTED || !active(player)) return;
		List<Hit> hits = RECENT_HITS.computeIfAbsent(player.getUUID(), ignored -> new ArrayList<>());
		hits.removeIf(hit -> hit.tick() + 200L <= now);
		hits.add(new Hit(action, now));
		if (hits.size() < 3) return;
		String chosen = hits.stream().map(Hit::action).filter(VagabondHandler::hasCooldown).findAny().orElse("Demolition");
		clearCooldown(player, chosen);
		pilgrimsPath(player, 400);
		stealth(player, 200);
		HonorShieldsPackets.abilityEffect(player, ShieldType.VAGABOND, 3, null, 0);
		player.sendOverlayMessage(Component.literal("EXALTED PASSIVE: Master Espionage").withStyle(ChatFormatting.GOLD));
		hits.clear();
	}

	private static boolean hasCooldown(String action) {
		return switch (action) {
			case "Demolition", "Blowdart", "Smoke Bomb", "Signal Flare", "Sticky Mine" -> true;
			default -> false;
		};
	}

	private static void clearCooldown(ServerPlayer player, String action) {
		switch (action) {
			case "Demolition" -> ShieldAbilityHandler.clearCooldown(player, ShieldType.VAGABOND, 2);
			case "Blowdart" -> BLOWDART_READY.remove(player.getUUID());
			case "Smoke Bomb" -> SMOKE_READY.remove(player.getUUID());
			case "Signal Flare" -> FLARE_READY.remove(player.getUUID());
			case "Sticky Mine" -> MINE_READY.remove(player.getUUID());
			default -> { }
		}
	}

	private static void pilgrimsPath(ServerPlayer player, int duration) {
		player.heal(8.0F);
		EffectStacking.applyOnce(player, MobEffects.SPEED, duration, 2);
	}

	private static void stealth(ServerPlayer player, int duration) {
		EffectStacking.applyOnce(player, MobEffects.INVISIBILITY, duration, 0);
		var attribute = player.getAttribute(Attributes.SNEAKING_SPEED);
		if (attribute != null && !attribute.hasModifier(RogueStealthState.VAGABOND_STEALTH_MARKER_ID)) {
			attribute.addTransientModifier(VAGABOND_STEALTH_MARKER);
		}
		long now = ((ServerLevel) player.level()).getGameTime();
		STEALTH_UNTIL.merge(player.getUUID(), now + duration, Math::max);
	}

	private static boolean ready(ServerPlayer player, Map<UUID, Long> map, long now, String name) {
		long readyAt = map.getOrDefault(player.getUUID(), 0L);
		if (readyAt <= now) return true;
		player.sendOverlayMessage(Component.literal(name + ": %.1fs".formatted((readyAt - now) / 20.0)).withStyle(ChatFormatting.RED));
		return false;
	}

	private static boolean active(ServerPlayer player) {
		HonorPlayerData data = (HonorPlayerData) player;
		return data.honorshields$getShieldCondition().usable() && ShieldResourceManager.activeShield(player) == ShieldType.VAGABOND;
	}

	private static boolean enemy(ServerPlayer player, LivingEntity target) {
		return ShieldResourceManager.isEnemy(player, target);
	}

	private static List<LivingEntity> entities(ServerLevel level, Vec3 center, double radius) {
		return level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius),
			target -> target.isAlive() && target.position().distanceToSqr(center) <= radius * radius);
	}

	public static void resetPlayer(ServerPlayer player) {
		UUID id = player.getUUID();
		BLOWDART_READY.remove(id); SMOKE_READY.remove(id); FLARE_READY.remove(id); DRAW_READY.remove(id); MINE_READY.remove(id);
		VOLLEYS.remove(id); RECENT_HITS.remove(id); SATURATION_BLOCKED_UNTIL.remove(id); STEALTH_UNTIL.remove(id);
		var attribute = player.getAttribute(Attributes.SNEAKING_SPEED);
		if (attribute != null) attribute.removeModifier(RogueStealthState.VAGABOND_STEALTH_MARKER_ID);
		DARTS.entrySet().removeIf(entry -> entry.getValue().owner().equals(id));
		MINES.remove(id);
		PENDING_MINE_BLASTS.removeIf(blast -> blast.owner().equals(id));
	}

	private VagabondHandler() { }
}
