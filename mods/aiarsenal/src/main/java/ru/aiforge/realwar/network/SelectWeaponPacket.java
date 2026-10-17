package ru.aiforge.realwar.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

/** r20 C->S: number key 1..9 pressed while riding - pick that slot of the vehicle's weapon bar. */
public record SelectWeaponPacket(int id, int slot) {
    public static void encode(SelectWeaponPacket m, FriendlyByteBuf b) {
        b.writeVarInt(m.id);
        b.writeByte(m.slot);
    }

    public static SelectWeaponPacket decode(FriendlyByteBuf b) {
        int id = b.readVarInt();
        int slot = b.readByte();
        return new SelectWeaponPacket(id, slot);
    }

    public static void handle(SelectWeaponPacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer p = ctx.get().getSender();
            if (p == null) return;
            Entity v = p.getVehicle();
            if (v == null || v.getId() != m.id || m.slot < 0 || m.slot > 9) return;
            if (v instanceof ru.aiforge.realwar.vehicle.GunVehicle gv) gv.selectSlot(p, m.slot);
            else if (v instanceof ru.aiforge.realwar.craft.CraftEntity c) c.selectSlot(c.seatOf(p), m.slot);
            else if (v instanceof ru.aiforge.realwar.aircraft.AircraftEntity a) a.selectBomb(m.slot);
        });
        ctx.get().setPacketHandled(true);
    }
}
