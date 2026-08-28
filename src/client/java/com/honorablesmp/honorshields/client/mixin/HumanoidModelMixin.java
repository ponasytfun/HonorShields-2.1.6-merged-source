package com.honorablesmp.honorshields.client.mixin;

import com.honorablesmp.honorshields.client.AbilityAnimationController;
import com.honorablesmp.honorshields.shield.ShieldType;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin {
	@Shadow @Final public ModelPart rightArm;
	@Shadow @Final public ModelPart leftArm;
	@Shadow @Final public ModelPart body;

	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
	private void honorshields$forearmGuard(HumanoidRenderState state, CallbackInfo ci) {
		HumanoidArm offhandArm = state.mainArm.getOpposite();
		ModelPart modelArm;
		ItemStack stack;
		if (offhandArm == HumanoidArm.RIGHT) {
			modelArm = this.rightArm;
			stack = state.rightHandItemStack;
		} else {
			modelArm = this.leftArm;
			stack = state.leftHandItemStack;
		}
		ShieldType shield = ShieldType.fromStack(stack);
		if (shield == null) return;
		poseShieldArm(state, offhandArm, modelArm);

		if (state instanceof AvatarRenderState avatar) {
			AbilityAnimationController.Pose pose = AbilityAnimationController.sample(avatar.id);
			if (pose.active()) {
				float mirror = offhandArm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
				boolean blocking = state.isUsingItem && state.useItemHand == InteractionHand.OFF_HAND;
				modelArm.xRot += pose.armX() * (blocking ? 0.16F : 1.0F);
				if (!blocking) {
					modelArm.yRot += mirror * pose.armY();
					modelArm.zRot += mirror * pose.armZ();
				}
				this.body.yRot += mirror * pose.bodyY() * (blocking ? 0.22F : 1.0F);
			}
		}
	}

	private static void poseShieldArm(HumanoidRenderState state, HumanoidArm arm, ModelPart modelArm) {
		boolean usingThisArm = state.isUsingItem && state.useItemHand == InteractionHand.OFF_HAND;
		if (!usingThisArm) return;

		// Sweep the offhand almost straight across the torso, while lowering it
		// and carrying it about twenty degrees forward. The paired signs keep the
		// pose physically mirrored for left- and right-handed players. The shield
		// stays strapped beside the forearm and its pointed end faces the floor.
		float side = arm == HumanoidArm.RIGHT ? -1.0F : 1.0F;
		modelArm.xRot = -0.50F;
		modelArm.yRot = side * 0.85F;
		modelArm.zRot = side * 0.90F;
	}
}
