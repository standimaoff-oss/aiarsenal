package ru.aiforge.realwar.strike;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** Client -> server: call a strike from the radio screen. */
public record StrikeRequestPacket(int type, double x, double z, float yaw, int fuze) {
    public static void encode(StrikeRequestPacket m, FriendlyByteBuf b) {
        b.writeVarInt(m.type);
        b.writeDouble(m.x);
        b.writeDouble(m.z);
        b.writeFloat(m.yaw);
        b.writeVarInt(m.fuze);
    }

    public static StrikeRequestPacket decode(FriendlyByteBuf b) {
        return new StrikeRequestPacket(b.readVarInt(), b.readDouble(), b.readDouble(), b.readFloat(), b.readVarInt());
    }

    public static void handle(StrikeRequestPacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer p = ctx.get().getSender();
            if (p == null || !Float.isFinite(m.yaw) || !Double.isFinite(m.x) || !Double.isFinite(m.z)) return;
            if (m.type < 0 || m.type >= StrikeType.values().length) return;
            int fuze = m.fuze == ru.aiforge.arsenal.nuke.ChargeRegistry.FUZE_REMOTE ? m.fuze : ru.aiforge.arsenal.nuke.ChargeRegistry.FUZE_AIR;
            String err = StrikeManager.request(p, StrikeType.byOrdinal(m.type), m.x, m.z, m.yaw, fuze);
            if (err != null && !err.isEmpty()) p.displayClientMessage(Component.translatable(err), true);
        });
        ctx.get().setPacketHandled(true);
    }
}
