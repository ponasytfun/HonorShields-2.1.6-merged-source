package com.honorablesmp.honorshields.mixin;

import com.honorablesmp.honorshields.classsystem.ClassType;
import com.honorablesmp.honorshields.data.HonorPlayerData;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EnchantmentHelper.class)
public abstract class EnchantmentHelperMixin {
	@Unique private static final int HONORSHIELDS_IMPALING_LEVEL = 5;
	@Unique private static final float HONORSHIELDS_IMPALING_V_BONUS = 2.5F * HONORSHIELDS_IMPALING_LEVEL;
	@Unique private static final float HONORSHIELDS_SHARPNESS_II_BONUS = 1.5F;

	@Inject(method = "modifyDamage", at = @At("RETURN"), cancellable = true)
	private static void honorshields$drownedImpalingBalance(ServerLevel level, ItemStack itemStack, Entity victim,
		DamageSource damageSource, float damage, CallbackInfoReturnable<Float> cir) {
		if (!(damageSource.getEntity() instanceof ServerPlayer attacker)
			|| ((HonorPlayerData) attacker).honorshields$getClassType() != ClassType.DROWNED
			|| !itemStack.is(Items.TRIDENT)
			|| EnchantmentHelper.getItemEnchantmentLevel(
				level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.IMPALING), itemStack
			) != HONORSHIELDS_IMPALING_LEVEL) return;

		float adjustedDamage = cir.getReturnValueF() + HONORSHIELDS_SHARPNESS_II_BONUS;
		if (victim.is(EntityTypeTags.SENSITIVE_TO_IMPALING)) adjustedDamage -= HONORSHIELDS_IMPALING_V_BONUS;
		cir.setReturnValue(adjustedDamage);
	}
}
