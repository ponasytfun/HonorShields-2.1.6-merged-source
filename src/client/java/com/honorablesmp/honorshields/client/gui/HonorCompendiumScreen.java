package com.honorablesmp.honorshields.client.gui;

import com.honorablesmp.honorshields.classsystem.ClassType;
import com.honorablesmp.honorshields.client.HONORABLESMPClient;
import com.honorablesmp.honorshields.client.KeybindHandler;
import com.honorablesmp.honorshields.shield.ShieldCondition;
import com.honorablesmp.honorshields.shield.ShieldType;
import com.honorablesmp.honorshields.shield.AnglerLoot;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Data-driven reference opened with the class/menu key (H by default). */
public final class HonorCompendiumScreen extends Screen {
	private record RenderLine(FormattedCharSequence text, int color, int spacing) {}

	private boolean shieldsTab;
	private int selectedClass;
	private int selectedShield;
	private int scroll;
	private int maxScroll;
	private int panelX;
	private int panelY;
	private int panelWidth;
	private int panelHeight;
	private int sidebarWidth;
	private int detailX;
	private int detailY;
	private int detailWidth;
	private int detailHeight;
	private final List<RenderLine> detailLines = new ArrayList<>();

	public HonorCompendiumScreen() {
		super(Component.literal("HonorShields Compendium"));
		ClassType currentClass = ClassType.byId(HONORABLESMPClient.classId);
		ShieldType currentShield = ShieldType.byId(HONORABLESMPClient.shieldId);
		selectedClass = currentClass == null ? 0 : currentClass.ordinal();
		selectedShield = currentShield == null ? 0 : currentShield.ordinal();
	}

	@Override
	protected void init() {
		panelWidth = Math.max(300, Math.min(620, width - 20));
		panelHeight = Math.max(210, Math.min(350, height - 20));
		panelX = (width - panelWidth) / 2;
		panelY = (height - panelHeight) / 2;
		sidebarWidth = Math.min(174, Math.max(140, panelWidth / 3));
		detailX = panelX + sidebarWidth + 12;
		detailY = panelY + 58;
		detailWidth = panelWidth - sidebarWidth - 24;
		detailHeight = panelHeight - 92;
		rebuildMenu();
	}

