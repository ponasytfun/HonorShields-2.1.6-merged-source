package com.honorablesmp.honorshields.client.gui;

import com.honorablesmp.honorshields.classsystem.ClassType;
import com.honorablesmp.honorshields.client.HONORABLESMPClient;
import com.honorablesmp.honorshields.client.HudStyle;
import com.honorablesmp.honorshields.client.KeybindHandler;
import com.honorablesmp.honorshields.config.HonorShieldsConfig;
import com.honorablesmp.honorshields.shield.ShieldType;
import com.honorablesmp.honorshields.shield.ShieldCondition;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class LeaderboardHud {
	public static final int WIDTH = 178;
	public static final int HEIGHT = 66;

	public static void render(GuiGraphicsExtractor graphics, DeltaTracker tickCounter) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || HONORABLESMPClient.classId.isEmpty()) return;
		var config = HonorShieldsConfig.get();
		if (!config.showLeaderboard || !HONORABLESMPClient.hudVisible) return;
		ClassType classType = ClassType.byId(HONORABLESMPClient.classId);
		ShieldType shieldType = ShieldType.byId(HONORABLESMPClient.shieldId);
		// Hide the complete oath HUD whenever the assigned shield is stowed. This
		// mirrors the server's offhand-only ability authorization and leaves the
		// screen uncluttered while another offhand item is in use.
		if (classType == null || shieldType == null || ShieldType.fromStack(client.player.getOffhandItem()) != shieldType) return;
		ShieldCondition condition = ShieldCondition.byId(HONORABLESMPClient.conditionId);
		float scale = Math.max(0.5F, Math.min(2.0F, config.leaderboardScale * HONORABLESMPClient.hudScale));
		int x = config.leaderboardX;
		int y = config.leaderboardY;
		int width = WIDTH, height = HEIGHT;
		int accent = 0xFF000000 | classType.color();
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(scale, scale);
		HudStyle.frame(graphics, width, height, accent, 23);
		graphics.text(client.font, "HONORSHIELDS", 9, 7, HudStyle.GOLD, true);
		String hint = "[" + KeybindHandler.menuKeyName() + "] GUIDE";
		hint = fit(client, hint, 62);
		graphics.text(client.font, hint, width - client.font.width(hint) - 8, 7, 0xFF8BE9FD, false);
		HudStyle.tile(graphics, 8, 27, 24, 24, accent);
		String initial = classType.displayName().substring(0, 1);
		graphics.centeredText(client.font, initial, 20, 34, HudStyle.opaque(accent));
		graphics.text(client.font, classType.displayName().toUpperCase(), 38, 27, HudStyle.TEXT, true);
		String summary = classType.passiveTraits().size() + " PASSIVES";
		if (!classType.debuffTraits().isEmpty()) summary += "  //  " + classType.debuffTraits().size() + " DRAWBACKS";
		graphics.text(client.font, fit(client, summary, shieldType == null ? width - 46 : width - 71), 38, 39, HudStyle.MUTED, false);
		if (shieldType != null) {
			HudStyle.tile(graphics, width - 29, 27, 21, 21, 0xFF000000 | shieldType.color());
			graphics.item(shieldType.stack(condition), width - 26, 29);
		}
		String shieldText = shieldType == null ? "Shield: Unassigned" : shieldType.displayName() + "  •  " + condition.displayName();
		graphics.text(client.font, fit(client, shieldText, width - 16), 9, 55, shieldType == null ? 0xFF9A8F84 : 0xFF8BE9FD, true);
		graphics.pose().popMatrix();
	}

	private static String fit(Minecraft client, String text, int maxWidth) {
		if (client.font.width(text) <= maxWidth) return text;
		String suffix = "…";
		while (!text.isEmpty() && client.font.width(text + suffix) > maxWidth) text = text.substring(0, text.length() - 1);
		return text + suffix;
	}

	private LeaderboardHud() {}
}
