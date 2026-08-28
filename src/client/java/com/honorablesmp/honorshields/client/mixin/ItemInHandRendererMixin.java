package com.honorablesmp.honorshields.client.mixin;

import com.honorablesmp.honorshields.client.FirstPersonShieldRenderer;
import com.honorablesmp.honorshields.client.RogueStealthRenderState;
import com.honorablesmp.honorshields.config.HonorShieldsConfig;
import com.honorablesmp.honorshields.shield.ShatteredShieldItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
	@Unique private boolean honorshields$scaled;

	@Inject(
		method = "submitArmWithItem(Lnet/minecraft/client/player/AbstractClientPlayer;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V",
		at = @At("HEAD"),
		cancellable = true
	)
	private void honorshields$before(AbstractClientPlayer player, float frameInterp, float xRot, InteractionHand hand, float attack,
		ItemStack stack, float armHeight, PoseStack matrices, SubmitNodeCollector collector, int light, CallbackInfo ci) {
		if (ShatteredShieldItem.isProtectedShield(stack) && (!HonorShieldsConfig.get().showShieldInFirstPerson
			|| RogueStealthRenderState.isActive(player))) { ci.cancel(); return; }
		honorshields$scaled = FirstPersonShieldRenderer.begin(matrices, player, hand, stack);
	}

	@Inject(
		method = "submitArmWithItem(Lnet/minecraft/client/player/AbstractClientPlayer;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V",
		at = @At("RETURN")
	)
	private void honorshields$after(AbstractClientPlayer player, float frameInterp, float xRot, InteractionHand hand, float attack,
		ItemStack stack, float armHeight, PoseStack matrices, SubmitNodeCollector collector, int light, CallbackInfo ci) {
		FirstPersonShieldRenderer.end(matrices, honorshields$scaled);
		honorshields$scaled = false;
	}
}
