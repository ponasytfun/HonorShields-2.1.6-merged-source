package com.honorablesmp.honorshields.client.gui;

import com.honorablesmp.honorshields.client.AbilityHud;
import com.honorablesmp.honorshields.client.HONORABLESMPClient;
import com.honorablesmp.honorshields.client.KeybindHandler;
import com.honorablesmp.honorshields.config.HonorShieldsConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class HudLayoutScreen extends Screen {
	private enum Panel { CLASS, ABILITY }

	private Panel dragging;
	private double dragOffsetX;
	private double dragOffsetY;

	public HudLayoutScreen() {
		super(Component.literal("Move HonorShields HUD"));
	}

	@Override
	protected void init() {
		clampPanels();
		int buttonY = 5;
		addRenderableWidget(Button.builder(Component.literal("Reset"), ignored -> reset())
			.bounds(Math.max(4, width - 136), buttonY, 64, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Done"), ignored -> onClose())
			.bounds(Math.max(72, width - 68), buttonY, 64, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		graphics.fill(0, 0, width, height, 0x26000000);
		drawPanel(graphics, Panel.CLASS, "CLASS HUD — DRAG", 0xFFFFC857);
		drawPanel(graphics, Panel.ABILITY, "ABILITY HUD — DRAG", 0xFF8BE9FD);
		graphics.fill(0, Math.max(0, height - 17), width, height, 0xD9100E0D);
		graphics.centeredText(font,
			Component.literal("Drag either outlined HUD • " + KeybindHandler.hudLayoutKeyName() + " or Esc saves and closes")
				.withStyle(ChatFormatting.GOLD), width / 2, Math.max(4, height - 14), 0xFFFFD166);
		super.extractRenderState(graphics, mouseX, mouseY, delta);
	}

	private void drawPanel(GuiGraphicsExtractor graphics, Panel panel, String label, int color) {
		int x = panelX(panel);
		int y = panelY(panel);
		int w = panelWidth(panel);
		int h = panelHeight(panel);
		graphics.fill(x, y, x + w, y + h, dragging == panel ? 0x4DFFFFFF : 0x26000000 | (color & 0x00FFFFFF));
		graphics.outline(x, y, w, h, color);
		if (w > 4 && h > 4) graphics.outline(x + 2, y + 2, w - 4, h - 4, 0x99000000 | (color & 0x00FFFFFF));
		graphics.text(font, label, x + 6, y + 6, color, true);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0) {
			Panel hit = hit(event.x(), event.y());
			if (hit != null) {
				dragging = hit;
				dragOffsetX = event.x() - panelX(hit);
				dragOffsetY = event.y() - panelY(hit);
				setDragging(true);
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (dragging == null) return super.mouseDragged(event, dragX, dragY);
		var config = HonorShieldsConfig.get();
		int nextX = clamp((int) Math.round(event.x() - dragOffsetX), 0, Math.max(0, width - panelWidth(dragging)));
		int nextY = clamp((int) Math.round(event.y() - dragOffsetY), 0, Math.max(0, height - panelHeight(dragging)));
		if (dragging == Panel.CLASS) {
			config.leaderboardX = nextX;
			config.leaderboardY = nextY;
		} else {
			config.abilityHudX = nextX;
			config.abilityHudY = nextY;
		}
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (dragging != null && event.button() == 0) {
			dragging = null;
			setDragging(false);
			HonorShieldsConfig.save();
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (KeybindHandler.matchesHudLayoutKey(event)) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		clampPanels();
		HonorShieldsConfig.save();
		super.onClose();
	}

	@Override public boolean isPauseScreen() { return false; }

	private void reset() {
		HonorShieldsConfig.get().resetHudLayout();
		clampPanels();
		HonorShieldsConfig.save();
	}

	private Panel hit(double mouseX, double mouseY) {
		if (contains(Panel.ABILITY, mouseX, mouseY)) return Panel.ABILITY;
		if (contains(Panel.CLASS, mouseX, mouseY)) return Panel.CLASS;
		return null;
	}

	private boolean contains(Panel panel, double mouseX, double mouseY) {
		int x = panelX(panel), y = panelY(panel);
		return mouseX >= x && mouseX < x + panelWidth(panel) && mouseY >= y && mouseY < y + panelHeight(panel);
	}

	private void clampPanels() {
		var config = HonorShieldsConfig.get();
		config.leaderboardX = clamp(config.leaderboardX, 0, Math.max(0, width - panelWidth(Panel.CLASS)));
		config.leaderboardY = clamp(config.leaderboardY, 0, Math.max(0, height - panelHeight(Panel.CLASS)));
		config.abilityHudX = clamp(config.abilityHudX, 0, Math.max(0, width - panelWidth(Panel.ABILITY)));
		config.abilityHudY = clamp(config.abilityHudY, 0, Math.max(0, height - panelHeight(Panel.ABILITY)));
	}

	private int panelX(Panel panel) {
		var config = HonorShieldsConfig.get();
		return panel == Panel.CLASS ? config.leaderboardX : config.abilityHudX;
	}

	private int panelY(Panel panel) {
		var config = HonorShieldsConfig.get();
		return panel == Panel.CLASS ? config.leaderboardY : config.abilityHudY;
	}

	private int panelWidth(Panel panel) {
		var config = HonorShieldsConfig.get();
		float scale = panel == Panel.CLASS
			? Math.max(0.5F, Math.min(2.0F, config.leaderboardScale * HONORABLESMPClient.hudScale))
			: Math.max(0.5F, Math.min(2.0F, config.abilityHudScale));
		return Math.max(1, Math.round((panel == Panel.CLASS ? LeaderboardHud.WIDTH : AbilityHud.WIDTH) * scale));
	}

	private int panelHeight(Panel panel) {
		var config = HonorShieldsConfig.get();
		float scale = panel == Panel.CLASS
			? Math.max(0.5F, Math.min(2.0F, config.leaderboardScale * HONORABLESMPClient.hudScale))
			: Math.max(0.5F, Math.min(2.0F, config.abilityHudScale));
		return Math.max(1, Math.round((panel == Panel.CLASS ? LeaderboardHud.HEIGHT : AbilityHud.HEIGHT) * scale));
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}
