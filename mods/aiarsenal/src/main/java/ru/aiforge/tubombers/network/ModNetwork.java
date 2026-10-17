package ru.aiforge.tubombers.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import ru.aiforge.tubombers.TuBombers;

/** The mod's single SimpleChannel. */
public final class ModNetwork {
    public static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(TuBombers.MODID, "aviation"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private ModNetwork() {
    }

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(PilotInputPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(PilotInputPacket::encode).decoder(PilotInputPacket::decode).consumerMainThread(PilotInputPacket::handle).add();
        CHANNEL.messageBuilder(CrewActionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CrewActionPacket::encode).decoder(CrewActionPacket::decode).consumerMainThread(CrewActionPacket::handle).add();
        CHANNEL.messageBuilder(CrewMovePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CrewMovePacket::encode).decoder(CrewMovePacket::decode).consumerMainThread(CrewMovePacket::handle).add();
        CHANNEL.messageBuilder(CrewSyncPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(CrewSyncPacket::encode).decoder(CrewSyncPacket::decode).consumerMainThread(CrewSyncPacket::handle).add();
        CHANNEL.messageBuilder(FxPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(FxPacket::encode).decoder(FxPacket::decode).consumerMainThread(FxPacket::handle).add();
    }
}
