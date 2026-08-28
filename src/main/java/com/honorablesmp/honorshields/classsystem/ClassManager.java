package com.honorablesmp.honorshields.classsystem;

import com.honorablesmp.honorshields.data.HonorPlayerData;
import com.honorablesmp.honorshields.HonorAdvancements;
import com.honorablesmp.honorshields.data.HonorWorldState;
import com.honorablesmp.honorshields.network.HonorShieldsPackets;
import com.honorablesmp.honorshields.shield.ShieldManager;
import com.honorablesmp.honorshields.shield.ShieldCondition;
import com.honorablesmp.honorshields.shield.ShieldAbilityHandler;
import com.honorablesmp.honorshields.shield.ShieldType;
import com.honorablesmp.honorshields.shield.ShieldResourceManager;
import com.honorablesmp.honorshields.shield.MonsoonHandler;
import com.honorablesmp.honorshields.shield.PlowHandler;
import com.honorablesmp.honorshields.shield.VagabondHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.ArrayList;

public final class ClassManager {
	public static boolean assignClass(ServerPlayer player, ClassType type, boolean force) {
		HonorPlayerData data = (HonorPlayerData) player;
		if (!force && !HonorWorldState.get(player.level().getServer()).isActivated()) {
			player.sendSystemMessage(Component.literal("The HonorShields oath is not active.").withStyle(ChatFormatting.RED));
			return false;
		}
		if (!force && data.honorshields$getClassType() != null) {
			player.sendSystemMessage(Component.literal("Your oath is already sworn.").withStyle(ChatFormatting.RED));
			return false;
		}
		if (force) {
			ShieldAbilityHandler.resetPlayer(player);
			PassiveTriggerHandler.resetPlayer(player);
			MonsoonHandler.resetPlayer(player);
			VagabondHandler.resetPlayer(player);
			PlowHandler.resetPlayer(player);
			ShieldManager.beginClassSwap(player);
		}
		ShieldType shield = type.shields().get(player.getRandom().nextInt(type.shields().size()));
		data.honorshields$setClassType(type);
		data.honorshields$setShieldType(shield);
		data.honorshields$setShieldCondition(ShieldCondition.HONORED);
		data.honorshields$setShieldShattered(false);
		data.honorshields$setOathGeneration(HonorWorldState.get(player.level().getServer()).generation());
		data.honorshields$setDawnLastChance(false);
		data.honorshields$setDawnLastChanceProgress(0L);
		data.honorshields$setStoneBulwarkReadyAt(0L);
		data.honorshields$setBerserkerResolveReadyAt(0L);
		data.honorshields$setDemonCoreReadyAt(0L);
		data.honorshields$setAbsoluteZeroReadyAt(0L);
		data.honorshields$setElderMercyReadyAt(0L);
		data.honorshields$setBlackoutReadyAt(0L);
		data.honorshields$setEdenInterventionReadyAt(0L);
		data.honorshields$setVerdancy(0);
		data.honorshields$setVerdancyOverflow(0);
		ShieldResourceManager.initializeForShield(player, shield);
		applyClassStats(player, type);
		ShieldManager.equipAssigned(player);
		HonorShieldsPackets.syncPlayer(player);
		HonorShieldsPackets.reveal(player, shield);
		HonorAdvancements.awardOath(player, type, shield);
		player.sendSystemMessage(Component.literal("You swore the oath of the " + type.displayName() + ".").withStyle(ChatFormatting.GOLD));
		return true;
	}

	public static boolean assignShield(ServerPlayer player, ShieldType shield) {
		HonorPlayerData data = (HonorPlayerData) player;
		if (data.honorshields$getClassType() == null) return false;
		ShieldAbilityHandler.resetPlayer(player);
		PassiveTriggerHandler.resetPlayer(player);
		MonsoonHandler.resetPlayer(player);
		VagabondHandler.resetPlayer(player);
		PlowHandler.resetPlayer(player);
		ShieldManager.beginClassSwap(player);
		data.honorshields$setShieldType(shield);
		data.honorshields$setShieldCondition(ShieldCondition.HONORED);
		data.honorshields$setShieldShattered(false);
		ShieldResourceManager.initializeForShield(player, shield);
		ShieldManager.equipAssigned(player);
		HonorShieldsPackets.syncPlayer(player);
		HonorShieldsPackets.reveal(player, shield);
		HonorAdvancements.awardShield(player, shield);
		return true;
	}

