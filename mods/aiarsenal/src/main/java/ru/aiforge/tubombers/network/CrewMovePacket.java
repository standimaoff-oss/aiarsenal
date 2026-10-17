package ru.aiforge.tubombers.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import ru.aiforge.tubombers.entity.BomberEntity;

import java.util.function.Supplier;

/** Client -> server: own position inside the cabin (walking / ladder), validated by the server. */
public record CrewMovePacket(int vehicle, int mode, int index, float x, float y, float z) {
    public void encode(FriendlyByteBuf b) {
        b.writeVarInt(vehicle);
        b.writeByte(mode);
        b.writeByte(index);
        b.writeFloat(x);
        b.writeFloat(y);
        b.writeFloat(z);
    }

    public static CrewMovePacket decode(FriendlyByteBuf b) {
        return new CrewMovePacket(b.readVarInt(), b.readByte(), b.readByte(), b.readFloat(), b.readFloat(), b.readFloat());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        if (p != null && p.getVehicle() instanceof BomberEntity b && b.getId() == vehicle && Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z))
            b.handleCrewMove(p, mode, index, x, y, z);
        ctx.get().setPacketHandled(true);
    }
}
