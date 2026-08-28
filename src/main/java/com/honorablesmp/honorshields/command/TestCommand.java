package com.honorablesmp.honorshields.command;

import com.honorablesmp.honorshields.classsystem.ClassManager;
import com.honorablesmp.honorshields.classsystem.ClassType;
import com.honorablesmp.honorshields.data.HonorPlayerData;
import com.honorablesmp.honorshields.network.HonorShieldsPackets;
import com.honorablesmp.honorshields.shield.ShieldCondition;
import com.honorablesmp.honorshields.shield.ShieldAbilityHandler;
import com.honorablesmp.honorshields.shield.ShieldManager;
import com.honorablesmp.honorshields.shield.ShieldResourceManager;
import com.honorablesmp.honorshields.shield.ShieldType;
import com.honorablesmp.honorshields.shield.ShatteredShieldItem;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
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
			.then(Commands.literal("tab1")
				.executes(context -> tab1Status(context.getSource().getPlayerOrException()))
				.then(Commands.literal("status").executes(context -> tab1Status(context.getSource().getPlayerOrException())))
				.then(Commands.literal("setup").then(Commands.argument("shield", StringArgumentType.word())
					.suggests((context, builder) -> SharedSuggestionProvider.suggest(Arrays.stream(ShieldType.values()).map(ShieldType::id), builder))
					.executes(context -> tab1Setup(context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "shield")))))
				.then(Commands.literal("resource")
					.then(Commands.argument("verdancy", IntegerArgumentType.integer(0, 100))
						.then(Commands.argument("overflow", IntegerArgumentType.integer(0, 100))
							.executes(context -> tab1Resources(context.getSource().getPlayerOrException(),
								IntegerArgumentType.getInteger(context, "verdancy"), IntegerArgumentType.getInteger(context, "overflow"))))))
				.then(Commands.literal("activate").then(Commands.argument("slot", IntegerArgumentType.integer(1, 3))
					.executes(context -> tab1Activate(context.getSource().getPlayerOrException(),
						IntegerArgumentType.getInteger(context, "slot"))))))
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

	/**
	 * A compact, operator-only Tab 1 harness. It uses the ordinary class/shield,
	 * condition, resource sync and ability entry points; only the explicit
	 * resource setter is a debug convenience.
	 */
	private static int tab1Setup(ServerPlayer player, String id) {
		ShieldType shield = ShieldType.byId(id);
		if (shield == null) return 0;
		ClassManager.assignClass(player, ClassType.MERCHANT, true);
		if (!ClassManager.assignShield(player, shield)) return 0;
		ShieldManager.applyCondition(player, ShieldCondition.EXALTED);
		ShieldResourceManager.conditionChanged(player);
		player.sendSystemMessage(Component.literal("TAB1 setup: " + shield.displayName()
			+ " Shield (Exalted). Use /test tab1 status, resource, or activate.")
			.withStyle(ChatFormatting.LIGHT_PURPLE));
		return 1;
	}

	private static int tab1Resources(ServerPlayer player, int verdancy, int overflow) {
		HonorPlayerData data = (HonorPlayerData) player;
		if (data.honorshields$getShieldType() != ShieldType.PLOW) {
			player.sendSystemMessage(Component.literal("Use /test tab1 setup plow first.").withStyle(ChatFormatting.RED));
			return 0;
		}
		data.honorshields$setVerdancy(verdancy);
		data.honorshields$setVerdancyOverflow(data.honorshields$getShieldCondition() == ShieldCondition.EXALTED ? overflow : 0);
		ShieldResourceManager.sync(player, true);
		player.sendSystemMessage(Component.literal("TAB1 Plow resources: Verdancy " + verdancy + "/100, Overflow "
			+ data.honorshields$getVerdancyOverflow() + "/100.").withStyle(ChatFormatting.GREEN));
		return 1;
	}

	private static int tab1Activate(ServerPlayer player, int slot) {
		ShieldType shield = ((HonorPlayerData) player).honorshields$getShieldType();
		if (shield == null) {
			player.sendSystemMessage(Component.literal("Use /test tab1 setup <shield> first.").withStyle(ChatFormatting.RED));
			return 0;
		}
		switch (slot) {
			case 1 -> ShieldAbilityHandler.activateAbilityOne(player, shield);
			case 2 -> ShieldAbilityHandler.activateAbilityTwo(player, shield, null);
			case 3 -> ShieldAbilityHandler.activateUltimate(player, shield);
			default -> { return 0; }
		}
		player.sendSystemMessage(Component.literal("TAB1 invoked the real " + shield.displayName() + " slot " + slot
			+ " entry point; target-dependent moves still require a valid target.").withStyle(ChatFormatting.AQUA));
		return 1;
	}

	private static int tab1Status(ServerPlayer player) {
		HonorPlayerData data = (HonorPlayerData) player;
		ShieldType shield = data.honorshields$getShieldType();
		player.sendSystemMessage(Component.literal("TAB1 state — Shield: "
			+ (shield == null ? "none" : shield.displayName()) + ", Condition: " + data.honorshields$getShieldCondition().displayName())
			.withStyle(ChatFormatting.GOLD));
		player.sendSystemMessage(Component.literal("Verdancy: " + data.honorshields$getVerdancy() + "/100 | Overflow: "
			+ data.honorshields$getVerdancyOverflow() + "/100 | Static: " + data.honorshields$getThunderCharge()
			+ " | Wind: " + data.honorshields$getTempestCharge())
			.withStyle(ChatFormatting.AQUA));
		player.sendSystemMessage(Component.literal("Use /honorshields buildinfo to confirm this server's JAR.")
			.withStyle(ChatFormatting.GRAY));
		return 1;
	}

	private TestCommand() {}
}
