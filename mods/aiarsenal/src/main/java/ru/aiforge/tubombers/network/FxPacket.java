package ru.aiforge.tubombers.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: explosion visual/audio effect (delayed sound, shock wave, camera shake, debris). */
public record FxPacket(double x, double y, double z, int kind, float power) {
    public static final int LIGHT = 0, MEDIUM = 1, HEAVY = 2, CRASH = 3;

    public void encode(FriendlyByteBuf b) {
        b.writeDouble(x);
        b.writeDouble(y);
        b.writeDouble(z);
        b.writeByte(kind);
        b.writeFloat(power);
    }

    public static FxPacket decode(FriendlyByteBuf b) {
        return new FxPacket(b.readDouble(), b.readDouble(), b.readDouble(), b.readByte(), b.readFloat());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        if (ctx.get().getDirection().getReceptionSide() == LogicalSide.CLIENT)
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ru.aiforge.tubombers.client.ClientFx.explosion(this));
        ctx.get().setPacketHandled(true);
    }
}
