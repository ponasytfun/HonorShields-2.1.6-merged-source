package com.honorablesmp.honorshields.command;

import com.honorablesmp.honorshields.shield.ConditionScrollItem;
import com.honorablesmp.honorshields.shield.ShieldCondition;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.Arrays;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class WithdrawCommand {
	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("withdraw")
			.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(Commands.literal("conditionscroll").then(Commands.argument("condition", StringArgumentType.word())
				.suggests((context, builder) -> SharedSuggestionProvider.suggest(Arrays.stream(ShieldCondition.values()).map(ShieldCondition::id), builder))
				.executes(context -> withdraw(context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "condition"))))));
	}

	private static int withdraw(ServerPlayer player, String id) {
		ShieldCondition condition = ShieldCondition.byId(id);
		if (!player.getInventory().add(ConditionScrollItem.create(condition))) player.drop(ConditionScrollItem.create(condition), false);
		player.sendSystemMessage(Component.literal("Withdrew a Condition Scroll (one-tier upgrade).")
			.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		return 1;
	}

	private WithdrawCommand() {}
}
