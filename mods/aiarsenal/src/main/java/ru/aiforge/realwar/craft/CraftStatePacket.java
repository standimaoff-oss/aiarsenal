package ru.aiforge.realwar.craft;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** C->S every tick from the pilot: full physical state (client-authoritative flight / sailing). */
public record CraftStatePacket(int id, double x, double y, double z, float vx, float vy, float vz, float yaw, float pitch,
                               float roll, float a1, float a2, float a3, float impact) {
    public static void encode(CraftStatePacket m, FriendlyByteBuf b) {
        b.writeVarInt(m.id);
        b.writeDouble(m.x);
        b.writeDouble(m.y);
        b.writeDouble(m.z);
        for (float v : new float[]{m.vx, m.vy, m.vz, m.yaw, m.pitch, m.roll}) b.writeFloat(v);
        for (float v : new float[]{m.a1, m.a2, m.a3, m.impact}) b.writeFloat(v);
    }

    public static CraftStatePacket decode(FriendlyByteBuf b) {
        return new CraftStatePacket(b.readVarInt(), b.readDouble(), b.readDouble(), b.readDouble(), b.readFloat(), b.readFloat(),
                b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat());
    }

    public static void handle(CraftStatePacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer p = ctx.get().getSender();
            if (p != null && p.getVehicle() instanceof CraftEntity c && c.getId() == m.id) {
                c.acceptClientState(p, m.x, m.y, m.z, m.vx, m.vy, m.vz, m.yaw, m.pitch, m.roll, m.a1, m.a2, m.a3, m.impact);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
