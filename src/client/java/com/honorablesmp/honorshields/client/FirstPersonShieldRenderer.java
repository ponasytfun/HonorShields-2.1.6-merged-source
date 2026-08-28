package com.honorablesmp.honorshields.client;

import com.honorablesmp.honorshields.config.HonorShieldsConfig;
import com.honorablesmp.honorshields.shield.ShatteredShieldItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

public final class FirstPersonShieldRenderer {
	public static boolean begin(PoseStack matrices, AbstractClientPlayer player, InteractionHand hand, ItemStack stack) {
		if (hand != InteractionHand.OFF_HAND || !ShatteredShieldItem.isProtectedShield(stack)) return false;
		var config = HonorShieldsConfig.get();
		if (!config.showShieldInFirstPerson) return false;
		matrices.pushPose();

		HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
		float side = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
		boolean guarding = player.isUsingItem() && player.getUsedItemHand() == hand;
		float scale = Math.max(0.4F, Math.min(2.8F, config.shieldRenderScale * config.shieldSize));

		// Guarding keeps the established size, height, and depth. Its horizontal
		// offset leaves only a thin slice of the left edge outside the viewport;
		// the blocking item model removes every residual
		// pitch/roll so the face is straight and the pointed end remains down.
		if (guarding) {
			matrices.translate(-0.16F, -0.08F, -0.22F);
		} else {
			matrices.translate(side * 0.18F + 0.05F, -0.08F, -0.15F);
			matrices.mulPose(Axis.XP.rotationDegrees(-4.0F));
			matrices.mulPose(Axis.YP.rotationDegrees(side * -3.0F));
			matrices.mulPose(Axis.ZP.rotationDegrees(side * 5.0F));
		}
		AbilityAnimationController.Pose pose = AbilityAnimationController.sample(player.getId());
		if (pose.active()) {
			float motion = guarding ? 0.28F : 1.0F;
			matrices.translate(side * pose.firstX() * motion, pose.firstY() * motion, pose.firstZ() * motion);
			// Preserve the deliberately straight blocking face. Cast animations may
			// add a tiny positional recoil while guarding, but never re-angle it.
			if (!guarding) {
				matrices.mulPose(Axis.XP.rotationDegrees(pose.firstPitch()));
				matrices.mulPose(Axis.YP.rotationDegrees(side * pose.firstYaw()));
				matrices.mulPose(Axis.ZP.rotationDegrees(side * pose.firstRoll()));
			}
		}
		matrices.scale(scale, scale, scale);
		return true;
	}

	public static void end(PoseStack matrices, boolean active) {
		if (active) matrices.popPose();
	}

	private FirstPersonShieldRenderer() {}
}
