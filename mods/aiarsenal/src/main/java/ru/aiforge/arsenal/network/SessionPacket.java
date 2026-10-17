package ru.aiforge.arsenal.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: remote session started (entity id) or ended (with a reason key). */
public record SessionPacket(int entityId, boolean start, String reason) {
    public void encode(FriendlyByteBuf b) {
        b.writeVarInt(entityId + 1);
        b.writeBoolean(start);
        b.writeUtf(reason, 128);
    }

    public static SessionPacket decode(FriendlyByteBuf b) {
        return new SessionPacket(b.readVarInt() - 1, b.readBoolean(), b.readUtf(128));
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ru.aiforge.arsenal.client.ArsClient.session(this));
        ctx.get().setPacketHandled(true);
    }
}
