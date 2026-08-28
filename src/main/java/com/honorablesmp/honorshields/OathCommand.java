package com.honorablesmp.honorshields;

import com.honorablesmp.honorshields.classsystem.ClassManager;
import com.honorablesmp.honorshields.data.HonorWorldState;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;

public final class OathCommand {
	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("oath")
			.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.executes(context -> activate(context.getSource()))
			.then(Commands.literal("cancel").executes(context -> cancel(context.getSource()))));
	}

	private static int activate(CommandSourceStack source) {
		HonorWorldState state = HonorWorldState.get(source.getServer());
		if (state.isActivated()) {
			source.sendFailure(Component.literal("The HonorShields oath has already been sworn."));
			return 0;
		}
		state.activate();
		Component message = Component.literal("An ancient oath has been sworn. HonorShields awakens!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
		source.getServer().getPlayerList().broadcastSystemMessage(message, false);
		for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
			player.playSound(SoundEvents.ENCHANTMENT_TABLE_USE, 1.0F, 0.8F);
			ClassManager.requestSelection(player);
		}
		return 1;
	}

	private static int cancel(CommandSourceStack source) {
		HonorWorldState state = HonorWorldState.get(source.getServer());
		if (!state.isActivated()) {
			source.sendFailure(Component.literal("There is no active HonorShields oath to cancel."));
			return 0;
		}
		state.deactivate();
		for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) ClassManager.cancelOath(player);
		Component message = Component.literal("The ancient oath has been cancelled. All HonorShields have faded.")
			.withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
		source.getServer().getPlayerList().broadcastSystemMessage(message, false);
		return 1;
	}

	private OathCommand() {}
}
