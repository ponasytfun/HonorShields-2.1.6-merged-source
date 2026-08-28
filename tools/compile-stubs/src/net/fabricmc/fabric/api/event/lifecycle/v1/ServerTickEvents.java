package net.fabricmc.fabric.api.event.lifecycle.v1;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.server.MinecraftServer;
public final class ServerTickEvents {
    public static final Event<EndTick> END_SERVER_TICK = null;
    @FunctionalInterface public interface EndTick { void onEndTick(MinecraftServer server); }
}
