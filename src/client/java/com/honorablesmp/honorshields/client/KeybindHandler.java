package com.honorablesmp.honorshields.client;

import com.honorablesmp.honorshields.HonorShieldsMod;
import com.honorablesmp.honorshields.client.gui.ClassSelectionScreen;
import com.honorablesmp.honorshields.client.gui.HonorCompendiumScreen;
import com.honorablesmp.honorshields.client.gui.HudLayoutScreen;
import com.honorablesmp.honorshields.config.HonorShieldsConfig;
import com.honorablesmp.honorshields.network.HonorShieldsPackets;
import com.honorablesmp.honorshields.shield.ShieldCondition;
import com.honorablesmp.honorshields.shield.ShieldType;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;

public final class KeybindHandler {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(HonorShieldsMod.id("controls"));
	private static KeyMapping abilityOne, abilityTwo, ultimate, classSelection, leaderboard, optimization, shieldHud, hudLayout;
	private static boolean jumpWasDown;
	private static boolean jumpCycleArmed;
	private static boolean airborne;
	private static boolean airborneJumpReleased;
	private static int extraJumpsRequested;
	private static int jumpInputSequence;
	private static LocalPlayer trackedPlayer;
	private static ClientLevel trackedLevel;

	public static void register() {
		abilityOne = bind("ability_one", InputConstants.KEY_R, 0);
		abilityTwo = bind("ability_two", InputConstants.KEY_F, 1);
		ultimate = bind("ultimate", InputConstants.KEY_G, 2);
		classSelection = bind("class_selection", InputConstants.KEY_H, 3);
		leaderboard = bind("leaderboard", InputConstants.KEY_L, 4);
		optimization = bind("optimization", InputConstants.KEY_F8, 5);
		shieldHud = bind("shield_hud", InputConstants.KEY_K, 6);
		hudLayout = bind("hud_layout", InputConstants.KEY_P, 7);
		ClientTickEvents.END_CLIENT_TICK.register(KeybindHandler::tick);
	}

	private static KeyMapping bind(String name, int key, int order) {
		return KeyMappingHelper.registerKeyMapping(new KeyMapping("key.honorable-smp." + name, InputConstants.Type.KEYSYM, key, CATEGORY, order));
	}

	private static void tick(Minecraft client) {
		while (abilityOne.consumeClick()) sendAbility(1);
		while (abilityTwo.consumeClick()) sendAbility(2);
		while (ultimate.consumeClick()) sendAbility(3);
		while (classSelection.consumeClick()) {
			if (client.gui.screen() instanceof HonorCompendiumScreen) {
				client.gui.setScreen(null);
			} else if (HONORABLESMPClient.classId.isEmpty()) {
				ClientPlayNetworking.send(new HonorShieldsPackets.ClientActionPayload("request_class"));
				client.gui.setScreen(new ClassSelectionScreen());
			} else {
				client.gui.setScreen(new HonorCompendiumScreen());
			}
		}
		while (leaderboard.consumeClick()) ClientPlayNetworking.send(new HonorShieldsPackets.ClientActionPayload("toggle_leaderboard"));
		while (optimization.consumeClick()) client.gui.setScreen(new OptimizationScreen(client.gui.screen()));
		while (shieldHud.consumeClick()) {
			HonorShieldsConfig.get().showAbilityHud = !HonorShieldsConfig.get().showAbilityHud;
			HonorShieldsConfig.save();
		}
		while (hudLayout.consumeClick()) {
			if (client.gui.screen() instanceof HudLayoutScreen) client.gui.setScreen(null);
			else client.gui.setScreen(new HudLayoutScreen());
		}
		tickDoubleJump(client);
	}

