package com.honorablesmp.honorshields.client.gui;

import com.honorablesmp.honorshields.shield.ShieldType;
import com.honorablesmp.honorshields.shield.ShieldCondition;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

public final class ShieldRevealAnimation {
	private static ShieldType shield;
	private static ShieldCondition condition = ShieldCondition.HONORED;
	private static long started;

	public static void show(ShieldType type, ShieldCondition currentCondition) {
		shield = type;
		condition = currentCondition == null ? ShieldCondition.HONORED : currentCondition;
		started = System.currentTimeMillis();
	}

	public static void clear() { shield = null; }

	public static void render(GuiGraphicsExtractor graphics, DeltaTracker tickCounter) {
		if (shield == null) return;
		long elapsed = System.currentTimeMillis() - started;
		if (elapsed >= 3000) { shield = null; return; }
		Minecraft client = Minecraft.getInstance();
		float rawFade = elapsed < 300 ? elapsed / 300.0F : elapsed > 2500 ? (3000 - elapsed) / 500.0F : 1.0F;
		float fade = rawFade * rawFade * (3.0F - 2.0F * rawFade);
		int alpha = Mth.clamp(Math.round(220 * fade), 0, 220);
		int centerX = graphics.guiWidth() / 2;
		int centerY = graphics.guiHeight() / 2;
		float entrance = Mth.clamp(elapsed / 420.0F, 0.0F, 1.0F);
		entrance = 1.0F - (1.0F - entrance) * (1.0F - entrance);
		int halfWidth = Math.round((105 + 22 * entrance) * fade);
		int accent = conditionColor(condition);
		graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), (Math.round(115 * fade) << 24));
		graphics.fill(centerX - halfWidth, centerY - 46, centerX + halfWidth, centerY + 46, (alpha << 24) | 0x160E09);
		graphics.outline(centerX - halfWidth, centerY - 46, halfWidth * 2, 92, (Mth.clamp(Math.round(255 * fade), 0, 255) << 24) | accent);
		graphics.fill(centerX - halfWidth + 8, centerY - 39, centerX + halfWidth - 8, centerY - 38,
			(Mth.clamp(Math.round(170 * fade), 0, 255) << 24) | (shield.color() & 0xFFFFFF));
		graphics.item(shield.stack(), centerX - 8, centerY - 34);
		graphics.centeredText(client.font, shield.displayName() + " Shield", centerX, centerY - 9, (Mth.clamp(Math.round(255 * fade), 0, 255) << 24) | shield.color());
		graphics.centeredText(client.font, shield.subtitle(), centerX, centerY + 8, (Mth.clamp(Math.round(230 * fade), 0, 255) << 24) | 0xE8D9B5);
		graphics.centeredText(client.font, condition.displayName() + " • " + shield.passive(), centerX, centerY + 24,
			(Mth.clamp(Math.round(220 * fade), 0, 255) << 24) | accent);
	}

	private static int conditionColor(ShieldCondition value) {
		return switch (value) {
			case EXALTED -> 0xFFD76A;
			case BLESSED -> 0x9FEAFF;
			case HONORED -> 0xFFC857;
			case TARNISHED -> 0x928A82;
			case FORSAKEN -> 0x87329D;
		};
	}

	private ShieldRevealAnimation() {}
}
