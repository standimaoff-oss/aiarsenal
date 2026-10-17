/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.core.BlockPos
 *  net.minecraft.network.FriendlyByteBuf
 *  net.minecraft.server.level.ServerPlayer
 *  net.minecraft.world.level.block.entity.BlockEntity
 *  net.minecraftforge.network.NetworkEvent$Context
 */
package ru.aiforge.realwar.missile;

import ru.aiforge.realwar.missile.LaunchComputerBlockEntity;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

public record ComputerActionPacket(BlockPos pos, int action, int a, int b) {
    public static void encode(ComputerActionPacket m, FriendlyByteBuf buf) {
        buf.writeBlockPos(m.pos).writeVarInt(m.action).writeInt(m.a).writeInt(m.b);
    }

    public static ComputerActionPacket decode(FriendlyByteBuf buf) {
        return new ComputerActionPacket(buf.readBlockPos(), buf.readVarInt(), buf.readInt(), buf.readInt());
    }

    public static void handle(ComputerActionPacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer p = ((NetworkEvent.Context)ctx.get()).getSender();
            if (p == null || !p.level().isLoaded(m.pos)) {
                return;
            }
            BlockEntity patt1031$temp = p.level().getBlockEntity(m.pos);
            if (patt1031$temp instanceof LaunchComputerBlockEntity) {
                LaunchComputerBlockEntity be = (LaunchComputerBlockEntity)patt1031$temp;
                be.handle(p, m.action, m.a, m.b);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}

