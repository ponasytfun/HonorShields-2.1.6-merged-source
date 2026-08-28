package com.honorablesmp.honorshields.client.mixin;

import com.honorablesmp.honorshields.client.RogueStealthRenderState;
import com.honorablesmp.honorshields.shield.ShatteredShieldItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerMixin {
	@Inject(
		method = "submitArmWithItem(Lnet/minecraft/client/renderer/entity/state/ArmedEntityRenderState;Lnet/minecraft/client/renderer/item/ItemStackRenderState;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V",
		at = @At("HEAD"), cancellable = true
	)
	private void honorshields$hideRogueStealthShield(ArmedEntityRenderState state, ItemStackRenderState itemState,
		ItemStack stack, HumanoidArm arm, PoseStack matrices, SubmitNodeCollector collector, int light, CallbackInfo ci) {
		boolean offhand = arm != state.mainArm;
		if (offhand && RogueStealthRenderState.isActive(state) && ShatteredShieldItem.isProtectedShield(stack)) ci.cancel();
	}
}
