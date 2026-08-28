package com.honorablesmp.honorshields.mixin;

import com.honorablesmp.honorshields.classsystem.ClassType;
import com.honorablesmp.honorshields.classsystem.PassiveTriggerHandler;
import com.honorablesmp.honorshields.data.HonorPlayerData;
import com.honorablesmp.honorshields.shield.SeasonTwoGameplay;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TridentItem.class)
public abstract class TridentItemMixin {
	@Unique private record LandRiptideCooldownKey(UUID playerId, boolean clientSide) {}
	@Unique private static final long HONORSHIELDS_LAND_RIPTIDE_COOLDOWN_NANOS = TimeUnit.SECONDS.toNanos(10L);
	@Unique private static final Map<LandRiptideCooldownKey, Long> HONORSHIELDS_LAND_RIPTIDE_READY_AT = new ConcurrentHashMap<>();

	@Inject(method = "use", at = @At("HEAD"), cancellable = true)
	private void honorshields$riptideUse(Level level, Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
		ItemStack stack = player.getItemInHand(hand);
		if (isDrowned(player) && EnchantmentHelper.getTridentSpinAttackStrength(stack, player) > 0.0F && !player.isInWaterOrRain()) {
			if (stack.nextDamageWillBreak()) {
				cir.setReturnValue(InteractionResult.FAIL);
				return;
			}
			if (!landRiptideReady(level, player)) {
				cir.setReturnValue(InteractionResult.FAIL);
				return;
			}
			player.startUsingItem(hand);
			cir.setReturnValue(InteractionResult.CONSUME);
		}
	}

	@Inject(method = "releaseUsing", at = @At("HEAD"), cancellable = true)
	private void honorshields$riptideAnywhere(ItemStack stack, Level level, LivingEntity entity, int remaining, CallbackInfoReturnable<Boolean> cir) {
		if (!(entity instanceof Player player) || !isDrowned(player) || player.isInWaterOrRain()) return;
		int held = ((TridentItem) (Object) this).getUseDuration(stack, entity) - remaining;
		float strength = EnchantmentHelper.getTridentSpinAttackStrength(stack, player);
		if (held < 10 || strength <= 0.0F || player.isPassenger()) return;
		if (stack.nextDamageWillBreak()) {
			cir.setReturnValue(false);
			return;
		}
		if (!landRiptideReady(level, player)) {
			cir.setReturnValue(false);
			return;
		}
		startLandRiptideCooldown(level, player);
		player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
		Vec3 movement = player.getLookAngle().normalize().scale(strength);
		player.push(movement);
		player.startAutoSpinAttack(20, 8.0F, stack);
		if (player.onGround()) player.push(0.0, 1.2, 0.0);
		player.hurtMarked = true;
		if (!level.isClientSide()) {
			stack.hurtWithoutBreaking(1, player);
			Holder<SoundEvent> riptideSound = EnchantmentHelper
				.pickHighestLevel(stack, EnchantmentEffectComponents.TRIDENT_SOUND)
				.orElse(SoundEvents.TRIDENT_THROW);
			level.playSound(null, player, riptideSound.value(), SoundSource.PLAYERS, 1.0F, 1.0F);
			if (player instanceof ServerPlayer serverPlayer) {
				PassiveTriggerHandler.trigger(serverPlayer, ClassType.DROWNED, "Storm Caller");
				SeasonTwoGameplay.tryWhirlpool(serverPlayer, serverPlayer.position());
			}
		}
		cir.setReturnValue(true);
	}

	@Inject(method = "releaseUsing", at = @At("RETURN"))
	private void honorshields$waterRiptideWhirlpool(ItemStack stack, Level level, LivingEntity entity, int remaining,
		CallbackInfoReturnable<Boolean> cir) {
		if (!level.isClientSide() && Boolean.TRUE.equals(cir.getReturnValue()) && entity instanceof ServerPlayer player
			&& isDrowned(player) && player.isInWaterOrRain()
			&& EnchantmentHelper.getTridentSpinAttackStrength(stack, player) > 0.0F) {
			SeasonTwoGameplay.tryWhirlpool(player, player.position());
		}
	}

	private static boolean isDrowned(Player player) {
		return player instanceof HonorPlayerData data && data.honorshields$getClassType() == ClassType.DROWNED;
	}

	@Unique
	private static boolean landRiptideReady(Level level, Player player) {
		LandRiptideCooldownKey key = new LandRiptideCooldownKey(player.getUUID(), level.isClientSide());
		long now = System.nanoTime();
		long readyAt = HONORSHIELDS_LAND_RIPTIDE_READY_AT.getOrDefault(key, 0L);
		if (now >= readyAt) {
			HONORSHIELDS_LAND_RIPTIDE_READY_AT.remove(key);
			return true;
		}
		return false;
	}

	@Unique
	private static void startLandRiptideCooldown(Level level, Player player) {
		LandRiptideCooldownKey key = new LandRiptideCooldownKey(player.getUUID(), level.isClientSide());
		long now = System.nanoTime();
		if (HONORSHIELDS_LAND_RIPTIDE_READY_AT.size() > 256) {
			HONORSHIELDS_LAND_RIPTIDE_READY_AT.entrySet().removeIf(entry -> now >= entry.getValue());
		}
		HONORSHIELDS_LAND_RIPTIDE_READY_AT.put(key, now + HONORSHIELDS_LAND_RIPTIDE_COOLDOWN_NANOS);
	}
}
