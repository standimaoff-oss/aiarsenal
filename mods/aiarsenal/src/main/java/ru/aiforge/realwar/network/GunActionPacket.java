package ru.aiforge.realwar.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/** r22 client -> server: small-arms input (trigger / aim / reload / fire selector), see Firearms.TRIGGER_DOWN.. */
public record GunActionPacket(int action) {
    public static void encode(GunActionPacket m, FriendlyByteBuf b) {
        b.writeByte(m.action);
    }

    public static GunActionPacket decode(FriendlyByteBuf b) {
        return new GunActionPacket(b.readByte());
    }

    public static void handle(GunActionPacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            var p = ctx.get().getSender();
            if (p != null) ru.aiforge.realwar.handheld.Firearms.handle(p, m.action);
        });
        ctx.get().setPacketHandled(true);
    }
}
