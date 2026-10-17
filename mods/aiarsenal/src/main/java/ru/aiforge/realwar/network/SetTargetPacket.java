/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.core.BlockPos
 *  net.minecraft.network.FriendlyByteBuf
 *  net.minecraft.server.level.ServerPlayer
 *  net.minecraft.world.entity.Entity
 *  net.minecraftforge.network.NetworkEvent$Context
 */
package ru.aiforge.realwar.network;

import ru.aiforge.realwar.vehicle.LauncherVehicle;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

public record SetTargetPacket(int entityId, boolean set, int x, int y, int z) {
    public static void encode(SetTargetPacket m, FriendlyByteBuf b) {
        b.writeVarInt(m.entityId).writeBoolean(m.set).writeInt(m.x).writeInt(m.y).writeInt(m.z);
    }

    public static SetTargetPacket decode(FriendlyByteBuf b) {
        return new SetTargetPacket(b.readVarInt(), b.readBoolean(), b.readInt(), b.readInt(), b.readInt());
    }

    public static void handle(SetTargetPacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            LauncherVehicle lv;
            ServerPlayer p;
            block8: {
                block7: {
                    p = ((NetworkEvent.Context)ctx.get()).getSender();
                    if (p == null) {
                        return;
                    }
                    Entity v = p.getVehicle();
                    if (!(v instanceof LauncherVehicle)) break block7;
                    lv = (LauncherVehicle)v;
                    if (v.getId() == m.entityId) break block8;
                }
                return;
            }
            if (lv.seatOf((Entity)p) != (lv.spec.seatCount() > 1 ? 1 : 0)) {
                return;
            }
            if (!m.set) {
                lv.setMapTarget(p, null);
                return;
            }
            if (Math.abs(m.x) > 29999000 || Math.abs(m.z) > 29999000) {
                return;
            }
            int y = Math.max(p.level().getMinBuildHeight(), Math.min(p.level().getMaxBuildHeight() + 2000, m.y));
            lv.setMapTarget(p, new BlockPos(m.x, y, m.z));
        });
        ctx.get().setPacketHandled(true);
    }
}

