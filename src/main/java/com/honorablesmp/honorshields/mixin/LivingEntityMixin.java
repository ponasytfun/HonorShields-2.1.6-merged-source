package com.honorablesmp.honorshields.mixin;

import com.honorablesmp.honorshields.classsystem.PassiveTriggerHandler;
import com.honorablesmp.honorshields.data.HonorPlayerData;
import com.honorablesmp.honorshields.shield.ShieldBlockingHandler;
import com.honorablesmp.honorshields.shield.ShieldType;
import com.honorablesmp.honorshields.shield.SeasonTwoGameplay;
import com.honorablesmp.honorshields.shield.ShieldResourceManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	@Unique private ItemStack honorshields$consumed = ItemStack.EMPTY;
	@Unique private float honorshields$saturationBefore;

	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private float honorshields$modifyDamage(float damage, ServerLevel level, DamageSource source) {
		float modified = ShieldBlockingHandler.modifyDamage((LivingEntity) (Object) this, source, damage);
		if ((Object) this instanceof ServerPlayer player) modified = ShieldResourceManager.absorbDawnDamage(player, modified);
		if (source.getEntity() instanceof ServerPlayer attacker) modified = ShieldResourceManager.applyFullSunAttack(attacker, (LivingEntity) (Object) this, modified);
		return modified;
	}

	@Inject(method = "canFreeze", at = @At("HEAD"), cancellable = true)
	private void honorshields$rimeFreezeImmunity(CallbackInfoReturnable<Boolean> cir) {
		if ((Object) this instanceof Player player && player instanceof HonorPlayerData data
			&& data.honorshields$getShieldType() == ShieldType.RIME
			&& data.honorshields$getShieldCondition().usable()
			&& ShieldType.fromStack(player.getOffhandItem()) == ShieldType.RIME) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "applyItemBlocking", at = @At("RETURN"))
	private void honorshields$blocked(ServerLevel level, DamageSource source, float damage, CallbackInfoReturnable<Float> cir) {
		if ((Object) this instanceof ServerPlayer player && cir.getReturnValueF() > 0.0F) ShieldBlockingHandler.onBlocked(player, source, cir.getReturnValueF());
	}

	@Inject(method = "knockback", at = @At("HEAD"), cancellable = true)
	private void honorshields$cancelShieldKnockback(double strength, double x, double z, DamageSource source,
		float resistance, boolean markHurt, CallbackInfo ci) {
		if (ShieldBlockingHandler.blocksKnockback((LivingEntity) (Object) this)) ci.cancel();
	}

	@Inject(method = "causeFallDamage", at = @At("HEAD"), cancellable = true)
	private void honorshields$fall(double distance, float modifier, DamageSource source, CallbackInfoReturnable<Boolean> cir) {
		if (!((Object) this instanceof ServerPlayer player)) return;
		HonorPlayerData data = (HonorPlayerData) player;
		ShieldType equipped = ShieldType.fromStack(player.getOffhandItem());
		ShieldType shield = data.honorshields$getShieldCondition().usable() && equipped == data.honorshields$getShieldType()
			? equipped : null;
		boolean protectedFall = SeasonTwoGameplay.onFall(player, distance, modifier)
			|| shield == ShieldType.VAGABOND && player.getY() < 64
			|| shield == ShieldType.VOID && player.level().getMaxLocalRawBrightness(player.blockPosition()) < 8;
		if (protectedFall) cir.setReturnValue(false);
	}

	@Inject(method = "completeUsingItem", at = @At("HEAD"))
	private void honorshields$beforeFood(CallbackInfo ci) {
		if ((Object) this instanceof ServerPlayer player) {
			honorshields$consumed = player.getUseItem().copy();
			honorshields$saturationBefore = player.getFoodData().getSaturationLevel();
		}
	}

	@Inject(method = "completeUsingItem", at = @At("TAIL"))
	private void honorshields$afterFood(CallbackInfo ci) {
		if ((Object) this instanceof ServerPlayer player && !honorshields$consumed.isEmpty()) {
			PassiveTriggerHandler.onFoodConsumed(player, honorshields$consumed, honorshields$saturationBefore);
			honorshields$consumed = ItemStack.EMPTY;
		}
	}
}
