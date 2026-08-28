package com.honorablesmp.honorshields.client;

import com.honorablesmp.honorshields.config.HonorShieldsConfig;
import com.honorablesmp.honorshields.shield.ShieldCondition;
import com.honorablesmp.honorshields.shield.ShieldType;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class AbilityHud {
	public static final int WIDTH = 238;
	public static final int HEIGHT = 202;
	private static final int BASE_HEIGHT = 118;
	private record Cooldown(String name, long readyAt, long duration) {}
	private record Resource(String kind, int current, int maximum, boolean armed) {}
	private static final Map<Integer, Cooldown> COOLDOWNS = new HashMap<>();
	private static Resource resource = new Resource("", 0, 0, false);
	private static Resource overflow = new Resource("", 0, 0, false);

	public static void setCooldown(int slot, String name, int seconds) {
		long duration = seconds * 1000L;
		COOLDOWNS.put(slot, new Cooldown(name, System.currentTimeMillis() + duration, duration));
	}

	public static void setResource(String kind, int current, int maximum, boolean armed) {
		Resource value = new Resource(kind == null ? "" : kind, Math.max(0, current), Math.max(0, maximum), armed);
		if ("verdancy_overflow".equals(value.kind())) overflow = value;
		else resource = value;
	}

	public static void clear() { COOLDOWNS.clear(); resource = new Resource("", 0, 0, false); overflow = new Resource("", 0, 0, false); }

	public static void render(GuiGraphicsExtractor graphics, DeltaTracker tickCounter) {
		Minecraft client = Minecraft.getInstance();
		var config = HonorShieldsConfig.get();
		if (client.player == null || !config.showAbilityHud || HONORABLESMPClient.shieldId.isEmpty()) return;
		ShieldType shield = ShieldType.byId(HONORABLESMPClient.shieldId);
		// The oath remains owned while stored in the player's inventory, but its
		// combat interface only exists while the physical shield is equipped.
		if (shield == null || ShieldType.fromStack(client.player.getOffhandItem()) != shield) return;
		ShieldCondition condition = ShieldCondition.byId(HONORABLESMPClient.conditionId);
		float scale = Math.max(0.5F, Math.min(2.0F, config.abilityHudScale));
		int x = config.abilityHudX, y = config.abilityHudY;
		boolean showResource = resource.maximum() > 0 && resourceMatches(shield, resource.kind());
		boolean showOverflow = shield == ShieldType.PLOW && condition == ShieldCondition.EXALTED && overflow.maximum() > 0;
		String exaltedPassive = exaltedPassiveName(shield);
		boolean showExaltedPassive = hasExaltedCooldown(exaltedPassive, shield);
		int width = WIDTH;
		int resourceRows = showResource ? (showOverflow ? 2 : 1) : 0;
		int height = resourceRows > 0 ? (showExaltedPassive ? HEIGHT + (resourceRows - 1) * 30 : 152 + (resourceRows - 1) * 30) : (showExaltedPassive ? 139 : BASE_HEIGHT);
		int accent = 0xFF000000 | shield.color();
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(scale, scale);
		HudStyle.frame(graphics, width, height, accent, 29);
		HudStyle.tile(graphics, 8, 6, 21, 21, accent);
		graphics.item(shield.stack(condition), 11, 8);
		graphics.text(client.font, shield.displayName().toUpperCase(), 35, 6, HudStyle.TEXT, true);
		graphics.text(client.font, condition.displayName().toUpperCase() + "  //  " + shield.category(), 35, 17, HudStyle.MUTED, false);
		String menuHint = "[" + KeybindHandler.menuKeyName() + "] CODEX";
		menuHint = fit(client, menuHint, 70);
		graphics.text(client.font, menuHint, width - client.font.width(menuHint) - 8, 7, 0xFF8BE9FD, false);

		graphics.fill(8, 33, width - 8, 51, HudStyle.SURFACE);
		graphics.outline(8, 33, width - 16, 18, 0x6647505D);
		HudStyle.chip(graphics, 10, 35, 50, 14, 0xFF4FAE72);
		graphics.centeredText(client.font, "PASSIVE", 35, 38, HudStyle.READY);
		graphics.text(client.font, fit(client, shield.passive() + " — " + shield.passiveHelp(), width - 77), 65, 38, 0xFFDAD2CA, false);

		drawSlot(graphics, client, 1, KeybindHandler.abilityKeyName(1), shield.abilityOne(), shield.abilityOneCooldown(), accent, true, 8, 54, width - 16);
		drawSlot(graphics, client, 2, KeybindHandler.abilityKeyName(2), shield.abilityTwo(), shield.abilityTwoCooldown(), accent, true, 8, 75, width - 16);
		if (shield == ShieldType.VAGABOND) drawNoUltimate(graphics, client, 8, 96, width - 16);
		else drawSlot(graphics, client, 3, KeybindHandler.abilityKeyName(3), shield.ultimate(), shield.ultimateCooldown(), HudStyle.GOLD,
			condition == ShieldCondition.EXALTED, 8, 96, width - 16);
		if (showExaltedPassive) drawSlot(graphics, client, 4, "", exaltedPassive,
			exaltedPassiveDuration(shield), HudStyle.GOLD,
			condition == ShieldCondition.EXALTED, 8, 117, width - 16);
		if (showResource) {
			int resourceY = showExaltedPassive ? 139 : 118;
			drawResource(graphics, client, resource, shield, 8, resourceY, width - 16);
			if (showOverflow) drawResource(graphics, client, overflow, shield, 8, resourceY + 31, width - 16);
		}
		graphics.pose().popMatrix();
	}

	private static String exaltedPassiveName(ShieldType shield) {
		return shield.exaltedPassive();
	}

	/** Permanent/resource-only exalted passives are represented by their meter or
	 * world effect, not as a fake always-ready cooldown row. */
	private static boolean hasExaltedCooldown(String name, ShieldType shield) {
		return !name.isEmpty() && switch (shield) {
			case TEMPEST, THUNDER, ANGLER, VAGABOND -> false;
			default -> true;
		};
	}

	private static int exaltedPassiveDuration(ShieldType shield) {
		return switch (shield) {
			case DAWN -> 1_200;
			case STONE -> 3_600;
			case MONSOON -> 300;
			default -> 0;
		};
	}

	private static boolean resourceMatches(ShieldType shield, String kind) {
		return switch (shield) {
			case THUNDER -> kind.equals("static");
			case TEMPEST -> kind.equals("wind");
			case DAWN -> kind.equals("sun");
			case WARDEN -> kind.equals("warden");
			case VOID -> kind.equals("void");
			default -> false;
		};
	}

	private static void drawResource(GuiGraphicsExtractor graphics, Minecraft client, Resource meter,
		ShieldType shield, int x, int y, int width) {
		String label = switch (meter.kind()) {
			case "static" -> "STATIC CHARGE";
			case "wind" -> "WIND CHARGE";
			case "sun" -> meter.armed() ? "FULL SUN ARMED" : "SUN CHARGE";
			case "warden" -> "STORED DAMAGE";
			case "void" -> "VOID CHARGE";
			case "verdancy" -> "VERDANCY";
			case "verdancy_overflow" -> "OVERFLOW";
			default -> "RESOURCE";
		};
		int accent = 0xFF000000 | shield.color();
		graphics.fill(x, y, x + width, y + 30, HudStyle.SURFACE);
		graphics.outline(x, y, width, 30, 0x6647505D);
		graphics.text(client.font, label, x + 5, y + 3, HudStyle.TEXT, true);
		String value = meter.current() + " / " + meter.maximum();
		graphics.text(client.font, value, x + width - client.font.width(value) - 5, y + 3, HudStyle.TEXT, true);
		int barX = x + 5, barWidth = width - 10;
		graphics.fill(barX, y + 17, barX + barWidth, y + 24, 0xFF252A34);
		float progress = meter.maximum() == 0 ? 0.0F : Math.min(1.0F, meter.current() / (float) meter.maximum());
		graphics.fill(barX, y + 17, barX + Math.round(barWidth * progress), y + 24, HudStyle.opaque(accent));
		int segments = Math.min(20, meter.maximum());
		if (segments > 1) for (int i = 1; i < segments; i++) {
			int line = barX + Math.round(barWidth * i / (float) segments);
			graphics.fill(line, y + 17, line + 1, y + 24, 0xAA151922);
		}
	}

	private static void drawSlot(GuiGraphicsExtractor graphics, Minecraft client, int slot, String key, String fallback,
		int fallbackDuration, int accent, boolean unlocked, int x, int y, int width) {
		Cooldown cooldown = COOLDOWNS.get(slot);
		long remaining = cooldown == null ? 0L : cooldown.readyAt() - System.currentTimeMillis();
		boolean ready = unlocked && remaining <= 0L;
		String name = cooldown == null ? fallback : cooldown.name();
		String suffix = !unlocked ? "LOCKED" : ready ? "READY" : "%.1fs".formatted(remaining / 1000.0);
		int color = ready ? HudStyle.READY : HudStyle.WAITING;
		// The key tile doubles as the move icon. Keep the entire move treatment
		// neutral while unavailable, then restore its shield/ultimate accent at
		// the exact moment the cooldown expires.
		int moveAccent = ready ? accent : 0xFF626975;
		int keyColor = ready ? HudStyle.GOLD : 0xFFC1C5CC;
		int nameColor = ready ? HudStyle.TEXT : 0xFF999FA9;
		int keyWidth = Math.max(17, client.font.width(key) + 8);
		graphics.fill(x, y, x + width, y + 18, HudStyle.SURFACE);
		graphics.outline(x, y, width, 18, 0x6647505D);
		graphics.fill(x, y, x + 3, y + 18, HudStyle.opaque(moveAccent));
		HudStyle.chip(graphics, x + 5, y + 2, keyWidth, 13, moveAccent);
		graphics.centeredText(client.font, key, x + 5 + keyWidth / 2, y + 4, keyColor);
		int statusWidth = client.font.width(suffix);
		graphics.text(client.font, fit(client, name, width - keyWidth - statusWidth - 22), x + keyWidth + 11, y + 4, nameColor, true);
		graphics.text(client.font, suffix, x + width - statusWidth - 4, y + 4, color, true);
		long duration = cooldown == null ? fallbackDuration * 1000L : Math.max(1L, cooldown.duration());
		float progress = !unlocked ? 0.0F : ready ? 1.0F : Math.max(0.0F, 1.0F - remaining / (float) duration);
		graphics.fill(x + 3, y + 16, x + width, y + 18, 0xFF252A34);
		graphics.fill(x + 3, y + 16, x + 3 + Math.round((width - 3) * progress), y + 18, HudStyle.opaque(moveAccent));
	}

	private static void drawNoUltimate(GuiGraphicsExtractor graphics, Minecraft client, int x, int y, int width) {
		graphics.fill(x, y, x + width, y + 18, HudStyle.SURFACE);
		graphics.outline(x, y, width, 18, 0x6647505D);
		graphics.fill(x, y, x + 3, y + 18, 0xFF626975);
		graphics.text(client.font, "NO ULTIMATE", x + 10, y + 4, 0xFF999FA9, true);
		graphics.text(client.font, "FIELD SPECIALIST", x + width - client.font.width("FIELD SPECIALIST") - 5, y + 4, HudStyle.MUTED, true);
	}

	private static String fit(Minecraft client, String text, int maxWidth) {
		if (client.font.width(text) <= maxWidth) return text;
		String suffix = "…";
		while (!text.isEmpty() && client.font.width(text + suffix) > maxWidth) text = text.substring(0, text.length() - 1);
		return text + suffix;
	}

	private AbilityHud() {}
}
