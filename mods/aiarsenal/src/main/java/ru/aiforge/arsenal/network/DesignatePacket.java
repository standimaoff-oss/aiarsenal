package ru.aiforge.arsenal.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import ru.aiforge.arsenal.control.RemoteControl;

import java.util.function.Supplier;

/** Client -> server: target designated through the drone camera (block point + optional entity). */
public record DesignatePacket(int entityId, double x, double y, double z, int hitId) {
    public void encode(FriendlyByteBuf b) {
        b.writeVarInt(entityId);
        b.writeDouble(x);
        b.writeDouble(y);
        b.writeDouble(z);
        b.writeVarInt(hitId + 1);
    }

    public static DesignatePacket decode(FriendlyByteBuf b) {
        return new DesignatePacket(b.readVarInt(), b.readDouble(), b.readDouble(), b.readDouble(), b.readVarInt() - 1);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        if (p != null) RemoteControl.designate(p, entityId, new Vec3(x, y, z), hitId);
        ctx.get().setPacketHandled(true);
    }
}
