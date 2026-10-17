/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.network.FriendlyByteBuf
 *  net.minecraftforge.network.NetworkEvent$Context
 */
package ru.aiforge.realwar.radar;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

public record RadarQueryPacket(int vehId) {
    public static void encode(RadarQueryPacket m, FriendlyByteBuf b) {
        b.writeVarInt(m.vehId);
    }

    public static RadarQueryPacket decode(FriendlyByteBuf b) {
        return new RadarQueryPacket(b.readVarInt());
    }

    public static void handle(RadarQueryPacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // reconstructed from the original bytecode (CFR could not decompile this lambda)
            net.minecraft.server.level.ServerPlayer p = ctx.get().getSender();
            if (p == null || !(p.getVehicle() instanceof ru.aiforge.realwar.vehicle.LauncherVehicle v) || v.getId() != m.vehId) return;
            boolean self = v instanceof RadarNet.RadarEmitter re && re.radarActive();
            java.util.List<net.minecraft.world.entity.Entity> src = RadarNet.sources(v);
            java.util.List<RadarNet.Contact> cs = src.isEmpty() ? java.util.List.of() : RadarNet.contacts(v);
            if (cs.size() > 200) cs = cs.subList(0, 200);
            ru.aiforge.realwar.network.ModNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> p),
                    new RadarReplyPacket(v.getId(), !src.isEmpty(), self,
                            src.stream().map(e -> new float[]{(float) e.getX(), (float) e.getZ(), (float) ((RadarNet.RadarEmitter) e).radarRange()}).toList(), cs));
        });
        ctx.get().setPacketHandled(true);
    }
}

