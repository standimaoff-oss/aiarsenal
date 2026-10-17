package ru.aiforge.arsenal.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import ru.aiforge.arsenal.control.RemoteControl;

import java.util.function.Supplier;

/** Client -> server, every tick of a remote session: keys, camera yaw/pitch, zoom; exit when entityId &lt; 0. */
public record ControlPacket(int entityId, int keys, float yaw, float pitch, float zoom) {
    public void encode(FriendlyByteBuf b) {
        b.writeVarInt(entityId + 1);
        b.writeVarInt(keys);
        b.writeFloat(yaw);
        b.writeFloat(pitch);
        b.writeFloat(zoom);
    }

    public static ControlPacket decode(FriendlyByteBuf b) {
        return new ControlPacket(b.readVarInt() - 1, b.readVarInt(), b.readFloat(), b.readFloat(), b.readFloat());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        if (p != null) {
            if (entityId < 0) {
                if (RemoteControl.inSession(p)) RemoteControl.stop(p, null);
            } else RemoteControl.input(p, entityId, keys, yaw, pitch, zoom);
        }
        ctx.get().setPacketHandled(true);
    }
}
