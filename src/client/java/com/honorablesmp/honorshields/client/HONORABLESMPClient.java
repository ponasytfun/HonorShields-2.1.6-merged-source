package com.honorablesmp.honorshields.client;

import com.honorablesmp.honorshields.HonorShieldsMod;
import com.honorablesmp.honorshields.classsystem.ClassType;
import com.honorablesmp.honorshields.client.gui.ClassSelectionScreen;
import com.honorablesmp.honorshields.client.gui.LeaderboardHud;
import com.honorablesmp.honorshields.client.gui.ShieldRevealAnimation;
import com.honorablesmp.honorshields.config.HonorShieldsConfig;
import com.honorablesmp.honorshields.data.HonorPlayerData;
import com.honorablesmp.honorshields.network.HonorShieldsPackets;
import com.honorablesmp.honorshields.shield.ShieldCondition;
import com.honorablesmp.honorshields.shield.ShieldType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;

public final class HONORABLESMPClient implements ClientModInitializer {
	public static String classId = "";
	public static String shieldId = "";
	public static String conditionId = "honored";
	public static boolean hudVisible = true;
	public static float hudScale = 1.0F;

	@Override
	public void onInitializeClient() {
		HonorShieldsConfig.load();
		HonorShieldsMod.LOGGER.info("[HonorShields] Season 2 Tab 1 client build loaded: {} ({})",
			HonorShieldsMod.VERSION, HonorShieldsMod.BUILD_ID);
		HonorShieldsMod.LOGGER.info("[HonorShields] Feature set: {}", HonorShieldsMod.FEATURE_SET);
		KeybindHandler.register();
		WorldVfxRenderer.register();
		ClientPlayNetworking.registerGlobalReceiver(HonorShieldsPackets.OpenClassScreenPayload.TYPE, (payload, context) ->
			context.client().execute(() -> context.client().gui.setScreen(new ClassSelectionScreen())));
		ClientPlayNetworking.registerGlobalReceiver(HonorShieldsPackets.PlayerStatePayload.TYPE, (payload, context) -> context.client().execute(() -> {
			classId = payload.classId();
			boolean changed = !shieldId.equals(payload.shieldId());
			shieldId = payload.shieldId();
			conditionId = payload.conditionId();
			hudVisible = payload.hudVisible();
			hudScale = payload.hudScale();
			if (context.client().player instanceof HonorPlayerData data) {
				data.honorshields$setClassType(ClassType.byId(classId));
				data.honorshields$setShieldType(ShieldType.byId(shieldId));
				data.honorshields$setShieldCondition(ShieldCondition.byId(conditionId));
			}
			if (changed) {
				AbilityHud.clear();
				AbilityVfxManager.clear();
			}
			if (shieldId.isEmpty()) {
				ShieldRevealAnimation.clear();
				AbilityVfxManager.clear();
				if (context.client().gui.screen() instanceof ClassSelectionScreen) context.client().gui.setScreen(null);
			}
		}));
		ClientPlayNetworking.registerGlobalReceiver(HonorShieldsPackets.RevealShieldPayload.TYPE, (payload, context) -> context.client().execute(() -> {
			ShieldType type = ShieldType.byId(payload.shieldId());
			ShieldCondition condition = ShieldCondition.byId(payload.conditionId());
			if (type != null) ShieldRevealAnimation.show(type, condition == null ? ShieldCondition.HONORED : condition);
		}));
		ClientPlayNetworking.registerGlobalReceiver(HonorShieldsPackets.PresentationEffectPayload.TYPE, (payload, context) ->
			context.client().execute(() -> { WorldVfxRenderer.handle(payload); AbilityVfxManager.handlePresentation(payload); }));
		ClientPlayNetworking.registerGlobalReceiver(HonorShieldsPackets.PassiveEffectPayload.TYPE, (payload, context) ->
			context.client().execute(() -> AbilityVfxManager.handlePassive(payload)));
		ClientPlayNetworking.registerGlobalReceiver(HonorShieldsPackets.AbilityEffectPayload.TYPE, (payload, context) ->
			context.client().execute(() -> { AbilityVfxManager.handle(payload); WorldVfxRenderer.handle(payload); }));
		ClientPlayNetworking.registerGlobalReceiver(HonorShieldsPackets.CooldownPayload.TYPE, (payload, context) ->
			context.client().execute(() -> AbilityHud.setCooldown(payload.slot(), payload.abilityName(), payload.seconds())));
		ClientPlayNetworking.registerGlobalReceiver(HonorShieldsPackets.ShieldResourcePayload.TYPE, (payload, context) ->
			context.client().execute(() -> AbilityHud.setResource(payload.kind(), payload.current(), payload.maximum(), payload.armed())));
		ClientTickEvents.END_CLIENT_TICK.register(AbilityVfxManager::tick);
		ClientTickEvents.END_CLIENT_TICK.register(WorldVfxRenderer::tick);
		// The cinematic edge-light is intentionally first so the functional HUD
		// and reveal cards always remain crisp above it.
		HudElementRegistry.addLast(HonorShieldsMod.id("ability_vfx"), AbilityVfxManager::renderOverlay);
		HudElementRegistry.addLast(HonorShieldsMod.id("leaderboard"), LeaderboardHud::render);
		HudElementRegistry.addLast(HonorShieldsMod.id("ability_hud"), AbilityHud::render);
		HudElementRegistry.addLast(HonorShieldsMod.id("shield_reveal"), ShieldRevealAnimation::render);
	}
}
