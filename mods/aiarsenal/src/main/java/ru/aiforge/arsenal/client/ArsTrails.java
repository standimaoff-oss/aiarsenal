package ru.aiforge.arsenal.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import ru.aiforge.arsenal.core.Kind;
import ru.aiforge.arsenal.entity.IcbmEntity;
import ru.aiforge.arsenal.entity.MissileEntity;
import ru.aiforge.arsenal.entity.ShahedDrone;
import ru.aiforge.realwar.client.fx.TrailFx;

/**
 * Client-only exhaust / launch effects of the arsenal units, rendered with the Real War particle system (TrailFx):
 * launch blast + dust on the first tick of a boosted flight, thick solid-motor smoke while a booster burns, a light
 * contrail for turbofan cruise flight and a plasma trail for the re-entry vehicle.
 */
public final class ArsTrails {
    private ArsTrails() {
    }

    private static ClientLevel level() {
        return Minecraft.getInstance().level;
    }

    /** Nozzle offset behind the entity origin (half the real body length in metres = blocks). */
    private static double tail(Kind k) {
        return switch (k) {
            case KH101 -> 3.7;
            case KH55, KALIBR -> 3.1;
            case TOMAHAWK -> 3.0;
            case HELLFIRE, KAB50 -> 0.8;
            case YARS -> 4.0;
            default -> 1.5;
        };
    }

    private static Vec3 dir(Vec3 a, Vec3 b, float yaw, float pitch) {
        Vec3 d = b.subtract(a);
        if (d.lengthSqr() > 1e-6) return d.normalize();
        return Vec3.directionFromRotation(pitch, yaw);
    }

    public static void missile(MissileEntity e) {
        ClientLevel l = level();
        if (l == null) return;
        Vec3 a = new Vec3(e.xo, e.yo, e.zo), b = e.position();
        Kind k = e.kind();
        int st = e.state();
        boolean booster = e.booster();
        if (e.tickCount == 2 && st == MissileEntity.BOOST)
            TrailFx.launch(l, b, dir(a, b, e.getYRot(), e.getXRot()), booster ? 1 : 0, 1.5);
        if (booster || (st == MissileEntity.BOOST && k == Kind.HELLFIRE)) {
            TrailFx.trail(l, e.getId(), a, b, k == Kind.HELLFIRE ? 0.55 : 1.0, tail(k) + (booster ? 0.9 : 0), true, false);
        } else if (k == Kind.HELLFIRE && st != MissileEntity.DROP) {
            TrailFx.trail(l, e.getId(), a, b, 0.45, tail(k), true, false);
        } else if (e.engine()) {
            TrailFx.engineWisp(l, b.subtract(dir(a, b, e.getYRot(), e.getXRot()).scale(tail(k))), dir(a, b, e.getYRot(), e.getXRot()), 1.0);
        }
    }

    public static void icbm(IcbmEntity e) {
        ClientLevel l = level();
        if (l == null) return;
        Vec3 a = new Vec3(e.xo, e.yo, e.zo), b = e.position();
        int st = e.state();
        if (st == IcbmEntity.EJECT && e.tickCount < 4)
            TrailFx.launch(l, b, new Vec3(0, 1, 0), 2, 8.0);          // mortar (gas-generator) ejection from the TPK
        if (st == IcbmEntity.BOOST) {
            int stages = Integer.bitCount(e.aux() & (IcbmEntity.B_S1 | IcbmEntity.B_S2 | IcbmEntity.B_S3));
            double s = stages >= 3 ? 3.0 : stages == 2 ? 2.2 : 1.5;
            TrailFx.trail(l, e.getId(), a, b, s, tail(Kind.YARS) * stages / 3.0 + 1.0, true, false);
        } else if (st == IcbmEntity.REENTRY) {
            TrailFx.trail(l, e.getId(), a, b, 0.8, 0.4, false, true);
        }
    }

    public static void shahed(ShahedDrone e) {
        ClientLevel l = level();
        if (l == null) return;
        Vec3 a = new Vec3(e.xo, e.yo, e.zo), b = e.position();
        int st = e.state();
        if (st == ShahedDrone.BOOST) {
            Vec3 d = dir(a, b, e.getYRot(), e.getXRot());
            if (e.tickCount < 4) TrailFx.launch(l, b, d, 0, 1.0);
            if ((e.aux() & ShahedDrone.B_BOOSTER) != 0) TrailFx.trail(l, e.getId(), a, b, 0.7, 1.2, true, false);
        } else if (st == ShahedDrone.CRUISE || st == ShahedDrone.DIVE) {
            Vec3 d = dir(a, b, e.getYRot(), e.getXRot());
            TrailFx.engineWisp(l, b.subtract(d.scale(1.4)), d, 0.6);
        }
    }
}
