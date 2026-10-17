package ru.aiforge.tubombers.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import ru.aiforge.tubombers.entity.BomberEntity;

import java.util.function.Supplier;

/** Client -> server: discrete crew commands (sit/stand/exit, gear, flaps, bay, sweep, release, target...). */
public record CrewActionPacket(int vehicle, int action, int arg, double x, double y, double z) {
    public CrewActionPacket(int vehicle, int action, int arg) {
        this(vehicle, action, arg, 0, 0, 0);
    }

    public void encode(FriendlyByteBuf b) {
        b.writeVarInt(vehicle);
        b.writeByte(action);
        b.writeByte(arg);
        b.writeDouble(x);
        b.writeDouble(y);
        b.writeDouble(z);
    }

    public static CrewActionPacket decode(FriendlyByteBuf b) {
        return new CrewActionPacket(b.readVarInt(), b.readByte(), b.readByte(), b.readDouble(), b.readDouble(), b.readDouble());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        if (p != null && p.getVehicle() instanceof BomberEntity b && b.getId() == vehicle && Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z))
            b.crewAction(p, action, arg, x, y, z);
        ctx.get().setPacketHandled(true);
    }
}
