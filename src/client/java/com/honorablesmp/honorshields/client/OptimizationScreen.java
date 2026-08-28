package com.honorablesmp.honorshields.client;

import com.honorablesmp.honorshields.config.HonorShieldsConfig;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.Function;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class OptimizationScreen extends Screen {
	private final Screen parent;

	public OptimizationScreen(Screen parent) {
		super(Component.literal("HonorShields Optimization"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		var config = HonorShieldsConfig.get();
		int left = width / 2 - 230, right = width / 2 + 10, y = height / 2 - 145;
		addRenderableWidget(new ConfigSlider(left, y, 220, "Render scale", (config.shieldRenderScale - 0.5) / 1.5,
			value -> config.shieldRenderScale = (float) (0.5 + value * 1.5), value -> "%.2fx".formatted(0.5 + value * 1.5)));
		addRenderableWidget(new ConfigSlider(right, y, 220, "Shield size", (config.shieldSize - 0.6) / 1.0,
			value -> config.shieldSize = (float) (0.6 + value), value -> "%.2fx".formatted(0.6 + value)));
		y += 28;
		addRenderableWidget(new ConfigSlider(left, y, 220, "Particle density", config.particleDensity / 3.0,
			value -> config.particleDensity = (int) Math.round(value * 3.0), value -> Integer.toString((int) Math.round(value * 3.0))));
		addRenderableWidget(new ConfigSlider(right, y, 220, "Ability HUD scale", (config.abilityHudScale - 0.5) / 1.5,
			value -> config.abilityHudScale = (float) (0.5 + value * 1.5), value -> "%.2fx".formatted(0.5 + value * 1.5)));
		y += 28;
		addRenderableWidget(new ConfigSlider(left, y, 220, "Class HUD X", config.leaderboardX / (double) Math.max(1, width - 180),
			value -> config.leaderboardX = (int) Math.round(value * Math.max(1, width - 180)), value -> Integer.toString((int) Math.round(value * Math.max(1, width - 180)))));
		addRenderableWidget(new ConfigSlider(right, y, 220, "Class HUD Y", config.leaderboardY / (double) Math.max(1, height - 78),
			value -> config.leaderboardY = (int) Math.round(value * Math.max(1, height - 78)), value -> Integer.toString((int) Math.round(value * Math.max(1, height - 78)))));
		y += 28;
		addRenderableWidget(new ConfigSlider(left, y, 220, "Ability HUD X", config.abilityHudX / (double) Math.max(1, width - 240),
			value -> config.abilityHudX = (int) Math.round(value * Math.max(1, width - 240)), value -> Integer.toString((int) Math.round(value * Math.max(1, width - 240)))));
		addRenderableWidget(new ConfigSlider(right, y, 220, "Ability HUD Y", config.abilityHudY / (double) Math.max(1, height - 120),
			value -> config.abilityHudY = (int) Math.round(value * Math.max(1, height - 120)), value -> Integer.toString((int) Math.round(value * Math.max(1, height - 120)))));
		y += 34;
		addToggle(left, y, 220, "First-person shield", () -> config.showShieldInFirstPerson, value -> config.showShieldInFirstPerson = value);
		addToggle(right, y, 220, "Ability HUD", () -> config.showAbilityHud, value -> config.showAbilityHud = value);
		y += 24;
		addToggle(left, y, 220, "Class HUD", () -> config.showLeaderboard, value -> config.showLeaderboard = value);
		addToggle(right, y, 220, "Extra passive particles", () -> config.showPassiveTriggers, value -> config.showPassiveTriggers = value);
		y += 24;
		addToggle(left, y, 220, "Ability effects", () -> config.enableAbilityEffects, value -> config.enableAbilityEffects = value);
		addToggle(right, y, 220, "Ability sounds", () -> config.playAbilitySounds, value -> config.playAbilitySounds = value);
		y += 24;
		addToggle(left, y, 220, "Ability animations", () -> config.enableAbilityAnimations, value -> config.enableAbilityAnimations = value);
		addToggle(right, y, 220, "Cinematic camera", () -> config.enableCinematicCamera, value -> config.enableCinematicCamera = value);
		y += 24;
		addToggle(left, y, 220, "Passive sounds", () -> config.playPassiveSounds, value -> config.playPassiveSounds = value);
		addToggle(right, y, 220, "Reduced flashes", () -> config.reducedFlashes, value -> config.reducedFlashes = value);
		y += 24;
		addToggle(left, y, 220, "GUI sounds", () -> config.playGuiSounds, value -> config.playGuiSounds = value);
		y += 32;
		addRenderableWidget(Button.builder(Component.literal("Done").withStyle(ChatFormatting.GOLD), button -> onClose()).bounds(width / 2 - 105, y, 210, 20).build());
	}

	private void addToggle(int x, int y, int width, String label, BooleanSupplier getter, Consumer<Boolean> setter) {
		addRenderableWidget(Button.builder(toggleLabel(label, getter.getAsBoolean()), button -> {
			boolean value = !getter.getAsBoolean();
			setter.accept(value);
			button.setMessage(toggleLabel(label, value));
			HonorShieldsConfig.save();
		}).bounds(x, y, width, 20).build());
	}

	private static Component toggleLabel(String label, boolean enabled) {
		return Component.literal(label + ": " + (enabled ? "ON" : "OFF")).withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED);
	}

	@Override
	public void onClose() {
		HonorShieldsConfig.save();
		minecraft.gui.setScreen(parent);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		graphics.fill(0, 0, width, height, 0xD90B0908);
		int x = width / 2 - 250, y = height / 2 - 180;
		graphics.fill(x, y, x + 500, y + 360, 0xEE24160F);
		graphics.outline(x, y, 500, 360, 0xFFFFC857);
		graphics.centeredText(font, Component.literal("HONORSHIELDS OPTIMIZATION").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), width / 2, y + 15, 0xFFFFD166);
		graphics.centeredText(font, "Changes save immediately to config/honorshields.json", width / 2, y + 31, 0xFFB8A58C);
		super.extractRenderState(graphics, mouseX, mouseY, delta);
	}

	@Override public boolean isPauseScreen() { return false; }

	private static final class ConfigSlider extends AbstractSliderButton {
		private final String label;
		private final DoubleConsumer consumer;
		private final Function<Double, String> formatter;

		private ConfigSlider(int x, int y, int width, String label, double value, DoubleConsumer consumer, Function<Double, String> formatter) {
			super(x, y, width, 20, Component.empty(), Math.max(0.0, Math.min(1.0, value)));
			this.label = label;
			this.consumer = consumer;
			this.formatter = formatter;
			updateMessage();
		}

		@Override protected void updateMessage() { setMessage(Component.literal(label + ": " + formatter.apply(value))); }
		@Override protected void applyValue() { consumer.accept(value); HonorShieldsConfig.save(); }
	}
}
