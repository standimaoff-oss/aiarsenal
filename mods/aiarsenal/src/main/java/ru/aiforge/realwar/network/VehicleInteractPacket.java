/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.network.FriendlyByteBuf
 *  net.minecraft.server.level.ServerPlayer
 *  net.minecraft.world.entity.Entity
 *  net.minecraftforge.network.NetworkEvent$Context
 */
package ru.aiforge.realwar.network;

import ru.aiforge.realwar.vehicle.VehicleEntity;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

public record VehicleInteractPacket(int entityId, int part) {
    public static void encode(VehicleInteractPacket m, FriendlyByteBuf b) {
        b.writeVarInt(m.entityId).writeVarInt(m.part + 1);
    }

    public static VehicleInteractPacket decode(FriendlyByteBuf b) {
        return new VehicleInteractPacket(b.readVarInt(), b.readVarInt() - 1);
    }

    public static void handle(VehicleInteractPacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer p = ((NetworkEvent.Context)ctx.get()).getSender();
            if (p == null) {
                return;
            }
            Entity e = p.level().getEntity(m.entityId);
            if (e instanceof VehicleEntity) {
                VehicleEntity v = (VehicleEntity)e;
                if (!p.isSpectator()) {
                    v.interactPart(p, m.part, false);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}

