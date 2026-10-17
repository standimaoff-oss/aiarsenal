/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.network.FriendlyByteBuf
 *  net.minecraft.server.level.ServerPlayer
 *  net.minecraft.world.entity.Entity
 *  net.minecraftforge.network.NetworkEvent$Context
 */
package ru.aiforge.realwar.radar;

import ru.aiforge.realwar.vehicle.SamLauncherEntity;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

public record RadarFirePacket(int vehId, int targetId) {
    public static void encode(RadarFirePacket m, FriendlyByteBuf b) {
        b.writeVarInt(m.vehId).writeInt(m.targetId);
    }

    public static RadarFirePacket decode(FriendlyByteBuf b) {
        return new RadarFirePacket(b.readVarInt(), b.readInt());
    }

    public static void handle(RadarFirePacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            SamLauncherEntity s;
            Entity patt837$temp;
            ServerPlayer p = ((NetworkEvent.Context)ctx.get()).getSender();
            if (p != null && p.getVehicle() instanceof ru.aiforge.realwar.vehicle.YarsVehicle y && y.getId() == m.vehId) {
                y.radarLaunch(p, m.targetId);
                return;
            }
            if (p == null || !((patt837$temp = p.getVehicle()) instanceof SamLauncherEntity) || (s = (SamLauncherEntity)patt837$temp).getId() != m.vehId) {
                return;
            }
            s.fireAt(p, m.targetId);
        });
        ctx.get().setPacketHandled(true);
    }
}

