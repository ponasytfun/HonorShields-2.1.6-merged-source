package com.honorablesmp.honorshields.mixin;

import com.honorablesmp.honorshields.data.HonorPlayerData;
import com.honorablesmp.honorshields.shield.ShieldType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.PowderSnowBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Gives a usable Rime shield the same powder-snow footing as leather boots. */
@Mixin(PowderSnowBlock.class)
public abstract class PowderSnowBlockMixin {
	@Inject(method = "canEntityWalkOnPowderSnow", at = @At("HEAD"), cancellable = true)
	private static void honorshields$rimePowderSnowFooting(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (entity instanceof Player player && player instanceof HonorPlayerData data
			&& data.honorshields$getShieldType() == ShieldType.RIME
			&& data.honorshields$getShieldCondition().usable()
			&& ShieldType.fromStack(player.getOffhandItem()) == ShieldType.RIME) {
			cir.setReturnValue(true);
		}
	}
}
