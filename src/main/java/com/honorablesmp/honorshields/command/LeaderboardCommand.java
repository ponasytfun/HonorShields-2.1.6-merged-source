package com.honorablesmp.honorshields.command;

import com.honorablesmp.honorshields.data.HonorPlayerData;
import com.honorablesmp.honorshields.network.HonorShieldsPackets;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class LeaderboardCommand {
	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("leaderboard")
			.then(Commands.literal("toggle").executes(context -> toggle(context.getSource().getPlayerOrException())))
			.then(Commands.literal("scale")
				.then(Commands.literal("up").executes(context -> scale(context.getSource().getPlayerOrException(), 0.1F)))
				.then(Commands.literal("down").executes(context -> scale(context.getSource().getPlayerOrException(), -0.1F)))));
	}

	private static int toggle(ServerPlayer player) {
		HonorPlayerData data = (HonorPlayerData) player;
		data.honorshields$setLeaderboardVisible(!data.honorshields$isLeaderboardVisible());
		HonorShieldsPackets.syncPlayer(player);
		player.sendSystemMessage(Component.literal("HonorShields HUD " + (data.honorshields$isLeaderboardVisible() ? "enabled" : "disabled") + ".").withStyle(ChatFormatting.GOLD));
		return 1;
	}

	private static int scale(ServerPlayer player, float amount) {
		HonorPlayerData data = (HonorPlayerData) player;
		data.honorshields$setLeaderboardScale(data.honorshields$getLeaderboardScale() + amount);
		HonorShieldsPackets.syncPlayer(player);
		player.sendSystemMessage(Component.literal("HUD scale: %.1f".formatted(data.honorshields$getLeaderboardScale())).withStyle(ChatFormatting.GOLD));
		return 1;
	}

	private LeaderboardCommand() {}
}
