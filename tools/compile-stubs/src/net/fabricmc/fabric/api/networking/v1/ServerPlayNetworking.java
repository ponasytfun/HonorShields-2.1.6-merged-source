package net.fabricmc.fabric.api.networking.v1;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
public final class ServerPlayNetworking {
    @FunctionalInterface public interface PlayPayloadHandler<T extends CustomPacketPayload> { void receive(T payload, Context context); }
    public interface Context { MinecraftServer server(); ServerPlayer player(); }
    public static <T extends CustomPacketPayload> boolean registerGlobalReceiver(CustomPacketPayload.Type<T> type, PlayPayloadHandler<T> handler) { return true; }
    public static void send(ServerPlayer player, CustomPacketPayload payload) {}
}
