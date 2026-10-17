package ru.aiforge.realwar.craft;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/** S->C: the server rejected the pilot's state (teleport / desync) - snap back. */
public record CraftSnapPacket(int id, double x, double y, double z) {
    public static void encode(CraftSnapPacket m, FriendlyByteBuf b) {
        b.writeVarInt(m.id);
        b.writeDouble(m.x);
        b.writeDouble(m.y);
        b.writeDouble(m.z);
    }

    public static CraftSnapPacket decode(FriendlyByteBuf b) {
        return new CraftSnapPacket(b.readVarInt(), b.readDouble(), b.readDouble(), b.readDouble());
    }

    public static void handle(CraftSnapPacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> CraftEntity.snapHandler.accept(m));
        ctx.get().setPacketHandled(true);
    }
}
