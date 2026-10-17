package ru.aiforge.bm21grad.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import ru.aiforge.bm21grad.Bm21Grad;

/** The mod's single SimpleChannel. */
public final class ModNetwork {
    public static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(Bm21Grad.MODID, "bm21"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private ModNetwork() {
    }

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(VehicleCommandPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(VehicleCommandPacket::encode).decoder(VehicleCommandPacket::decode)
                .consumerMainThread(VehicleCommandPacket::handle).add();
        CHANNEL.messageBuilder(FxPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(FxPacket::encode).decoder(FxPacket::decode)
                .consumerMainThread(FxPacket::handle).add();
    }
}
