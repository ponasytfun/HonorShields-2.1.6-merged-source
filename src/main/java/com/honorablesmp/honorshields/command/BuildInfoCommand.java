package com.honorablesmp.honorshields.command;

import com.honorablesmp.honorshields.HonorShieldsMod;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/** Small operator-only confirmation that the expected server JAR is actually running. */
public final class BuildInfoCommand {
	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("honorshields")
			.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(Commands.literal("buildinfo").executes(context -> show(context.getSource()))));
	}

	private static int show(CommandSourceStack source) {
		source.sendSuccess(() -> Component.literal("HonorShields " + HonorShieldsMod.VERSION)
			.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
		source.sendSuccess(() -> Component.literal("Build ID: " + HonorShieldsMod.BUILD_ID)
			.withStyle(ChatFormatting.AQUA), false);
		source.sendSuccess(() -> Component.literal("Feature Set: Season 2 " + HonorShieldsMod.FEATURE_SET
			+ " | Phoenix Tether: YES | Verdancy: YES | Echo Beacon: YES | Rift Mirror: YES")
			.withStyle(ChatFormatting.LIGHT_PURPLE), false);
		return 1;
	}

	private BuildInfoCommand() {}
}
