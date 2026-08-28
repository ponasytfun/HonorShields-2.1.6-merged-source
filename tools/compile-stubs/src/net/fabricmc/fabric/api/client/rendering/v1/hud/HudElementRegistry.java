package net.fabricmc.fabric.api.client.rendering.v1.hud;

import net.minecraft.resources.Identifier;

public interface HudElementRegistry {
    public static void addLast(Identifier id, HudElement element) {}
}
