package ru.aiforge.arsenal.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import ru.aiforge.arsenal.item.DetonatorItem;

import java.util.function.Supplier;

/** Client -> server: detonator action (0 refresh, 1 detonate id, 2 detonate all, 3 cycle fuze of id). */
public record DetonatorPacket(int action, int id) {
    public static final int REFRESH = 0, DETONATE = 1, ALL = 2, FUZE = 3;

    public void encode(FriendlyByteBuf b) {
        b.writeByte(action);
        b.writeVarInt(id);
    }

    public static DetonatorPacket decode(FriendlyByteBuf b) {
        return new DetonatorPacket(b.readByte(), b.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        if (p != null) DetonatorItem.action(p, action, id);
        ctx.get().setPacketHandled(true);
    }
}