	private void rebuildMenu() {
		clearWidgets();
		int tabY = panelY + 29;
		addRenderableWidget(Button.builder(tabLabel("CLASSES", !shieldsTab), button -> switchTab(false))
			.bounds(panelX + 10, tabY, 78, 20).build());
		addRenderableWidget(Button.builder(tabLabel("SHIELDS", shieldsTab), button -> switchTab(true))
			.bounds(panelX + 92, tabY, 78, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Done").withStyle(ChatFormatting.GOLD), button -> onClose())
			.bounds(panelX + panelWidth - 64, panelY + panelHeight - 25, 54, 18).build());

		int listX = panelX + 10;
		int listY = panelY + 58;
		if (shieldsTab) {
			ShieldType[] shields = ShieldType.values();
			int gap = 4;
			int buttonWidth = (sidebarWidth - 24 - gap) / 2;
			for (int i = 0; i < shields.length; i++) {
				final int index = i;
				int column = i / 7;
				int row = i % 7;
				addRenderableWidget(Button.builder(entryLabel(shields[i].displayName(), i == selectedShield, shields[i].color()), button -> selectShield(index))
					.bounds(listX + column * (buttonWidth + gap), listY + row * 18, buttonWidth, 16).build());
			}
		} else {
			ClassType[] classes = ClassType.values();
			for (int i = 0; i < classes.length; i++) {
				final int index = i;
				addRenderableWidget(Button.builder(entryLabel(classes[i].displayName(), i == selectedClass, classes[i].color()), button -> selectClass(index))
					.bounds(listX, listY + i * 22, sidebarWidth - 20, 20).build());
			}
		}
		rebuildDetails();
	}

	private void switchTab(boolean shieldTab) {
		if (shieldsTab == shieldTab) return;
		shieldsTab = shieldTab;
		scroll = 0;
		rebuildMenu();
	}

	private void selectClass(int index) {
		selectedClass = index;
		scroll = 0;
		rebuildMenu();
	}

	private void selectShield(int index) {
		selectedShield = index;
		scroll = 0;
		rebuildMenu();
	}

	private void rebuildDetails() {
		detailLines.clear();
		int wrapWidth = Math.max(80, detailWidth - 16);
		if (shieldsTab) buildShieldDetails(ShieldType.values()[selectedShield], wrapWidth);
		else buildClassDetails(ClassType.values()[selectedClass], wrapWidth);
		int totalHeight = detailLines.stream().mapToInt(line -> 9 + line.spacing()).sum();
		maxScroll = Math.max(0, totalHeight - detailHeight);
		scroll = Math.max(0, Math.min(scroll, maxScroll));
	}

	private void buildClassDetails(ClassType type, int wrapWidth) {
		addLine(Component.literal(type.displayName()).withStyle(ChatFormatting.BOLD), 0xFF000000 | type.color(), 2, wrapWidth);
		addLine(Component.literal(type.description()), 0xFFD7CEC2, 4, wrapWidth);
		addLine(Component.literal("MAX HEALTH  •  " + formatHearts(type.maxHealth()) + " HEARTS"), 0xFFFFD166, 6, wrapWidth);
		addLine(Component.literal("PASSIVES").withStyle(ChatFormatting.BOLD), 0xFF7CFF8B, 3, wrapWidth);
		for (ClassType.Trait trait : type.passiveTraits().stream().filter(trait -> !trait.name().contains("(Exalted)")).toList()) {
			addLine(Component.literal("+ " + trait.name()).withStyle(ChatFormatting.BOLD), 0xFF9CFFAA, 0, wrapWidth);
			addLine(Component.literal("  " + trait.description()), 0xFFCBC4BB, 3, wrapWidth);
		}
		List<ClassType.Trait> exaltedTraits = type.passiveTraits().stream().filter(trait -> trait.name().contains("(Exalted)")).toList();
		if (!exaltedTraits.isEmpty()) {
			addLine(Component.literal("EXALTED PASSIVES").withStyle(ChatFormatting.BOLD), 0xFFC77DFF, 3, wrapWidth);
			for (ClassType.Trait trait : exaltedTraits) {
				addLine(Component.literal("+ " + trait.name().replace(" (Exalted)", "")).withStyle(ChatFormatting.BOLD), 0xFFC77DFF, 0, wrapWidth);
				addLine(Component.literal("  " + trait.description()), 0xFFDBC4FF, 3, wrapWidth);
			}
		}
		if (!type.debuffTraits().isEmpty()) {
			addLine(Component.literal("DEBUFFS").withStyle(ChatFormatting.BOLD), 0xFFFF8A80, 3, wrapWidth);
			for (ClassType.Trait trait : type.debuffTraits()) {
				addLine(Component.literal("- " + trait.name()).withStyle(ChatFormatting.BOLD), 0xFFFF9D94, 0, wrapWidth);
				addLine(Component.literal("  " + trait.description()), 0xFFCBC4BB, 3, wrapWidth);
			}
		}
		addLine(Component.literal("AVAILABLE SHIELDS").withStyle(ChatFormatting.BOLD), 0xFF8BE9FD, 3, wrapWidth);
		addLine(Component.literal(type.shields().stream().map(ShieldType::displayName).reduce((a, b) -> a + "  •  " + b).orElse("None")), 0xFFC2EAF4, 0, wrapWidth);
	}

	private void buildShieldDetails(ShieldType type, int wrapWidth) {
		addLine(Component.literal(type.displayName()).withStyle(ChatFormatting.BOLD), 0xFF000000 | type.color(), 1, wrapWidth);
		addLine(Component.literal(type.subtitle() + "  •  " + type.category()), 0xFFD7CEC2, 6, wrapWidth);
		addAbility("PASSIVE", type.passive(), "Active while this usable shield is equipped in the offhand", type.passiveHelp(), 0xFF7CFF8B, wrapWidth);
		if (!type.exaltedPassive().isEmpty()) addAbility("EXALTED PASSIVE", type.exaltedPassive(), "Automatically readied on becoming Exalted",
			type.exaltedPassiveHelp(), 0xFFC77DFF, wrapWidth);
		if (type == ShieldType.ANGLER) addAnglerLootTable(wrapWidth);
		addAbility("ABILITY 1", type.abilityOne(), KeybindHandler.abilityKeyName(1) + "  •  " + type.abilityOneCooldown() + "s cooldown", type.abilityOneHelp(), 0xFF8BE9FD, wrapWidth);
		addAbility("ABILITY 2", type.abilityTwo(), KeybindHandler.abilityKeyName(2) + " or successful block  •  " + type.abilityTwoCooldown() + "s cooldown", type.abilityTwoHelp(), 0xFF8BE9FD, wrapWidth);
		addAbility("ULTIMATE", type.ultimate(), KeybindHandler.abilityKeyName(3) + "  •  Exalted only  •  " + type.ultimateCooldown() + "s cooldown", type.ultimateHelp(), 0xFFFFD166, wrapWidth);
	}

	private void addAnglerLootTable(int wrapWidth) {
		addLine(Component.literal("MOBY'S BLESSING LOOT TABLE").withStyle(ChatFormatting.BOLD), 0xFF5DE6F2, 1, wrapWidth);
		addLine(Component.literal("Fishing rolls this table on a 50% bonus proc; Catch of the Day rolls it immediately."), 0xFFB8A58C, 3, wrapWidth);
		for (String entry : AnglerLoot.guideEntries()) addLine(Component.literal("• " + entry), 0xFFD7CEC2, 0, wrapWidth);
		addLine(Component.literal("EXALTED — ABYSSAL TREASURE").withStyle(ChatFormatting.BOLD), 0xFFC77DFF, 1, wrapWidth);
		for (String entry : AnglerLoot.exaltedGuideEntries()) addLine(Component.literal("• " + entry), 0xFFD7CEC2, 0, wrapWidth);
		addLine(Component.literal("Odds total 100% within each special table."), 0xFF8E8174, 6, wrapWidth);
	}

	private void addAbility(String slot, String name, String timing, String help, int color, int wrapWidth) {
		addLine(Component.literal(slot + "  •  " + name).withStyle(ChatFormatting.BOLD), color, 0, wrapWidth);
		addLine(Component.literal(timing), 0xFFB8A58C, 1, wrapWidth);
		addLine(Component.literal(help), 0xFFD7CEC2, 6, wrapWidth);
	}

	private void addLine(Component component, int color, int spacing, int wrapWidth) {
		List<FormattedCharSequence> wrapped = font.split(component, wrapWidth);
		for (int i = 0; i < wrapped.size(); i++) detailLines.add(new RenderLine(wrapped.get(i), color, i == wrapped.size() - 1 ? spacing : 0));
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (mouseX >= detailX && mouseX <= detailX + detailWidth && mouseY >= detailY && mouseY <= detailY + detailHeight) {
			scroll = Math.max(0, Math.min(maxScroll, scroll - (int) Math.signum(scrollY) * 24));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		graphics.fillGradient(0, 0, width, height, 0xED08090E, 0xF0120D0A);
		graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xF01B1512);
		graphics.outline(panelX, panelY, panelWidth, panelHeight, 0xFFFFC857);
		graphics.fillGradient(panelX + 1, panelY + 1, panelX + panelWidth - 1, panelY + 23, 0xFF3A2416, 0xFF201711);
		graphics.centeredText(font, Component.literal("HONORSHIELDS COMPENDIUM").withStyle(ChatFormatting.BOLD), width / 2, panelY + 8, 0xFFFFD166);
		graphics.fill(panelX + sidebarWidth + 5, panelY + 53, panelX + sidebarWidth + 6, panelY + panelHeight - 34, 0xFF71553B);
		graphics.fill(detailX - 5, detailY - 5, detailX + detailWidth + 2, detailY + detailHeight + 5, 0x7A070707);
		graphics.outline(detailX - 5, detailY - 5, detailWidth + 7, detailHeight + 10, 0xFF59412D);

		graphics.enableScissor(detailX, detailY, detailX + detailWidth, detailY + detailHeight);
		int y = detailY - scroll;
		for (RenderLine line : detailLines) {
			if (y + 9 >= detailY && y <= detailY + detailHeight) graphics.text(font, line.text(), detailX, y, line.color(), false);
			y += 9 + line.spacing();
		}
		graphics.disableScissor();

		if (maxScroll > 0) {
			int trackX = detailX + detailWidth - 3;
			int thumbHeight = Math.max(18, detailHeight * detailHeight / (detailHeight + maxScroll));
			int thumbY = detailY + (detailHeight - thumbHeight) * scroll / maxScroll;
			graphics.fill(trackX, detailY, trackX + 2, detailY + detailHeight, 0xFF30241C);
			graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbHeight, 0xFFFFC857);
		}

		graphics.text(font, "Mouse wheel: scroll details", detailX, panelY + panelHeight - 20, 0xFF8E8174, false);
		super.extractRenderState(graphics, mouseX, mouseY, delta);
	}

	@Override public boolean isPauseScreen() { return false; }

	private static Component tabLabel(String name, boolean active) {
		return Component.literal((active ? "◆ " : "") + name).withStyle(active ? ChatFormatting.GOLD : ChatFormatting.GRAY);
	}

	private static Component entryLabel(String name, boolean active, int color) {
		return Component.literal((active ? "› " : "") + name).withColor(active ? color : 0xC8C8C8).withStyle(active ? ChatFormatting.BOLD : ChatFormatting.RESET);
	}

	private static String formatHearts(float maxHealth) {
		float hearts = maxHealth / 2.0F;
		return hearts == Math.round(hearts) ? Integer.toString(Math.round(hearts)) : Float.toString(hearts);
	}
}
