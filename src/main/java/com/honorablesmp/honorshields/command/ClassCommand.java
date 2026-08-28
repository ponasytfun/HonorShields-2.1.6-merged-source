package com.honorablesmp.honorshields.command;

import com.honorablesmp.honorshields.classsystem.ClassManager;
import com.honorablesmp.honorshields.classsystem.ClassType;
import com.honorablesmp.honorshields.data.HonorPlayerData;
import com.honorablesmp.honorshields.shield.ShieldManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class ClassCommand {
	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("class")
			.then(Commands.literal("select").executes(context -> {
				ClassManager.requestSelection(context.getSource().getPlayerOrException());
				return 1;
			}))
			.then(Commands.literal("info").executes(context -> info(context.getSource().getPlayerOrException())))
			.then(Commands.literal("abilities").executes(context -> abilities(context.getSource().getPlayerOrException())))
			.then(Commands.literal("shield")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("unlock").then(Commands.argument("player", EntityArgument.player()).executes(context -> {
					ServerPlayer player = EntityArgument.getPlayer(context, "player");
					ShieldManager.setAdminUnlocked(player, true);
					player.sendSystemMessage(Component.literal("An administrator unlocked your oath shield.").withStyle(ChatFormatting.GOLD));
					return 1;
				})))
				.then(Commands.literal("lock").then(Commands.argument("player", EntityArgument.player()).executes(context -> {
					ServerPlayer player = EntityArgument.getPlayer(context, "player");
					ShieldManager.setAdminUnlocked(player, false);
					ShieldManager.equipAssigned(player);
					return 1;
				}))))
			.then(Commands.literal("force")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.argument("player", EntityArgument.player())
					.then(Commands.argument("class", StringArgumentType.word())
						.suggests((context, builder) -> SharedSuggestionProvider.suggest(java.util.Arrays.stream(ClassType.values()).map(ClassType::id), builder))
						.executes(context -> force(EntityArgument.getPlayer(context, "player"), StringArgumentType.getString(context, "class")))))));
	}

	private static int abilities(ServerPlayer player) {
		var data = (HonorPlayerData) player;
		var shield = data.honorshields$getShieldType();
		if (shield == null) {
			player.sendSystemMessage(Component.literal("You do not have an oath shield.").withStyle(ChatFormatting.RED));
			return 0;
		}
		player.sendSystemMessage(Component.literal(shield.displayName() + " Shield — " + shield.category()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		player.sendSystemMessage(Component.literal("PASSIVE — " + shield.passive()).withStyle(ChatFormatting.GREEN));
		player.sendSystemMessage(Component.literal(shield.passiveHelp()).withStyle(ChatFormatting.GRAY));
		if (!shield.exaltedPassive().isEmpty()) {
			player.sendSystemMessage(Component.literal("EXALTED PASSIVE — " + shield.exaltedPassive()).withStyle(ChatFormatting.GOLD));
			player.sendSystemMessage(Component.literal(shield.exaltedPassiveHelp()).withStyle(ChatFormatting.GRAY));
		}
		player.sendSystemMessage(Component.literal("R — " + shield.abilityOne() + " [" + shield.abilityOneCooldown() + "s]").withStyle(ChatFormatting.AQUA));
		player.sendSystemMessage(Component.literal(shield.abilityOneHelp()).withStyle(ChatFormatting.GRAY));
		player.sendSystemMessage(Component.literal("F — " + shield.abilityTwo() + " [" + shield.abilityTwoCooldown() + "s]").withStyle(ChatFormatting.AQUA));
		player.sendSystemMessage(Component.literal(shield.abilityTwoHelp()).withStyle(ChatFormatting.GRAY));
		player.sendSystemMessage(Component.literal("G — " + shield.ultimate() + " [" + shield.ultimateCooldown() + "s]").withStyle(ChatFormatting.LIGHT_PURPLE));
		player.sendSystemMessage(Component.literal(shield.ultimateHelp()).withStyle(ChatFormatting.GRAY));
		return 1;
	}

	private static int force(ServerPlayer player, String id) {
		ClassType type = ClassType.byId(id);
		if (type == null) return 0;
		ClassManager.assignClass(player, type, true);
		return 1;
	}

	private static int info(ServerPlayer player) {
		ClassType type = ((HonorPlayerData) player).honorshields$getClassType();
		if (type == null) {
			player.sendSystemMessage(Component.literal("You have not selected a class.").withStyle(ChatFormatting.RED));
			return 0;
		}
		player.sendSystemMessage(Component.literal(type.displayName()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		player.sendSystemMessage(Component.literal(type.description()).withStyle(ChatFormatting.GRAY));
		for (ClassType.Trait passive : type.passiveTraits()) {
			player.sendSystemMessage(Component.literal("+ " + passive.name()).withStyle(ChatFormatting.GREEN));
			player.sendSystemMessage(Component.literal("  " + passive.description()).withStyle(ChatFormatting.GRAY));
		}
		for (ClassType.Trait debuff : type.debuffTraits()) {
			player.sendSystemMessage(Component.literal("- " + debuff.name()).withStyle(ChatFormatting.RED));
			player.sendSystemMessage(Component.literal("  " + debuff.description()).withStyle(ChatFormatting.GRAY));
		}
		return 1;
	}

	private ClassCommand() {}
}
