package ru.aiforge.bm21grad.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.network.NetworkEvent;
import ru.aiforge.bm21grad.ammo.RocketType;
import ru.aiforge.bm21grad.entity.Bm21Entity;

import java.util.function.Supplier;

/** Client -> server: crew command for a BM-21 (keys / fire-control screen). */
public record VehicleCommandPacket(int entityId, int action, int value, BlockPos pos) {
    public static final int FIRE = 0, CEASE = 1, RELOAD = 2, SHIELDS = 3, MODE = 4, AMMO = 5, LIGHTS = 6, TARGET = 7, CLEAR_TARGET = 8, GUNNER = 9, AIM = 10;

    public static VehicleCommandPacket of(Entity e, int action, int value) {
        return new VehicleCommandPacket(e.getId(), action, value, BlockPos.ZERO);
    }

    public static VehicleCommandPacket target(Entity e, BlockPos p) {
        return new VehicleCommandPacket(e.getId(), TARGET, 0, p);
    }

    public void encode(FriendlyByteBuf b) {
        b.writeVarInt(entityId);
        b.writeByte(action);
        b.writeVarInt(value);
        b.writeBlockPos(pos);
    }

    public static VehicleCommandPacket decode(FriendlyByteBuf b) {
        return new VehicleCommandPacket(b.readVarInt(), b.readByte(), b.readVarInt(), b.readBlockPos());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.setPacketHandled(true);
        if (c.getDirection().getReceptionSide() != LogicalSide.SERVER) return;
        ServerPlayer p = c.getSender();
        if (p == null) return;
        Entity e = p.level().getEntity(entityId);
        if (!(e instanceof Bm21Entity v) || !v.isAlive()) return;
        // the crew (passengers) or somebody standing next to the vehicle may command it
        if (p.getVehicle() != v && p.distanceToSqr(v) > 12 * 12) return;
        Component msg = switch (action) {
            case FIRE -> v.startFire(p);
            case CEASE -> v.ceaseFire();
            case RELOAD -> v.startReload(p.getAbilities().instabuild);
            case SHIELDS -> v.toggleShields();
            case MODE -> v.setFireMode(value);
            case AMMO -> v.setAmmoSelection(RocketType.byIndex(value));
            case LIGHTS -> v.toggleLights();
            case TARGET -> v.setTarget(value == 1 ? v.resolveGround(pos) : pos);
            case CLEAR_TARGET -> v.setTarget(null);
            case GUNNER -> v.toggleGunner();
            case AIM -> {
                v.aimDirect(Bm21Entity.unpackTurret(value), Bm21Entity.unpackElev(value));
                yield null;
            }
            default -> null;
        };
        if (msg != null) p.displayClientMessage(msg, true);
    }
}
