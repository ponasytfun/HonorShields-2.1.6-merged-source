package com.honorablesmp.honorshields.client.mixin;

import com.honorablesmp.honorshields.client.RogueStealthRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidArmorLayer.class)
public abstract class HumanoidArmorLayerMixin {
	@Inject(
		method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;FF)V",
		at = @At("HEAD"), cancellable = true
	)
	private void honorshields$hideRogueStealthArmor(PoseStack matrices, SubmitNodeCollector collector, int light,
		HumanoidRenderState state, float yRot, float xRot, CallbackInfo ci) {
		if (RogueStealthRenderState.isActive(state)) ci.cancel();
	}
}
