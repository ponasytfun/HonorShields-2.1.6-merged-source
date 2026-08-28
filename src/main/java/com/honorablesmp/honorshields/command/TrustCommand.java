package com.honorablesmp.honorshields.command;

import com.honorablesmp.honorshields.classsystem.TrustManager;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class TrustCommand {
	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("trust").then(Commands.argument("player", EntityArgument.player()).executes(context -> {
			ServerPlayer owner = context.getSource().getPlayerOrException();
			ServerPlayer target = EntityArgument.getPlayer(context, "player");
			if (owner == target) {
				owner.sendSystemMessage(Component.literal("You already trust yourself.").withStyle(ChatFormatting.RED));
				return 0;
			}
			boolean trusted = TrustManager.toggle(owner, target);
			owner.sendSystemMessage(Component.literal((trusted ? "Trusted " : "No longer trusting ") + target.getName().getString() + ".").withStyle(trusted ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
			return 1;
		})));
	}

	private TrustCommand() {}
}
