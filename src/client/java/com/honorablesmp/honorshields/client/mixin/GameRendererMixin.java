package com.honorablesmp.honorshields.client.mixin;

import com.honorablesmp.honorshields.client.AbilityVfxManager;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A tiny additive camera impulse; vanilla hurt/view bobbing remains intact. */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@Unique private static final boolean HONORSHIELDS$ZOOMIFY_LOADED =
		FabricLoader.getInstance().isModLoaded("zoomify");

	@Inject(
		method = "bobHurt(Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V",
		at = @At("RETURN")
	)
	private void honorshields$abilityCamera(CameraRenderState state, PoseStack matrices, CallbackInfo ci) {
		// Zoomify owns the camera transform whenever it is installed. HonorShields
		// keeps particles, sounds, animation, and edge feedback, but declines even
		// its small optional shake transform so zoom projection has one clear owner.
		if (HONORSHIELDS$ZOOMIFY_LOADED) return;
		Minecraft client = Minecraft.getInstance();
		float strength = AbilityVfxManager.cameraShake();
		if (strength <= 0.0F || client.player == null) return;
		float time = AbilityVfxManager.cameraTime(client.getDeltaTracker());
		float waveA = (float) Math.sin(time * 2.31F);
		float waveB = (float) Math.sin(time * 1.73F + 1.27F);
		matrices.translate(waveB * 0.0045F * strength, waveA * 0.0032F * strength, 0.0F);
		matrices.mulPose(Axis.XP.rotationDegrees(waveA * 1.25F * strength));
		matrices.mulPose(Axis.ZP.rotationDegrees(waveB * 1.75F * strength));
	}
}
