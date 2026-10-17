package ru.aiforge.realwar.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** r22 server -> shooter: shot (recoil, flash, bolt), reload start (animation), loaded, dry click, mode, cancel. */
public record GunFxPacket(int fx, int ammo) {
    public static final int SHOT = 1, RELOAD = 2, RELOAD_EMPTY = 3, DRY = 4, MODE = 5, CANCEL = 6, LOADED = 7;

    public static void encode(GunFxPacket m, FriendlyByteBuf b) {
        b.writeByte(m.fx);
        b.writeShort(m.ammo);
    }

    public static GunFxPacket decode(FriendlyByteBuf b) {
        int fx = b.readByte();
        int ammo = b.readShort();
        return new GunFxPacket(fx, ammo);
    }

    public static void handle(GunFxPacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ru.aiforge.realwar.client.GunClient.onFx(m.fx, m.ammo)));
        ctx.get().setPacketHandled(true);
    }
}
