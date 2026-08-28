package com.honorablesmp.honorshields.mixin;

import com.honorablesmp.honorshields.classsystem.ClassType;
import com.honorablesmp.honorshields.data.HonorPlayerData;
import com.honorablesmp.honorshields.shield.ShieldManager;
import com.honorablesmp.honorshields.shield.ShieldBlockingHandler;
import com.honorablesmp.honorshields.shield.ShieldCondition;
import com.honorablesmp.honorshields.shield.ShieldType;
import com.honorablesmp.honorshields.shield.ShatteredShieldItem;
import com.honorablesmp.honorshields.classsystem.TrustManager;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerEntityMixin implements HonorPlayerData {
	@Unique private ClassType honorshields$classType;
	@Unique private ShieldType honorshields$shieldType;
	@Unique private ShieldCondition honorshields$shieldCondition = ShieldCondition.HONORED;
	@Unique private boolean honorshields$shieldShattered;
	@Unique private final Set<UUID> honorshields$trustedPlayers = new HashSet<>();
	@Unique private boolean honorshields$leaderboardVisible = true;
	@Unique private float honorshields$leaderboardScale = 1.0F;
	@Unique private int honorshields$oathGeneration;
	@Unique private int honorshields$thunderCharge;
	@Unique private int honorshields$voidCharge;
	@Unique private int honorshields$tempestCharge;
	@Unique private int honorshields$dawnSunCharge;
	@Unique private boolean honorshields$dawnFullSunArmed;
	@Unique private boolean honorshields$dawnLastChance;
	@Unique private long honorshields$dawnLastChanceProgress;
	@Unique private float honorshields$wardenStoredDamage;
	@Unique private long honorshields$stoneBulwarkReadyAt;
	@Unique private long honorshields$berserkerResolveReadyAt;
	@Unique private long honorshields$demonCoreReadyAt;
	@Unique private long honorshields$absoluteZeroReadyAt;
	@Unique private long honorshields$elderMercyReadyAt;
	@Unique private long honorshields$blackoutReadyAt;
	@Unique private long honorshields$edenInterventionReadyAt;
	@Unique private int honorshields$verdancy;
	@Unique private int honorshields$verdancyOverflow;
	@Unique private Entity honorshields$trustedAttackTarget;
	@Unique private int honorshields$trustedAttackFireTicks = -1;

	@Override public ClassType honorshields$getClassType() { return honorshields$classType; }
	@Override public void honorshields$setClassType(ClassType type) { honorshields$classType = type; }
	@Override public ShieldType honorshields$getShieldType() { return honorshields$shieldType; }
	@Override public void honorshields$setShieldType(ShieldType type) { honorshields$shieldType = type; }
	@Override public ShieldCondition honorshields$getShieldCondition() { return honorshields$shieldCondition; }
	@Override public void honorshields$setShieldCondition(ShieldCondition condition) { honorshields$shieldCondition = condition == null ? ShieldCondition.HONORED : condition; }
	@Override public boolean honorshields$isShieldShattered() { return honorshields$shieldShattered; }
	@Override public void honorshields$setShieldShattered(boolean shattered) { honorshields$shieldShattered = shattered; }
	@Override public Set<UUID> honorshields$getTrustedPlayers() { return honorshields$trustedPlayers; }
	@Override public boolean honorshields$isLeaderboardVisible() { return honorshields$leaderboardVisible; }
	@Override public void honorshields$setLeaderboardVisible(boolean visible) { honorshields$leaderboardVisible = visible; }
	@Override public float honorshields$getLeaderboardScale() { return honorshields$leaderboardScale; }
	@Override public void honorshields$setLeaderboardScale(float scale) { honorshields$leaderboardScale = Math.max(0.5F, Math.min(2.0F, scale)); }
	@Override public int honorshields$getOathGeneration() { return honorshields$oathGeneration; }
	@Override public void honorshields$setOathGeneration(int generation) { honorshields$oathGeneration = Math.max(0, generation); }
	@Override public int honorshields$getThunderCharge() { return honorshields$thunderCharge; }
	@Override public void honorshields$setThunderCharge(int value) {
		int maximum = honorshields$shieldCondition == ShieldCondition.EXALTED ? 6 : 3;
		honorshields$thunderCharge = Math.max(0, Math.min(maximum, value));
	}
	@Override public int honorshields$getVoidCharge() { return honorshields$voidCharge; }
	@Override public void honorshields$setVoidCharge(int value) { honorshields$voidCharge = Math.max(0, Math.min(3, value)); }
	@Override public int honorshields$getTempestCharge() { return honorshields$tempestCharge; }
	@Override public void honorshields$setTempestCharge(int value) { honorshields$tempestCharge = Math.max(0, value); }
	@Override public int honorshields$getDawnSunCharge() { return honorshields$dawnSunCharge; }
	@Override public void honorshields$setDawnSunCharge(int value) { honorshields$dawnSunCharge = Math.max(0, value); }
	@Override public boolean honorshields$isDawnFullSunArmed() { return honorshields$dawnFullSunArmed; }
	@Override public void honorshields$setDawnFullSunArmed(boolean armed) { honorshields$dawnFullSunArmed = armed; }
	@Override public boolean honorshields$hasDawnLastChance() { return honorshields$dawnLastChance; }
	@Override public void honorshields$setDawnLastChance(boolean available) { honorshields$dawnLastChance = available; }
	@Override public long honorshields$getDawnLastChanceProgress() { return honorshields$dawnLastChanceProgress; }
	@Override public void honorshields$setDawnLastChanceProgress(long ticks) { honorshields$dawnLastChanceProgress = Math.max(0L, ticks); }
	@Override public float honorshields$getWardenStoredDamage() { return honorshields$wardenStoredDamage; }
	@Override public void honorshields$setWardenStoredDamage(float damage) { honorshields$wardenStoredDamage = Math.max(0.0F, damage); }
	@Override public long honorshields$getStoneBulwarkReadyAt() { return honorshields$stoneBulwarkReadyAt; }
	@Override public void honorshields$setStoneBulwarkReadyAt(long tick) { honorshields$stoneBulwarkReadyAt = Math.max(0L, tick); }
	@Override public long honorshields$getBerserkerResolveReadyAt() { return honorshields$berserkerResolveReadyAt; }
	@Override public void honorshields$setBerserkerResolveReadyAt(long tick) { honorshields$berserkerResolveReadyAt = Math.max(0L, tick); }
	@Override public long honorshields$getDemonCoreReadyAt() { return honorshields$demonCoreReadyAt; }
	@Override public void honorshields$setDemonCoreReadyAt(long tick) { honorshields$demonCoreReadyAt = Math.max(0L, tick); }
	@Override public long honorshields$getAbsoluteZeroReadyAt() { return honorshields$absoluteZeroReadyAt; }
	@Override public void honorshields$setAbsoluteZeroReadyAt(long tick) { honorshields$absoluteZeroReadyAt = Math.max(0L, tick); }
	@Override public long honorshields$getElderMercyReadyAt() { return honorshields$elderMercyReadyAt; }
	@Override public void honorshields$setElderMercyReadyAt(long tick) { honorshields$elderMercyReadyAt = Math.max(0L, tick); }
	@Override public long honorshields$getBlackoutReadyAt() { return honorshields$blackoutReadyAt; }
	@Override public void honorshields$setBlackoutReadyAt(long tick) { honorshields$blackoutReadyAt = Math.max(0L, tick); }
	@Override public long honorshields$getEdenInterventionReadyAt() { return honorshields$edenInterventionReadyAt; }
	@Override public void honorshields$setEdenInterventionReadyAt(long tick) { honorshields$edenInterventionReadyAt = Math.max(0L, tick); }
	@Override public int honorshields$getVerdancy() { return honorshields$verdancy; }
	@Override public void honorshields$setVerdancy(int value) { honorshields$verdancy = Math.max(0, Math.min(100, value)); }
	@Override public int honorshields$getVerdancyOverflow() { return honorshields$verdancyOverflow; }
	@Override public void honorshields$setVerdancyOverflow(int value) { honorshields$verdancyOverflow = Math.max(0, Math.min(100, value)); }

	@Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
	private void honorshields$read(ValueInput input, CallbackInfo ci) {
		honorshields$classType = ClassType.byId(input.getStringOr("HonorShieldsClass", ""));
		honorshields$shieldType = ShieldType.byId(input.getStringOr("HonorShieldsShield", ""));
		honorshields$shieldCondition = ShieldCondition.byId(input.getStringOr("HonorShieldsCondition", "honored"));
		honorshields$shieldShattered = input.getBooleanOr("HonorShieldsShieldShattered", false);
		honorshields$trustedPlayers.clear();
		String trusted = input.getStringOr("HonorShieldsTrusted", "");
		if (!trusted.isBlank()) Arrays.stream(trusted.split(",")).forEach(value -> {
			try { honorshields$trustedPlayers.add(UUID.fromString(value)); }
			catch (IllegalArgumentException ignored) { }
		});
		honorshields$leaderboardVisible = input.getBooleanOr("HonorShieldsHudVisible", true);
		honorshields$leaderboardScale = Math.max(0.5F, Math.min(2.0F, input.getFloatOr("HonorShieldsHudScale", 1.0F)));
		honorshields$oathGeneration = Math.max(0, input.getIntOr("HonorShieldsOathGeneration", 0));
		int thunderMaximum = honorshields$shieldCondition == ShieldCondition.EXALTED ? 6 : 3;
		honorshields$thunderCharge = Math.max(0, Math.min(thunderMaximum, input.getIntOr("HonorShieldsThunderCharge", 0)));
		honorshields$voidCharge = Math.max(0, Math.min(3, input.getIntOr("HonorShieldsVoidCharge", 0)));
		honorshields$tempestCharge = Math.max(0, input.getIntOr("HonorShieldsTempestCharge", 0));
		honorshields$dawnSunCharge = Math.max(0, input.getIntOr("HonorShieldsDawnSunCharge", 0));
		honorshields$dawnFullSunArmed = input.getBooleanOr("HonorShieldsDawnFullSunArmed", false);
		honorshields$dawnLastChance = input.getBooleanOr("HonorShieldsDawnLastChance", false);
		honorshields$dawnLastChanceProgress = Math.max(0L, input.getLongOr("HonorShieldsDawnLastChanceProgress", 0L));
		honorshields$wardenStoredDamage = Math.max(0.0F, input.getFloatOr("HonorShieldsWardenStoredDamage", 0.0F));
		honorshields$stoneBulwarkReadyAt = Math.max(0L, input.getLongOr("HonorShieldsStoneBulwarkReadyAt", 0L));
		honorshields$berserkerResolveReadyAt = Math.max(0L, input.getLongOr("HonorShieldsBerserkerResolveReadyAt", 0L));
		honorshields$demonCoreReadyAt = Math.max(0L, input.getLongOr("HonorShieldsDemonCoreReadyAt", 0L));
		honorshields$absoluteZeroReadyAt = Math.max(0L, input.getLongOr("HonorShieldsAbsoluteZeroReadyAt", 0L));
		honorshields$elderMercyReadyAt = Math.max(0L, input.getLongOr("HonorShieldsElderMercyReadyAt", 0L));
		honorshields$blackoutReadyAt = Math.max(0L, input.getLongOr("HonorShieldsBlackoutReadyAt", 0L));
		honorshields$edenInterventionReadyAt = Math.max(0L, input.getLongOr("HonorShieldsEdenInterventionReadyAt", 0L));
		honorshields$verdancy = Math.max(0, Math.min(100, input.getIntOr("HonorShieldsVerdancy", 0)));
		honorshields$verdancyOverflow = Math.max(0, Math.min(100, input.getIntOr("HonorShieldsVerdancyOverflow", 0)));
	}

	@Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
	private void honorshields$write(ValueOutput output, CallbackInfo ci) {
		if (honorshields$classType != null) output.putString("HonorShieldsClass", honorshields$classType.id());
		if (honorshields$shieldType != null) output.putString("HonorShieldsShield", honorshields$shieldType.id());
		output.putString("HonorShieldsCondition", honorshields$shieldCondition.id());
		output.putBoolean("HonorShieldsShieldShattered", honorshields$shieldShattered);
		output.putString("HonorShieldsTrusted", honorshields$trustedPlayers.stream().map(UUID::toString).sorted().collect(Collectors.joining(",")));
		output.putBoolean("HonorShieldsHudVisible", honorshields$leaderboardVisible);
		output.putFloat("HonorShieldsHudScale", honorshields$leaderboardScale);
		output.putInt("HonorShieldsOathGeneration", honorshields$oathGeneration);
		output.putInt("HonorShieldsThunderCharge", honorshields$thunderCharge);
		output.putInt("HonorShieldsVoidCharge", honorshields$voidCharge);
		output.putInt("HonorShieldsTempestCharge", honorshields$tempestCharge);
		output.putInt("HonorShieldsDawnSunCharge", honorshields$dawnSunCharge);
		output.putBoolean("HonorShieldsDawnFullSunArmed", honorshields$dawnFullSunArmed);
		output.putBoolean("HonorShieldsDawnLastChance", honorshields$dawnLastChance);
		output.putLong("HonorShieldsDawnLastChanceProgress", honorshields$dawnLastChanceProgress);
		output.putFloat("HonorShieldsWardenStoredDamage", honorshields$wardenStoredDamage);
		output.putLong("HonorShieldsStoneBulwarkReadyAt", honorshields$stoneBulwarkReadyAt);
		output.putLong("HonorShieldsBerserkerResolveReadyAt", honorshields$berserkerResolveReadyAt);
		output.putLong("HonorShieldsDemonCoreReadyAt", honorshields$demonCoreReadyAt);
		output.putLong("HonorShieldsAbsoluteZeroReadyAt", honorshields$absoluteZeroReadyAt);
		output.putLong("HonorShieldsElderMercyReadyAt", honorshields$elderMercyReadyAt);
		output.putLong("HonorShieldsBlackoutReadyAt", honorshields$blackoutReadyAt);
		output.putLong("HonorShieldsEdenInterventionReadyAt", honorshields$edenInterventionReadyAt);
		output.putInt("HonorShieldsVerdancy", honorshields$verdancy);
		output.putInt("HonorShieldsVerdancyOverflow", honorshields$verdancyOverflow);
	}

	@Inject(method = "drop(Lnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/entity/item/ItemEntity;", at = @At("HEAD"), cancellable = true)
	private void honorshields$protectDrop(ItemStack stack, boolean thrownFromHand, CallbackInfoReturnable<ItemEntity> cir) {
		Player self = (Player) (Object) this;
		if (self instanceof ServerPlayer serverPlayer && ShatteredShieldItem.isProtectedShield(stack) && !ShieldManager.mayMove(serverPlayer)) cir.setReturnValue(null);
	}

	@Inject(method = "playStepSound", at = @At("HEAD"), cancellable = true)
	private void honorshields$silentFootfalls(BlockPos pos, BlockState state, CallbackInfo ci) {
		Player self = (Player) (Object) this;
		if (self.isCrouching() && honorshields$classType == ClassType.ROGUE) ci.cancel();
	}

	@Inject(method = "attack", at = @At("HEAD"))
	private void honorshields$beginRogueBackstab(Entity target, CallbackInfo ci) {
		Player self = (Player) (Object) this;
		if (self instanceof ServerPlayer serverPlayer) {
			ShieldBlockingHandler.beginPlayerAttack(serverPlayer, target);
			if (target instanceof ServerPlayer defender && TrustManager.trusts(serverPlayer, defender)) {
				honorshields$trustedAttackTarget = target;
				honorshields$trustedAttackFireTicks = target.getRemainingFireTicks();
			} else {
				honorshields$trustedAttackTarget = null;
				honorshields$trustedAttackFireTicks = -1;
			}
		}
	}

	@Inject(method = "attack", at = @At("RETURN"))
	private void honorshields$endRogueBackstab(Entity target, CallbackInfo ci) {
		Player self = (Player) (Object) this;
		if (self instanceof ServerPlayer serverPlayer) {
			ShieldBlockingHandler.endPlayerAttack(serverPlayer);
			if (honorshields$trustedAttackTarget == target && honorshields$trustedAttackFireTicks >= 0)
				target.setRemainingFireTicks(honorshields$trustedAttackFireTicks);
			honorshields$trustedAttackTarget = null;
			honorshields$trustedAttackFireTicks = -1;
		}
	}

	/** Observes the final vanilla/modded critical decision without replacing it. */
	@Inject(method = "canCriticalAttack", at = @At("RETURN"))
	private void honorshields$captureNaturalCritical(Entity target, CallbackInfoReturnable<Boolean> cir) {
		Player self = (Player) (Object) this;
		if (self instanceof ServerPlayer serverPlayer) {
			ShieldBlockingHandler.noteNaturalCritical(serverPlayer, target, Boolean.TRUE.equals(cir.getReturnValue()));
		}
	}

	/** A backstab is presented as one critical strike, never as a sweep attack. */
	@Inject(method = "isSweepAttack", at = @At("HEAD"), cancellable = true)
	private void honorshields$disableBackstabSweep(boolean fullStrengthAttack,
		boolean criticalAttack, boolean knockbackAttack, CallbackInfoReturnable<Boolean> cir) {
		Player self = (Player) (Object) this;
		if (self instanceof ServerPlayer serverPlayer
			&& ShieldBlockingHandler.hasActiveRogueBackstab(serverPlayer)) {
			cir.setReturnValue(false);
		}
	}

	/** Suppress sprint's pre-hit knockback sound so the confirmed hit has crit-only audio. */
	@Redirect(method = "attack", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/entity/player/Player;playServerSideSound(Lnet/minecraft/sounds/SoundEvent;)V"))
	private void honorshields$suppressBackstabKnockbackSound(Player self, SoundEvent sound) {
		if (self instanceof ServerPlayer serverPlayer
			&& ShieldBlockingHandler.hasActiveRogueBackstab(serverPlayer)
			&& sound == SoundEvents.PLAYER_ATTACK_KNOCKBACK) return;
		self.level().playSound(null, self.getX(), self.getY(), self.getZ(),
			sound, self.getSoundSource(), 1.0F, 1.0F);
	}

	/**
	 * Successful grounded/weak backstabs take the exact vanilla critical visual
	 * branch: standard crit sound, ServerPlayer.crit emitter, and magic-crit cue.
	 * Cancelling here also prevents the ordinary strong/weak or sweep sound from
	 * being layered over that critical feedback.
	 */
	@Inject(method = "attackVisualEffects", at = @At("HEAD"), cancellable = true)
	private void honorshields$rogueBackstabVisuals(Entity target, boolean criticalAttack,
		boolean sweepAttack, boolean fullStrengthAttack, boolean stabAttack, float magicBoost,
		CallbackInfo ci) {
		Player self = (Player) (Object) this;
		if (!(self instanceof ServerPlayer serverPlayer) || criticalAttack
			|| !ShieldBlockingHandler.isActiveRogueBackstab(serverPlayer, target)) return;
		self.level().playSound(null, self.getX(), self.getY(), self.getZ(),
			SoundEvents.PLAYER_ATTACK_CRIT, self.getSoundSource(), 1.0F, 1.0F);
		self.crit(target);
		if (magicBoost > 0.0F) self.magicCrit(target);
		ci.cancel();
	}

}