	public static boolean rerollShield(ServerPlayer player) {
		HonorPlayerData data = (HonorPlayerData) player;
		ClassType classType = data.honorshields$getClassType();
		ShieldType current = data.honorshields$getShieldType();
		if (classType == null || current == null || data.honorshields$isShieldShattered()) return false;
		var choices = new ArrayList<>(classType.shields());
		choices.remove(current);
		if (choices.isEmpty()) return false;
		ShieldCondition condition = data.honorshields$getShieldCondition();
		ShieldAbilityHandler.resetPlayer(player);
		PassiveTriggerHandler.resetPlayer(player);
		MonsoonHandler.resetPlayer(player);
		VagabondHandler.resetPlayer(player);
		PlowHandler.resetPlayer(player);
		ShieldManager.beginClassSwap(player);
		ShieldType replacement = choices.get(player.getRandom().nextInt(choices.size()));
		data.honorshields$setShieldType(replacement);
		data.honorshields$setShieldCondition(condition);
		ShieldResourceManager.initializeForShield(player, replacement);
		ShieldManager.equipAssigned(player);
		HonorShieldsPackets.syncPlayer(player);
		HonorShieldsPackets.revealReroll(player, replacement);
		HonorAdvancements.awardShield(player, replacement);
		player.sendSystemMessage(Component.literal("Your oath reshapes into the " + replacement.displayName() + " Shield.")
			.withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
		return true;
	}

	public static void reset(ServerPlayer player) {
		ShieldAbilityHandler.resetPlayer(player);
		PassiveTriggerHandler.resetPlayer(player);
		ShieldResourceManager.resetPlayer(player);
		MonsoonHandler.resetPlayer(player);
		VagabondHandler.resetPlayer(player);
		PlowHandler.resetPlayer(player);
		ShieldManager.beginClassSwap(player);
		HonorPlayerData data = (HonorPlayerData) player;
		data.honorshields$setClassType(null);
		data.honorshields$setShieldType(null);
		data.honorshields$setShieldCondition(ShieldCondition.HONORED);
		data.honorshields$setShieldShattered(false);
		data.honorshields$setBerserkerResolveReadyAt(0L);
		data.honorshields$setDawnLastChance(false);
		data.honorshields$setDawnLastChanceProgress(0L);
		data.honorshields$setStoneBulwarkReadyAt(0L);
		data.honorshields$setDemonCoreReadyAt(0L);
		data.honorshields$setAbsoluteZeroReadyAt(0L);
		data.honorshields$setElderMercyReadyAt(0L);
		data.honorshields$setBlackoutReadyAt(0L);
		data.honorshields$setEdenInterventionReadyAt(0L);
		data.honorshields$setVerdancy(0);
		data.honorshields$setVerdancyOverflow(0);
		ShieldResourceManager.initializeForShield(player, null);
		var maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
		if (maxHealth != null) maxHealth.setBaseValue(20.0D);
		var scale = player.getAttribute(Attributes.SCALE);
		if (scale != null) scale.setBaseValue(1.0D);
		if (player.getHealth() > 20.0F) player.setHealth(20.0F);
		HonorShieldsPackets.syncPlayer(player);
		requestSelection(player);
	}

	public static void cancelOath(ServerPlayer player) {
		ShieldAbilityHandler.resetPlayer(player);
		PassiveTriggerHandler.resetPlayer(player);
		ShieldResourceManager.resetPlayer(player);
		MonsoonHandler.resetPlayer(player);
		VagabondHandler.resetPlayer(player);
		PlowHandler.resetPlayer(player);
		ShieldManager.beginClassSwap(player);
		HonorPlayerData data = (HonorPlayerData) player;
		data.honorshields$setClassType(null);
		data.honorshields$setShieldType(null);
		data.honorshields$setShieldCondition(ShieldCondition.HONORED);
		data.honorshields$setShieldShattered(false);
		data.honorshields$getTrustedPlayers().clear();
		data.honorshields$setLeaderboardVisible(true);
		data.honorshields$setLeaderboardScale(1.0F);
		data.honorshields$setOathGeneration(0);
		data.honorshields$setBerserkerResolveReadyAt(0L);
		data.honorshields$setDawnLastChance(false);
		data.honorshields$setDawnLastChanceProgress(0L);
		data.honorshields$setStoneBulwarkReadyAt(0L);
		data.honorshields$setDemonCoreReadyAt(0L);
		data.honorshields$setAbsoluteZeroReadyAt(0L);
		data.honorshields$setElderMercyReadyAt(0L);
		data.honorshields$setBlackoutReadyAt(0L);
		data.honorshields$setEdenInterventionReadyAt(0L);
		data.honorshields$setVerdancy(0);
		data.honorshields$setVerdancyOverflow(0);
		ShieldResourceManager.initializeForShield(player, null);
		var maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
		if (maxHealth != null) maxHealth.setBaseValue(20.0D);
		var scale = player.getAttribute(Attributes.SCALE);
		if (scale != null) scale.setBaseValue(1.0D);
		if (player.getHealth() > 20.0F) player.setHealth(20.0F);
		HonorShieldsPackets.syncPlayer(player);
	}

	public static void applyClassStats(ServerPlayer player, ClassType type) {
		var maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
		if (maxHealth != null) maxHealth.setBaseValue(type.maxHealth());
		var scale = player.getAttribute(Attributes.SCALE);
		if (scale != null) scale.setBaseValue(type == ClassType.BERSERKER ? 2.05D / 1.8D : 1.0D);
		if (player.getHealth() > type.maxHealth()) player.setHealth(type.maxHealth());
	}

	public static void onJoin(ServerPlayer player) {
		HonorPlayerData data = (HonorPlayerData) player;
		if (data.honorshields$getClassType() != null) {
			applyClassStats(player, data.honorshields$getClassType());
			ShieldManager.equipAssigned(player);
			HonorAdvancements.awardOath(player, data.honorshields$getClassType(), data.honorshields$getShieldType());
		}
		HonorShieldsPackets.syncPlayer(player);
	}

	public static void requestSelection(ServerPlayer player) {
		if (HonorWorldState.get(player.level().getServer()).isActivated()
			&& ((HonorPlayerData) player).honorshields$getClassType() == null) HonorShieldsPackets.openClassScreen(player);
	}

	private ClassManager() {}
}
