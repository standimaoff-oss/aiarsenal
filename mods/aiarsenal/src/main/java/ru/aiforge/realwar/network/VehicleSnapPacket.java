/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.network.FriendlyByteBuf
 *  net.minecraft.world.entity.Entity
 *  net.minecraftforge.api.distmarker.Dist
 *  net.minecraftforge.fml.DistExecutor
 *  net.minecraftforge.network.NetworkEvent$Context
 */
package ru.aiforge.realwar.network;

import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

public record VehicleSnapPacket(int entityId, double x, double y, double z, float yaw) {
    public static void encode(VehicleSnapPacket m, FriendlyByteBuf b) {
        b.writeVarInt(m.entityId).writeDouble(m.x).writeDouble(m.y).writeDouble(m.z).writeFloat(m.yaw);
    }

    public static VehicleSnapPacket decode(FriendlyByteBuf b) {
        return new VehicleSnapPacket(b.readVarInt(), b.readDouble(), b.readDouble(), b.readDouble(), b.readFloat());
    }

    public static void handle(VehicleSnapPacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn((Dist)Dist.CLIENT, () -> () -> {
            Entity e;
            Entity entity = e = Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.getEntity(m.entityId);
            if (e != null) {
                e.setPos(m.x, m.y, m.z);
                e.setYRot(m.yaw);
                e.xo = e.xOld = m.x;
                e.yo = e.yOld = m.y;
                e.zo = e.zOld = m.z;
            }
        }));
        ctx.get().setPacketHandled(true);
    }
}

