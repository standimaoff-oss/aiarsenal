package ru.aiforge.arsenal.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;
import ru.aiforge.arsenal.entity.LauncherEntity;

import java.util.function.Supplier;

/** Client -> server: launcher GUI action (target, launch, salvo, erect, unload). */
public record LauncherPacket(int entityId, int action, int x, int z) {
    public void encode(FriendlyByteBuf b) {
        b.writeVarInt(entityId);
        b.writeByte(action);
        b.writeInt(x);
        b.writeInt(z);
    }

    public static LauncherPacket decode(FriendlyByteBuf b) {
        return new LauncherPacket(b.readVarInt(), b.readByte(), b.readInt(), b.readInt());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        if (p != null) {
            Entity e = p.level().getEntity(entityId);
            if (e instanceof LauncherEntity l && e.distanceToSqr(p) < 24 * 24) l.action(p, action, x, z);
        }
        ctx.get().setPacketHandled(true);
    }
}
