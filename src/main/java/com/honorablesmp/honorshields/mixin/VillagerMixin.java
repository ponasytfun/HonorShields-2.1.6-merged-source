package com.honorablesmp.honorshields.mixin;

import com.honorablesmp.honorshields.classsystem.ClassType;
import com.honorablesmp.honorshields.classsystem.PassiveTriggerHandler;
import com.honorablesmp.honorshields.data.HonorPlayerData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.ai.village.ReputationEventType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Villager.class)
public abstract class VillagerMixin {
	@Inject(method = "mobInteract", at = @At("HEAD"), cancellable = true)
	private void honorshields$farmerTrade(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
		Villager self = (Villager) (Object) this;
		if (player instanceof ServerPlayer serverPlayer
			&& ((HonorPlayerData) serverPlayer).honorshields$getClassType() == ClassType.FARMER
			&& self.getVillagerData().profession().is(VillagerProfession.FARMER)) {
			self.setUnhappyCounter(40);
			cir.setReturnValue(InteractionResult.FAIL);
		}
	}

	@Inject(method = "onReputationEventFrom", at = @At("HEAD"), cancellable = true)
	private void honorshields$silverTongue(ReputationEventType type, Entity source, CallbackInfo ci) {
		if (source instanceof ServerPlayer player
			&& ((HonorPlayerData) player).honorshields$getClassType() == ClassType.MERCHANT
			&& (type == ReputationEventType.VILLAGER_HURT || type == ReputationEventType.VILLAGER_KILLED)) {
			Villager self = (Villager) (Object) this;
			PassiveTriggerHandler.triggerAt(player, ClassType.MERCHANT, "Silver Tongue",
				self.position().add(0.0, self.getBbHeight() * 0.52, 0.0), self.position().subtract(player.position()));
			ci.cancel();
		}
	}

	@Inject(method = "rewardTradeXp", at = @At("TAIL"))
	private void honorshields$goldenTouch(MerchantOffer offer, CallbackInfo ci) {
		Villager self = (Villager) (Object) this;
		if (self.getTradingPlayer() instanceof ServerPlayer player
			&& ((HonorPlayerData) player).honorshields$getClassType() == ClassType.MERCHANT
			&& offer.shouldRewardExp()) {
			self.level().addFreshEntity(new ExperienceOrb(self.level(), self.getX(), self.getY() + 0.5, self.getZ(), 3 + self.getRandom().nextInt(4)));
			PassiveTriggerHandler.triggerAt(player, ClassType.MERCHANT, "Golden Touch",
				self.position().add(0.0, self.getBbHeight() * 0.52, 0.0), self.position().subtract(player.position()));
		}
	}
}
