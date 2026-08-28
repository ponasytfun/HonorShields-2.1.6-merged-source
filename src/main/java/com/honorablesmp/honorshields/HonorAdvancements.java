package com.honorablesmp.honorshields;

import com.honorablesmp.honorshields.classsystem.ClassType;
import com.honorablesmp.honorshields.shield.ShieldType;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.level.ServerPlayer;

/** Awards the custom-state advancements that vanilla criteria cannot observe directly. */
public final class HonorAdvancements {
	public static void awardOath(ServerPlayer player, ClassType classType, ShieldType shieldType) {
		award(player, "honor_awakened", "awakened");
		if (classType != null) award(player, "class_" + classType.id(), "selected");
		if (shieldType != null) award(player, "shield_" + shieldType.id(), "received");
	}

	public static void awardShield(ServerPlayer player, ShieldType shieldType) {
		if (shieldType != null) award(player, "shield_" + shieldType.id(), "received");
	}

	public static void awardExalted(ServerPlayer player) {
		award(player, "exalted_condition", "exalted");
	}

	private static void award(ServerPlayer player, String id, String criterion) {
		AdvancementHolder advancement = player.level().getServer().getAdvancements().get(HonorShieldsMod.id(id));
		if (advancement != null) player.getAdvancements().award(advancement, criterion);
	}

	private HonorAdvancements() {}
}
