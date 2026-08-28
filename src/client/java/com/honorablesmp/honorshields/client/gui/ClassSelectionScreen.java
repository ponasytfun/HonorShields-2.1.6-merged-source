package com.honorablesmp.honorshields.client.gui;

import com.honorablesmp.honorshields.classsystem.ClassType;
import com.honorablesmp.honorshields.config.HonorShieldsConfig;
import com.honorablesmp.honorshields.network.HonorShieldsPackets;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;

public final class ClassSelectionScreen extends Screen {
	private int panelX;
	private int panelY;
	private int panelWidth;
	private int panelHeight;

	public ClassSelectionScreen() { super(Component.literal("Choose Your Oath")); }

	@Override
	protected void init() {
		panelWidth = Math.max(260, Math.min(560, width - 24));
		panelHeight = Math.max(206, Math.min(252, height - 24));
		panelX = (width - panelWidth) / 2;
		panelY = (height - panelHeight) / 2;
		int columns = panelWidth >= 460 ? 3 : 2;
		int rows = (ClassType.values().length + columns - 1) / columns;
		int gap = 8;
		int buttonWidth = (panelWidth - 24 - gap * (columns - 1)) / columns;
		int availableHeight = panelHeight - 97;
		int buttonHeight = Math.max(30, Math.min(44, (availableHeight - gap * (rows - 1)) / rows));
		int startX = panelX + 12;
		int startY = panelY + 54;
		ClassType[] classes = ClassType.values();
		for (int i = 0; i < classes.length; i++) {
			ClassType type = classes[i];
			int x = startX + (i % columns) * (buttonWidth + gap);
			int y = startY + (i / columns) * (buttonHeight + gap);
			Button button = Button.builder(Component.literal(type.displayName()).withColor(type.color()).withStyle(ChatFormatting.BOLD), ignored -> select(type))
				.bounds(x, y, buttonWidth, buttonHeight)
				.tooltip(Tooltip.create(tooltip(type)))
				.build();
			addRenderableWidget(button);
		}
	}

	private MutableComponent tooltip(ClassType type) {
		MutableComponent text = Component.literal(type.displayName()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
			.append(Component.literal("\n" + type.description()).withStyle(ChatFormatting.GRAY))
			.append(Component.literal("\nHealth: " + formatHearts(type.maxHealth()) + " hearts").withStyle(ChatFormatting.RED))
			.append(Component.literal("\nPassives: ").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD))
			.append(Component.literal(type.passives().stream().collect(java.util.stream.Collectors.joining(" • "))).withStyle(ChatFormatting.GREEN));
		if (!type.debuffs().isEmpty()) text
			.append(Component.literal("\nDrawbacks: ").withStyle(ChatFormatting.RED, ChatFormatting.BOLD))
			.append(Component.literal(type.debuffs().stream().collect(java.util.stream.Collectors.joining(" • "))).withStyle(ChatFormatting.RED));
		text.append(Component.literal("\nShield pool: ").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD))
			.append(Component.literal(type.shields().stream().map(shield -> shield.displayName()).collect(java.util.stream.Collectors.joining(" • "))).withStyle(ChatFormatting.AQUA))
			.append(Component.literal("\nFull details unlock in the H compendium.").withStyle(ChatFormatting.DARK_GRAY));
		return text;
	}

	private void select(ClassType type) {
		ClientPlayNetworking.send(new HonorShieldsPackets.SelectClassPayload(type.id()));
		if (HonorShieldsConfig.get().playGuiSounds && minecraft.player != null) minecraft.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.8F, 1.0F);
		onClose();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		graphics.fillGradient(0, 0, width, height, 0xF008090E, 0xF0120D0A);
		graphics.fill(panelX + 3, panelY + 4, panelX + panelWidth + 4, panelY + panelHeight + 5, 0x79000000);
		graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xF024160F);
		graphics.outline(panelX, panelY, panelWidth, panelHeight, 0xFFFFC857);
		graphics.fillGradient(panelX + 1, panelY + 1, panelX + panelWidth - 1, panelY + 45, 0xFF4A2D19, 0xFF24160F);
		graphics.centeredText(font, Component.literal("CHOOSE YOUR OATH").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), width / 2, panelY + 12, 0xFFFFD166);
		graphics.centeredText(font, "Choose a class; your shield is drawn from its pool.", width / 2, panelY + 29, 0xFFD7CEC2);
		graphics.fill(panelX + 10, panelY + panelHeight - 35, panelX + panelWidth - 10, panelY + panelHeight - 34, 0xFF71553B);
		graphics.centeredText(font, "Hover for a compact summary  •  Press H after choosing for the full guide", width / 2, panelY + panelHeight - 24, 0xFFB8A58C);
		graphics.centeredText(font, "Passives, drawbacks, and shield abilities stay available in-game.", width / 2, panelY + panelHeight - 12, 0xFF8E8174);
		super.extractRenderState(graphics, mouseX, mouseY, delta);
	}

	private static String formatHearts(float maxHealth) {
		float hearts = maxHealth / 2.0F;
		return hearts == Math.round(hearts) ? Integer.toString(Math.round(hearts)) : Float.toString(hearts);
	}

	@Override public boolean shouldCloseOnEsc() { return false; }
}
