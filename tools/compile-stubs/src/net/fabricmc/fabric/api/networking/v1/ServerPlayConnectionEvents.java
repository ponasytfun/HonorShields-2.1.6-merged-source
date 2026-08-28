package net.fabricmc.fabric.api.networking.v1;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
public final class ServerPlayConnectionEvents {
    public static final Event<Join> JOIN = null;
    @FunctionalInterface public interface Join { void onPlayReady(ServerGamePacketListenerImpl handler, PacketSender sender, MinecraftServer server); }
}