	private static void tickDoubleJump(Minecraft client) {
		boolean down = client.options.keyJump.isDown();
		LocalPlayer player = client.player;
		ClientLevel level = client.level;
		if (player == null || level == null) {
			trackedPlayer = null;
			trackedLevel = null;
			resetJumpCycle(down);
			return;
		}
		if (player != trackedPlayer || level != trackedLevel) {
			trackedPlayer = player;
			trackedLevel = level;
			resetJumpCycle(down);
			return;
		}

		if (!validDoubleJumpContext(player)) {
			resetJumpCycle(down);
			return;
		}

		if (player.onGround()) {
			jumpCycleArmed = true;
			airborne = false;
			airborneJumpReleased = false;
			extraJumpsRequested = 0;
			jumpWasDown = down;
			return;
		}
		if (!jumpCycleArmed) {
			jumpWasDown = down;
			return;
		}

		// Never treat the ground-jump press as the air jump. A full release
		// after leaving the ground is required before a second press can fire.
		if (!airborne) {
			airborne = true;
			airborneJumpReleased = !down;
			if (airborneJumpReleased) sendDoubleJumpInput(false);
			jumpWasDown = down;
			return;
		}
		if (!down && jumpWasDown) {
			airborneJumpReleased = true;
			sendDoubleJumpInput(false);
		}
		int maximumExtraJumps = ShieldCondition.byId(HONORABLESMPClient.conditionId) == ShieldCondition.EXALTED ? 2 : 1;
		if (down && !jumpWasDown && airborneJumpReleased && extraJumpsRequested < maximumExtraJumps) {
			// The client detects only the jump edge. The server owns validation,
			// velocity, and the Exalted third-jump allowance.
			extraJumpsRequested++;
			airborneJumpReleased = false;
			sendDoubleJumpInput(true);
		}
		jumpWasDown = down;
	}

	private static void sendDoubleJumpInput(boolean pressed) {
		jumpInputSequence++;
		if (ClientPlayNetworking.canSend(HonorShieldsPackets.DoubleJumpPayload.TYPE)) {
			ClientPlayNetworking.send(new HonorShieldsPackets.DoubleJumpPayload(pressed, jumpInputSequence));
		}
	}

	private static boolean validDoubleJumpContext(LocalPlayer player) {
		return HONORABLESMPClient.shieldId.equals("tempest")
			&& ShieldCondition.byId(HONORABLESMPClient.conditionId).usable()
			&& ShieldType.fromStack(player.getOffhandItem()) == ShieldType.TEMPEST
			&& player.isAlive()
			&& !player.isSpectator()
			&& !player.isPassenger()
			&& !player.isSwimming()
			&& !player.isFallFlying()
			&& !player.getAbilities().flying;
	}

	private static void resetJumpCycle(boolean down) {
		jumpWasDown = down;
		jumpCycleArmed = false;
		airborne = false;
		airborneJumpReleased = false;
		extraJumpsRequested = 0;
	}

	private static void sendAbility(int slot) {
		Minecraft client = Minecraft.getInstance();
		ShieldType assigned = ShieldType.byId(HONORABLESMPClient.shieldId);
		if (client.player == null || assigned == null || ShieldType.fromStack(client.player.getOffhandItem()) != assigned) return;
		if (slot == 3 && ShieldCondition.byId(HONORABLESMPClient.conditionId) != ShieldCondition.EXALTED) {
			client.player.sendOverlayMessage(net.minecraft.network.chat.Component.literal("Your ultimate requires an Exalted shield."));
			return;
		}
		if (ClientPlayNetworking.canSend(HonorShieldsPackets.ActivateAbilityPayload.TYPE)) ClientPlayNetworking.send(new HonorShieldsPackets.ActivateAbilityPayload(slot));
	}

	public static String abilityKeyName(int slot) {
		KeyMapping mapping = switch (slot) {
			case 1 -> abilityOne;
			case 2 -> abilityTwo;
			case 3 -> ultimate;
			default -> null;
		};
		return mapping == null ? "?" : mapping.getTranslatedKeyMessage().getString();
	}

	public static String menuKeyName() {
		return classSelection == null ? "H" : classSelection.getTranslatedKeyMessage().getString();
	}

	public static String hudKeyName() {
		return shieldHud == null ? "K" : shieldHud.getTranslatedKeyMessage().getString();
	}

	public static String hudLayoutKeyName() {
		return hudLayout == null ? "P" : hudLayout.getTranslatedKeyMessage().getString();
	}

	public static boolean matchesHudLayoutKey(KeyEvent event) {
		return hudLayout != null && hudLayout.matches(event);
	}

	private KeybindHandler() {}
}
