package net.fabricmc.fabric.api.networking.v1;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
public interface PayloadTypeRegistry<B> {
    public static PayloadTypeRegistry<RegistryFriendlyByteBuf> serverboundPlay() { return null; }
    public static PayloadTypeRegistry<RegistryFriendlyByteBuf> clientboundPlay() { return null; }
    <T extends CustomPacketPayload> CustomPacketPayload.TypeAndCodec<? super B, T> register(
        CustomPacketPayload.Type<T> type, StreamCodec<? super B, T> codec
    );
}
