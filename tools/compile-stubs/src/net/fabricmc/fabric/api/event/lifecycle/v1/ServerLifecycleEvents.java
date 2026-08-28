package net.fabricmc.fabric.api.event.lifecycle.v1;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.server.MinecraftServer;

public final class ServerLifecycleEvents {
    public static final Event<ServerStopping> SERVER_STOPPING = null;
    @FunctionalInterface public interface ServerStopping { void onServerStopping(MinecraftServer server); }
}
