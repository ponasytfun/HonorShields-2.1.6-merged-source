package com.honorablesmp.honorshields;

import com.honorablesmp.honorshields.classsystem.ClassManager;
import com.honorablesmp.honorshields.classsystem.PassiveTriggerHandler;
import com.honorablesmp.honorshields.command.ClassCommand;
import com.honorablesmp.honorshields.command.LeaderboardCommand;
import com.honorablesmp.honorshields.command.TestCommand;
import com.honorablesmp.honorshields.command.TrustCommand;
import com.honorablesmp.honorshields.command.WithdrawCommand;
import com.honorablesmp.honorshields.config.HonorShieldsConfig;
import com.honorablesmp.honorshields.data.HonorPlayerData;
import com.honorablesmp.honorshields.data.HonorWorldState;
import com.honorablesmp.honorshields.network.HonorShieldsPackets;
import com.honorablesmp.honorshields.shield.HonorShieldsItemGroup;
import com.honorablesmp.honorshields.shield.ConditionScrollItem;
import com.honorablesmp.honorshields.shield.ReinforcedDeepslateBlock;
import com.honorablesmp.honorshields.shield.ShieldRitualHandler;
import com.honorablesmp.honorshields.shield.ShieldAbilityHandler;
import com.honorablesmp.honorshields.shield.ShieldManager;
import com.honorablesmp.honorshields.shield.ShieldType;
import com.honorablesmp.honorshields.shield.ShatteredShieldItem;
import com.honorablesmp.honorshields.shield.RerollTokenItem;
import com.honorablesmp.honorshields.shield.TempestDoubleJumpHandler;
import com.honorablesmp.honorshields.shield.CrystalBulwarkBlock;
import com.honorablesmp.honorshields.shield.SeasonTwoGameplay;
import com.honorablesmp.honorshields.shield.ShieldResourceManager;
import com.honorablesmp.honorshields.shield.VagabondHandler;
import com.honorablesmp.honorshields.shield.MonsoonHandler;
import com.honorablesmp.honorshields.shield.PlowHandler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class HonorShieldsMod implements ModInitializer {
	public static final String MOD_ID = "honorable-smp";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		HonorShieldsConfig.load();
		ShieldType.registerAll();
		ShatteredShieldItem.register();
		RerollTokenItem.register();
		ConditionScrollItem.register();
		ReinforcedDeepslateBlock.register();
		CrystalBulwarkBlock.register();
		HonorShieldsItemGroup.register();
		HonorShieldsPackets.registerCommon();
		ShieldAbilityHandler.registerEvents();
		PassiveTriggerHandler.registerEvents();
		ShieldResourceManager.registerEvents();
		VagabondHandler.registerEvents();
		PlowHandler.registerEvents();
		CommandRegistrationCallback.EVENT.register((dispatcher, context, environment) -> {
			OathCommand.register(dispatcher);
			ClassCommand.register(dispatcher);
			LeaderboardCommand.register(dispatcher);
			TestCommand.register(dispatcher);
			TrustCommand.register(dispatcher);
			WithdrawCommand.register(dispatcher);
		});
		UseBlockCallback.EVENT.register(ShieldRitualHandler::onUse);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> {
			HonorWorldState state = HonorWorldState.get(server);
			HonorPlayerData data = (HonorPlayerData) handler.player;
			if (!state.isActivated() || data.honorshields$getClassType() != null && data.honorshields$getOathGeneration() != state.generation()) {
				ClassManager.cancelOath(handler.player);
			} else {
				ClassManager.onJoin(handler.player);
				if (data.honorshields$getClassType() == null) ClassManager.requestSelection(handler.player);
			}
		}));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> server.execute(() -> {
			ShieldAbilityHandler.resetPlayer(handler.player);
			PassiveTriggerHandler.resetPlayer(handler.player);
			ShieldResourceManager.resetPlayer(handler.player);
			VagabondHandler.resetPlayer(handler.player);
			MonsoonHandler.resetPlayer(handler.player);
			PlowHandler.resetPlayer(handler.player);
		}));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			PassiveTriggerHandler.tick(server);
			ShieldAbilityHandler.tick(server);
			TempestDoubleJumpHandler.tick(server);
			ShieldResourceManager.tick(server);
			VagabondHandler.tick(server);
			MonsoonHandler.tick(server);
			PlowHandler.tick(server);
			SeasonTwoGameplay.tick(server);
			long time = server.overworld().getGameTime();
			for (var player : server.getPlayerList().getPlayers()) ShieldManager.tick(player);
			if (HonorWorldState.get(server).isActivated() && time % 18000L == 0L) {
				for (var player : server.getPlayerList().getPlayers()) if (((HonorPlayerData) player).honorshields$getClassType() == null) ClassManager.requestSelection(player);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			ShieldAbilityHandler.restoreTemporaryBlocks();
			SeasonTwoGameplay.restoreAll(server);
			PlowHandler.restoreAll();
		});
		LOGGER.info("HonorShields initialized for Minecraft 26.2");
	}

	public static Identifier id(String path) { return Identifier.fromNamespaceAndPath(MOD_ID, path); }
}
