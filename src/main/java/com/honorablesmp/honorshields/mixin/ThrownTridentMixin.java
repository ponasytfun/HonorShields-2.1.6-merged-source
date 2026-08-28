package com.honorablesmp.honorshields.mixin;

import com.honorablesmp.honorshields.shield.SeasonTwoGameplay;
import com.honorablesmp.honorshields.shield.MonsoonHandler;
import com.honorablesmp.honorshields.shield.ShieldResourceManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ThrownTrident.class)
public abstract class ThrownTridentMixin {
	@Inject(method = "tick", at = @At("HEAD"))
	private void honorshields$tidalTridentPierce(CallbackInfo ci) {
		ThrownTrident self = (ThrownTrident) (Object) this;
		if (self.getOwner() instanceof ServerPlayer owner && ShieldResourceManager.activeShield(owner) == com.honorablesmp.honorshields.shield.ShieldType.MONSOON
			&& self.getPierceLevel() < 3) ((AbstractArrowAccessor) self).honorshields$setPierceLevel((byte) 3);
	}

	@Inject(method = "onHitEntity", at = @At("TAIL"))
	private void honorshields$drownedEntityImpact(EntityHitResult hit, CallbackInfo ci) {
		ThrownTrident self = (ThrownTrident) (Object) this;
		if (!(self.getOwner() instanceof ServerPlayer owner)) return;
		SeasonTwoGameplay.tryWhirlpool(owner, hit.getLocation());
		Entity target = hit.getEntity();
		if (target instanceof LivingEntity living) {
			SeasonTwoGameplay.disableShieldWithTrident(owner, living);
			MonsoonHandler.onTridentHit(owner, living);
		}
	}

	@Inject(method = "hitBlockEnchantmentEffects", at = @At("TAIL"))
	private void honorshields$drownedBlockImpact(ServerLevel level, BlockHitResult hit, ItemStack stack, CallbackInfo ci) {
		ThrownTrident self = (ThrownTrident) (Object) this;
		if (self.getOwner() instanceof ServerPlayer owner) SeasonTwoGameplay.tryWhirlpool(owner, hit.getLocation());
	}
}
