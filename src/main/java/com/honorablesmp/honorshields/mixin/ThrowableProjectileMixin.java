package com.honorablesmp.honorshields.mixin;

import com.honorablesmp.honorshields.shield.VagabondHandler;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Snowball.class)
public abstract class ThrowableProjectileMixin {
	@Inject(method = "onHit", at = @At("HEAD"), cancellable = true)
	private void honorshields$stickySnowHit(HitResult hit, CallbackInfo ci) {
		if (VagabondHandler.handleStickySnowHit((Snowball) (Object) this, hit)) ci.cancel();
	}
}
