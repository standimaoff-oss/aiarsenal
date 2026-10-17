package ru.aiforge.realwar.craft;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** C->S from any crew member: trigger state + one-shot actions. */
public record CraftControlPacket(int id, boolean fire, int actions) {
    public static final int A_ENGINE = 1, A_WEAPON = 2, A_MODE = 4, A_SEAT = 8, A_FLARE = 16, A_GEAR = 32, A_LASER = 64, A_PERISCOPE = 128,
            /** r11: aeroplanes - change the bomb load on the ground (R). */
            A_LOADOUT = 256;

    public static void encode(CraftControlPacket m, FriendlyByteBuf b) {
        b.writeVarInt(m.id);
        b.writeBoolean(m.fire);
        b.writeVarInt(m.actions);
    }

    public static CraftControlPacket decode(FriendlyByteBuf b) {
        return new CraftControlPacket(b.readVarInt(), b.readBoolean(), b.readVarInt());
    }

    public static void handle(CraftControlPacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer p = ctx.get().getSender();
            if (p != null && p.getVehicle() instanceof CraftEntity c && c.getId() == m.id) c.handleControl(p, m.fire, m.actions);
        });
        ctx.get().setPacketHandled(true);
    }
}
