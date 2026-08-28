package com.honorablesmp.honorshields.mixin;

import com.honorablesmp.honorshields.classsystem.ClassManager;
import com.honorablesmp.honorshields.data.HonorPlayerData;
import com.honorablesmp.honorshields.network.HonorShieldsPackets;
import com.honorablesmp.honorshields.shield.ShieldManager;
import com.honorablesmp.honorshields.shield.ShatteredShieldItem;
import com.honorablesmp.honorshields.shield.SeasonTwoGameplay;
import com.honorablesmp.honorshields.shield.ShieldResourceManager;
import com.honorablesmp.honorshields.shield.ShieldAbilityHandler;
import com.honorablesmp.honorshields.shield.MonsoonHandler;
import com.honorablesmp.honorshields.shield.PlowHandler;
import com.honorablesmp.honorshields.shield.VagabondHandler;
import com.honorablesmp.honorshields.classsystem.PassiveTriggerHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {
	@Unique private boolean honorshields$deathProcessed;

	@Inject(method = "die", at = @At("HEAD"))
	private void honorshields$preserveShieldOnDeath(DamageSource source, CallbackInfo ci) {
		if (honorshields$deathProcessed) return;
		honorshields$deathProcessed = true;
		ServerPlayer player = (ServerPlayer) (Object) this;
		SeasonTwoGameplay.resetPlayer(player);
		ShieldAbilityHandler.resetPlayer(player);
		PassiveTriggerHandler.resetPlayer(player);
		ShieldResourceManager.resetPlayer(player);
		MonsoonHandler.resetPlayer(player);
		VagabondHandler.resetPlayer(player);
		PlowHandler.resetPlayer(player);
		((HonorPlayerData) player).honorshields$setWardenStoredDamage(0.0F);
		ShieldManager.onDeath(player);
	}

	@Inject(method = "drop(Z)V", at = @At("HEAD"), cancellable = true)
	private void honorshields$protectDropKey(boolean entireStack, CallbackInfo ci) {
		ServerPlayer player = (ServerPlayer) (Object) this;
		if (!ShieldManager.mayMove(player) && ShatteredShieldItem.isProtectedShield(player.getInventory().getSelectedItem())) {
			player.connection.send(player.getInventory().createInventoryUpdatePacket(player.getInventory().getSelectedSlot()));
			ci.cancel();
		}
	}

	@Inject(method = "drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;", at = @At("HEAD"), cancellable = true)
	private void honorshields$protectDirectDrop(ItemStack stack, boolean randomly, boolean thrownFromHand, CallbackInfoReturnable<ItemEntity> cir) {
		ServerPlayer player = (ServerPlayer) (Object) this;
		if (!ShieldManager.mayMove(player) && ShatteredShieldItem.isProtectedShield(stack)) cir.setReturnValue(null);
	}

	@Inject(method = "restoreFrom", at = @At("TAIL"))
	private void honorshields$copyOathState(ServerPlayer oldPlayer, boolean restoreAll, CallbackInfo ci) {
		ServerPlayer player = (ServerPlayer) (Object) this;
		HonorPlayerData oldData = (HonorPlayerData) oldPlayer;
		HonorPlayerData data = (HonorPlayerData) player;
		data.honorshields$setClassType(oldData.honorshields$getClassType());
		data.honorshields$setShieldType(oldData.honorshields$getShieldType());
		data.honorshields$setShieldCondition(oldData.honorshields$getShieldCondition());
		data.honorshields$setShieldShattered(oldData.honorshields$isShieldShattered());
		data.honorshields$getTrustedPlayers().clear();
		data.honorshields$getTrustedPlayers().addAll(oldData.honorshields$getTrustedPlayers());
		data.honorshields$setLeaderboardVisible(oldData.honorshields$isLeaderboardVisible());
		data.honorshields$setLeaderboardScale(oldData.honorshields$getLeaderboardScale());
		data.honorshields$setOathGeneration(oldData.honorshields$getOathGeneration());
		data.honorshields$setThunderCharge(oldData.honorshields$getThunderCharge());
		data.honorshields$setVoidCharge(oldData.honorshields$getVoidCharge());
		data.honorshields$setTempestCharge(oldData.honorshields$getTempestCharge());
		data.honorshields$setDawnSunCharge(oldData.honorshields$getDawnSunCharge());
		data.honorshields$setDawnFullSunArmed(oldData.honorshields$isDawnFullSunArmed());
		data.honorshields$setDawnLastChance(oldData.honorshields$hasDawnLastChance());
		data.honorshields$setDawnLastChanceProgress(oldData.honorshields$getDawnLastChanceProgress());
		data.honorshields$setWardenStoredDamage(oldData.honorshields$getWardenStoredDamage());
		data.honorshields$setStoneBulwarkReadyAt(oldData.honorshields$getStoneBulwarkReadyAt());
		data.honorshields$setBerserkerResolveReadyAt(oldData.honorshields$getBerserkerResolveReadyAt());
		data.honorshields$setDemonCoreReadyAt(oldData.honorshields$getDemonCoreReadyAt());
		data.honorshields$setAbsoluteZeroReadyAt(oldData.honorshields$getAbsoluteZeroReadyAt());
		data.honorshields$setElderMercyReadyAt(oldData.honorshields$getElderMercyReadyAt());
		data.honorshields$setBlackoutReadyAt(oldData.honorshields$getBlackoutReadyAt());
		data.honorshields$setEdenInterventionReadyAt(oldData.honorshields$getEdenInterventionReadyAt());
		data.honorshields$setVerdancy(oldData.honorshields$getVerdancy());
		data.honorshields$setVerdancyOverflow(oldData.honorshields$getVerdancyOverflow());
		ShieldManager.restoreAfterRespawn(player);

		// Run after PlayerList finishes the vanilla respawn handshake, so the new entity's
		// class stats and lowered condition are the state the client receives.
		player.level().getServer().execute(() -> {
			if (data.honorshields$getClassType() != null) ClassManager.applyClassStats(player, data.honorshields$getClassType());
			ShieldManager.tick(player);
			HonorShieldsPackets.syncPlayer(player);
		});
	}
}
