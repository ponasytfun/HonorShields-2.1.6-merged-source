package com.honorablesmp.honorshields.command;

import com.honorablesmp.honorshields.classsystem.ClassManager;
import com.honorablesmp.honorshields.classsystem.ClassType;
import com.honorablesmp.honorshields.network.HonorShieldsPackets;
import com.honorablesmp.honorshields.shield.ShieldCondition;
import com.honorablesmp.honorshields.shield.ShieldManager;
import com.honorablesmp.honorshields.shield.ShieldType;
import com.honorablesmp.honorshields.shield.ShatteredShieldItem;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.Arrays;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class TestCommand {
	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("test")
			.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(Commands.literal("class").then(Commands.argument("class", StringArgumentType.word())
				.suggests((context, builder) -> SharedSuggestionProvider.suggest(Arrays.stream(ClassType.values()).map(ClassType::id), builder))
				.executes(context -> setClass(context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "class")))))
			.then(Commands.literal("shield").then(Commands.argument("shield", StringArgumentType.word())
				.suggests((context, builder) -> SharedSuggestionProvider.suggest(Arrays.stream(ShieldType.values()).map(ShieldType::id), builder))
				.executes(context -> setShield(context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "shield")))))
			.then(Commands.literal("condition").then(Commands.argument("condition", StringArgumentType.word())
				.suggests((context, builder) -> SharedSuggestionProvider.suggest(Arrays.stream(ShieldCondition.values()).map(ShieldCondition::id), builder))
				.executes(context -> setCondition(context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "condition")))))
			.then(Commands.literal("reset").executes(context -> {
				ServerPlayer player = context.getSource().getPlayerOrException();
				ClassManager.reset(player);
				player.sendSystemMessage(Component.literal("HonorShields test state reset.").withStyle(ChatFormatting.GOLD));
				return 1;
			})));
	}

	private static int setClass(ServerPlayer player, String id) {
		ClassType type = ClassType.byId(id);
		if (type == null) return 0;
		ClassManager.assignClass(player, type, true);
		return 1;
	}

	private static int setShield(ServerPlayer player, String id) {
		ShieldType type = ShieldType.byId(id);
		if (type == null || !ClassManager.assignShield(player, type)) {
			player.sendSystemMessage(Component.literal("Select a class before testing a shield.").withStyle(ChatFormatting.RED));
			return 0;
		}
		return 1;
	}

	private static int setCondition(ServerPlayer player, String id) {
		ShieldCondition condition = ShieldCondition.byId(id);
		if (!ShatteredShieldItem.isProtectedShield(player.getOffhandItem())) {
			player.sendSystemMessage(Component.literal("Select a class and shield first.").withStyle(ChatFormatting.RED));
			return 0;
		}
		ShieldManager.applyCondition(player, condition);
		HonorShieldsPackets.syncPlayer(player);
		player.sendSystemMessage(Component.literal("Shield condition set to " + condition.displayName() + ".").withStyle(condition.formatting()));
		return 1;
	}

	private TestCommand() {}
}
