package com.honorablesmp.honorshields.mixin;

import com.honorablesmp.honorshields.classsystem.TrustManager;
import com.honorablesmp.honorshields.shield.VagabondHandler;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractArrow.class)
public abstract class AbstractArrowMixin {
	@org.spongepowered.asm.mixin.Unique private LivingEntity honorshields$trustedVictim;
	@org.spongepowered.asm.mixin.Unique private List<MobEffectInstance> honorshields$trustedEffects;
	@org.spongepowered.asm.mixin.Unique private int honorshields$trustedFireTicks;

	@Inject(method = "onHitEntity", at = @At("HEAD"), cancellable = true)
	private void honorshields$vagabondDartHit(EntityHitResult hit, CallbackInfo ci) {
		AbstractArrow arrow = (AbstractArrow) (Object) this;
		if (VagabondHandler.handleDartHit(arrow, hit)) {
			ci.cancel();
			return;
		}
		if (!(arrow.level() instanceof ServerLevel)
			|| !(arrow.getOwner() instanceof ServerPlayer owner)
			|| !(hit.getEntity() instanceof ServerPlayer victim)
			|| !TrustManager.trusts(owner, victim)) return;
		// Vanilla applies tipped-arrow effects after the damage call. Snapshot the
		// trusted target so the RETURN hook can roll back fire and every harmful
		// secondary effect while retaining the physical arrow hit/knockback.
		honorshields$trustedVictim = victim;
		honorshields$trustedEffects = new ArrayList<>();
		for (MobEffectInstance effect : victim.getActiveEffects())
			honorshields$trustedEffects.add(new MobEffectInstance(effect));
		honorshields$trustedFireTicks = victim.getRemainingFireTicks();
	}

	@Inject(method = "onHitEntity", at = @At("RETURN"))
	private void honorshields$restoreTrustedSecondaryEffects(EntityHitResult hit, CallbackInfo ci) {
		if (honorshields$trustedVictim == null || honorshields$trustedEffects == null) return;
		LivingEntity victim = honorshields$trustedVictim;
		victim.removeAllEffects();
		for (MobEffectInstance effect : honorshields$trustedEffects) victim.addEffect(new MobEffectInstance(effect));
		victim.setRemainingFireTicks(honorshields$trustedFireTicks);
		honorshields$trustedVictim = null;
		honorshields$trustedEffects = null;
		honorshields$trustedFireTicks = 0;
	}
}
