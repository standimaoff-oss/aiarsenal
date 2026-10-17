package ru.aiforge.tubombers.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import ru.aiforge.tubombers.entity.BomberEntity;

import java.util.function.Supplier;

/** Client -> server, every tick while seated at a crew station: stick, rudder, throttle, brakes, release consent. */
public record PilotInputPacket(int vehicle, float pitch, float roll, float yaw, float throttle, boolean brake, boolean consent) {
    public void encode(FriendlyByteBuf b) {
        b.writeVarInt(vehicle);
        b.writeFloat(pitch);
        b.writeFloat(roll);
        b.writeFloat(yaw);
        b.writeFloat(throttle);
        b.writeByte((brake ? 1 : 0) | (consent ? 2 : 0));
    }

    public static PilotInputPacket decode(FriendlyByteBuf b) {
        int v = b.readVarInt();
        float p = b.readFloat(), r = b.readFloat(), y = b.readFloat(), t = b.readFloat();
        int f = b.readByte();
        return new PilotInputPacket(v, p, r, y, t, (f & 1) != 0, (f & 2) != 0);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        if (p != null && p.getVehicle() instanceof BomberEntity b && b.getId() == vehicle
                && Float.isFinite(pitch) && Float.isFinite(roll) && Float.isFinite(yaw) && Float.isFinite(throttle))
            b.pilotInput(p, pitch, roll, yaw, throttle, brake, consent);
        ctx.get().setPacketHandled(true);
    }
}
