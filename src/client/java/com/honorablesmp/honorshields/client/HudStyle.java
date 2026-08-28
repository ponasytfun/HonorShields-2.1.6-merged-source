package com.honorablesmp.honorshields.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Shared visual language for the class and ability HUD panels. */
public final class HudStyle {
	public static final int BODY = 0xF20B0D12;
	public static final int SURFACE = 0xD9151820;
	public static final int SURFACE_DARK = 0xE0101218;
	public static final int TEXT = 0xFFF4EEE7;
	public static final int MUTED = 0xFFB7AAA0;
	public static final int GOLD = 0xFFFFD166;
	public static final int READY = 0xFF7CFF9B;
	public static final int WAITING = 0xFFFF8A80;

	public static void frame(GuiGraphicsExtractor graphics, int width, int height, int accent, int headerHeight) {
		graphics.fill(4, 5, width + 5, height + 6, 0x6A000000);
		graphics.fill(2, 3, width + 3, height + 4, 0x96000000);
		graphics.fill(0, 0, width, height, BODY);
		graphics.outline(0, 0, width, height, opaque(shade(accent, 0.72F)));
		graphics.outline(2, 2, width - 4, height - 4, 0x7747505D);
		graphics.fillGradient(1, 1, width - 1, headerHeight, alpha(shade(accent, 0.48F), 0xF4), 0xEE151820);
		graphics.fill(1, 1, width - 1, 4, opaque(accent));
		graphics.fill(1, headerHeight, width - 1, headerHeight + 1, alpha(accent, 0x88));
		graphics.fill(1, 1, 7, 7, opaque(accent));
		graphics.fill(width - 7, 1, width - 1, 7, opaque(accent));
		graphics.fill(1, height - 7, 4, height - 1, alpha(accent, 0xCC));
		graphics.fill(width - 4, height - 7, width - 1, height - 1, alpha(accent, 0xCC));
	}

	public static void tile(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int accent) {
		graphics.fill(x, y, x + width, y + height, SURFACE_DARK);
		graphics.outline(x, y, width, height, alpha(accent, 0xDD));
		graphics.fill(x + 1, y + 1, x + 3, y + height - 1, opaque(accent));
	}

	public static void chip(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int accent) {
		graphics.fill(x, y, x + width, y + height, alpha(shade(accent, 0.42F), 0xE8));
		graphics.outline(x, y, width, height, alpha(accent, 0xB8));
	}

	public static int alpha(int color, int alpha) {
		return (alpha & 0xFF) << 24 | color & 0x00FFFFFF;
	}

	public static int opaque(int color) {
		return 0xFF000000 | color & 0x00FFFFFF;
	}

	public static int shade(int color, float factor) {
		int red = Math.min(255, Math.round(((color >> 16) & 0xFF) * factor));
		int green = Math.min(255, Math.round(((color >> 8) & 0xFF) * factor));
		int blue = Math.min(255, Math.round((color & 0xFF) * factor));
		return red << 16 | green << 8 | blue;
	}

	private HudStyle() {}
}
