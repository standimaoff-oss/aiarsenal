/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.resources.ResourceLocation
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.server.level.ServerPlayer
 *  net.minecraftforge.network.NetworkRegistry
 *  net.minecraftforge.network.PacketDistributor
 *  net.minecraftforge.network.PacketDistributor$TargetPoint
 *  net.minecraftforge.network.simple.SimpleChannel
 */
package ru.aiforge.realwar.network;

import ru.aiforge.realwar.aircraft.AircraftControlPacket;
import ru.aiforge.realwar.aircraft.AircraftInteractPacket;
import ru.aiforge.realwar.missile.ComputerActionPacket;
import ru.aiforge.realwar.network.ConventionalEffectPacket;
import ru.aiforge.realwar.network.NukeEffectPacket;
import ru.aiforge.realwar.network.RadiationSyncPacket;
import ru.aiforge.realwar.network.SetTargetPacket;
import ru.aiforge.realwar.network.TracerPacket;
import ru.aiforge.realwar.network.VehicleControlPacket;
import ru.aiforge.realwar.network.VehicleInteractPacket;
import ru.aiforge.realwar.network.VehicleSnapPacket;
import ru.aiforge.realwar.network.VehicleStatePacket;
import ru.aiforge.realwar.radar.RadarFirePacket;
import ru.aiforge.realwar.radar.RadarQueryPacket;
import ru.aiforge.realwar.radar.RadarReplyPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetwork {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel((ResourceLocation)new ResourceLocation("aiarsenal", "realwar"), () -> "1", "1"::equals, "1"::equals);

    private ModNetwork() {
    }

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, NukeEffectPacket.class, NukeEffectPacket::encode, NukeEffectPacket::decode, NukeEffectPacket::handle);
        CHANNEL.registerMessage(id++, ConventionalEffectPacket.class, ConventionalEffectPacket::encode, ConventionalEffectPacket::decode, ConventionalEffectPacket::handle);
        CHANNEL.registerMessage(id++, RadiationSyncPacket.class, RadiationSyncPacket::encode, RadiationSyncPacket::decode, RadiationSyncPacket::handle);
        CHANNEL.registerMessage(id++, TracerPacket.class, TracerPacket::encode, TracerPacket::decode, TracerPacket::handle);
        CHANNEL.registerMessage(id++, VehicleControlPacket.class, VehicleControlPacket::encode, VehicleControlPacket::decode, VehicleControlPacket::handle);
        CHANNEL.registerMessage(id++, VehicleStatePacket.class, VehicleStatePacket::encode, VehicleStatePacket::decode, VehicleStatePacket::handle);
        CHANNEL.registerMessage(id++, VehicleSnapPacket.class, VehicleSnapPacket::encode, VehicleSnapPacket::decode, VehicleSnapPacket::handle);
        CHANNEL.registerMessage(id++, VehicleInteractPacket.class, VehicleInteractPacket::encode, VehicleInteractPacket::decode, VehicleInteractPacket::handle);
        CHANNEL.registerMessage(id++, SetTargetPacket.class, SetTargetPacket::encode, SetTargetPacket::decode, SetTargetPacket::handle);
        CHANNEL.registerMessage(id++, ComputerActionPacket.class, ComputerActionPacket::encode, ComputerActionPacket::decode, ComputerActionPacket::handle);
        CHANNEL.registerMessage(id++, AircraftControlPacket.class, AircraftControlPacket::encode, AircraftControlPacket::decode, AircraftControlPacket::handle);
        CHANNEL.registerMessage(id++, AircraftInteractPacket.class, AircraftInteractPacket::encode, AircraftInteractPacket::decode, AircraftInteractPacket::handle);
        CHANNEL.registerMessage(id++, RadarQueryPacket.class, RadarQueryPacket::encode, RadarQueryPacket::decode, RadarQueryPacket::handle);
        CHANNEL.registerMessage(id++, RadarReplyPacket.class, RadarReplyPacket::encode, RadarReplyPacket::decode, RadarReplyPacket::handle);
        CHANNEL.registerMessage(id++, RadarFirePacket.class, RadarFirePacket::encode, RadarFirePacket::decode, RadarFirePacket::handle);
        CHANNEL.registerMessage(id++, ru.aiforge.realwar.craft.CraftStatePacket.class, ru.aiforge.realwar.craft.CraftStatePacket::encode, ru.aiforge.realwar.craft.CraftStatePacket::decode, ru.aiforge.realwar.craft.CraftStatePacket::handle);
        CHANNEL.registerMessage(id++, ru.aiforge.realwar.craft.CraftControlPacket.class, ru.aiforge.realwar.craft.CraftControlPacket::encode, ru.aiforge.realwar.craft.CraftControlPacket::decode, ru.aiforge.realwar.craft.CraftControlPacket::handle);
        CHANNEL.registerMessage(id++, ru.aiforge.realwar.craft.CraftSnapPacket.class, ru.aiforge.realwar.craft.CraftSnapPacket::encode, ru.aiforge.realwar.craft.CraftSnapPacket::decode, ru.aiforge.realwar.craft.CraftSnapPacket::handle);
        CHANNEL.registerMessage(id++, ru.aiforge.realwar.radar.OverviewRequestPacket.class, ru.aiforge.realwar.radar.OverviewRequestPacket::encode, ru.aiforge.realwar.radar.OverviewRequestPacket::decode, ru.aiforge.realwar.radar.OverviewRequestPacket::handle);
        CHANNEL.registerMessage(id++, ru.aiforge.realwar.radar.OverviewTilePacket.class, ru.aiforge.realwar.radar.OverviewTilePacket::encode, ru.aiforge.realwar.radar.OverviewTilePacket::decode, ru.aiforge.realwar.radar.OverviewTilePacket::handle);
        CHANNEL.registerMessage(id++, ru.aiforge.realwar.radar.OverviewVillagesPacket.class, ru.aiforge.realwar.radar.OverviewVillagesPacket::encode, ru.aiforge.realwar.radar.OverviewVillagesPacket::decode, ru.aiforge.realwar.radar.OverviewVillagesPacket::handle);
        CHANNEL.registerMessage(id++, ru.aiforge.realwar.strike.StrikeRequestPacket.class, ru.aiforge.realwar.strike.StrikeRequestPacket::encode, ru.aiforge.realwar.strike.StrikeRequestPacket::decode, ru.aiforge.realwar.strike.StrikeRequestPacket::handle);
        CHANNEL.registerMessage(id++, HitCamPacket.class, HitCamPacket::encode, HitCamPacket::decode, HitCamPacket::handle);
        CHANNEL.registerMessage(id++, SelectWeaponPacket.class, SelectWeaponPacket::encode, SelectWeaponPacket::decode, SelectWeaponPacket::handle);
        CHANNEL.registerMessage(id++, GunActionPacket.class, GunActionPacket::encode, GunActionPacket::decode, GunActionPacket::handle);
        CHANNEL.registerMessage(id++, GunFxPacket.class, GunFxPacket::encode, GunFxPacket::decode, GunFxPacket::handle);
    }

    public static void sendToDimension(ServerLevel level, Object msg) {
        CHANNEL.send(PacketDistributor.DIMENSION.with(() -> ((ServerLevel)level).dimension()), msg);
    }

    public static void sendNear(ServerLevel level, double x, double y, double z, double radius, Object msg) {
        CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(x, y, z, radius, level.dimension())), msg);
    }

    public static void sendToPlayer(ServerPlayer p, Object msg) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), msg);
    }
}

