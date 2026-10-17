package ru.aiforge.arsenal.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> all clients in the dimension: nuclear detonation (flash, fireball, mushroom cloud, shock wave, sound). */
public record NukeFxPacket(double x, double y, double z, int groundY, float radius, int kind) {
    public void encode(FriendlyByteBuf b) {
        b.writeDouble(x);
        b.writeDouble(y);
        b.writeDouble(z);
        b.writeInt(groundY);
        b.writeFloat(radius);
        b.writeByte(kind);
    }

    public static NukeFxPacket decode(FriendlyByteBuf b) {
        return new NukeFxPacket(b.readDouble(), b.readDouble(), b.readDouble(), b.readInt(), b.readFloat(), b.readByte());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ru.aiforge.arsenal.client.NukeFx.add(this));
        ctx.get().setPacketHandled(true);
    }
}
