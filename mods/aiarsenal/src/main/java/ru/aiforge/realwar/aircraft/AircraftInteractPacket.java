/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.network.FriendlyByteBuf
 *  net.minecraft.server.level.ServerPlayer
 *  net.minecraft.world.InteractionHand
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.entity.player.Player
 *  net.minecraftforge.network.NetworkEvent$Context
 */
package ru.aiforge.realwar.aircraft;

import ru.aiforge.realwar.aircraft.AircraftEntity;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkEvent;

public record AircraftInteractPacket(int entityId, boolean bay) {
    public static void encode(AircraftInteractPacket m, FriendlyByteBuf b) {
        b.writeInt(m.entityId).writeBoolean(m.bay);
    }

    public static AircraftInteractPacket decode(FriendlyByteBuf b) {
        return new AircraftInteractPacket(b.readInt(), b.readBoolean());
    }

    public static void handle(AircraftInteractPacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            AircraftEntity a;
            ServerPlayer p;
            block7: {
                block6: {
                    p = ((NetworkEvent.Context)ctx.get()).getSender();
                    if (p == null) {
                        return;
                    }
                    Entity e = p.level().getEntity(m.entityId);
                    if (!(e instanceof AircraftEntity)) break block6;
                    a = (AircraftEntity)e;
                    if (!(p.distanceToSqr(e) > 1600.0)) break block7;
                }
                return;
            }
            if (m.bay) {
                a.handleBay(p, InteractionHand.MAIN_HAND);
            } else {
                a.tryBoard((Player)p, InteractionHand.MAIN_HAND);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}

