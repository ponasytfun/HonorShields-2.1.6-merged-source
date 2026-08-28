package com.honorablesmp.honorshields.mixin;

import com.honorablesmp.honorshields.data.HonorPlayerData;
import com.honorablesmp.honorshields.shield.AnglerLoot;
import com.honorablesmp.honorshields.shield.ShieldType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FishingHook.class)
public abstract class FishingHookMixin {
	@Shadow private int nibble;
	@Shadow private int timeUntilLured;
	@Shadow private int timeUntilHooked;
	@Unique private boolean honorshields$hadBite;
	@Unique private int honorshields$anglerTicks;

	@Inject(method = "tick", at = @At("TAIL"))
	private void honorshields$accelerateAnglerFishing(CallbackInfo ci) {
		FishingHook self = (FishingHook) (Object) this;
		if (!(self.getPlayerOwner() instanceof ServerPlayer player)) return;
		if (!honorshields$hasUsableAngler(player)) {
			honorshields$anglerTicks = 0;
			return;
		}
		// One extra countdown tick every two game ticks makes the lure and
		// hook phases progress at roughly 1.5x their vanilla rate.
		if (++honorshields$anglerTicks >= 2) {
			honorshields$anglerTicks = 0;
			timeUntilLured = Math.max(0, timeUntilLured - 1);
			timeUntilHooked = Math.max(0, timeUntilHooked - 1);
		}
	}

	@Inject(method = "retrieve", at = @At("HEAD"))
	private void honorshields$beforeRetrieve(ItemStack rod, CallbackInfoReturnable<Integer> cir) {
		honorshields$hadBite = this.nibble > 0;
	}

	@Inject(method = "retrieve", at = @At("TAIL"))
	private void honorshields$bonusLoot(ItemStack rod, CallbackInfoReturnable<Integer> cir) {
		FishingHook self = (FishingHook) (Object) this;
		if (!honorshields$hadBite || !(self.getPlayerOwner() instanceof ServerPlayer player)) return;
		if (honorshields$hasUsableAngler(player) && player.getRandom().nextFloat() < 0.50F) {
			player.drop(AnglerLoot.roll(player), false);
		}
	}

	@Unique
	private static boolean honorshields$hasUsableAngler(ServerPlayer player) {
		HonorPlayerData data = (HonorPlayerData) player;
		return data.honorshields$getClassType() != null
			&& data.honorshields$getShieldType() == ShieldType.ANGLER
			&& data.honorshields$getShieldCondition().usable()
			&& ShieldType.fromStack(player.getOffhandItem()) == ShieldType.ANGLER;
	}
}
