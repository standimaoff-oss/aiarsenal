package ru.aiforge.arsenal.core;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import ru.aiforge.tubombers.fx.ImpactHandler;
import ru.aiforge.tubombers.network.FxPacket;

import javax.annotation.Nullable;

/** Conventional warheads of drones and missiles mapped onto the shared impact effects. */
public final class Warheads {
    public static int count;

    private Warheads() {
    }

    public static void detonate(ServerLevel sl, Vec3 p, Kind k, @Nullable Entity direct, @Nullable Entity owner, @Nullable Entity hit) {
        count++;
        if (ru.aiforge.arsenal.ArsenalConfig.REALWAR_HE.get() && heKg(k) > 0) {
            ru.aiforge.realwar.blast.ConventionalBlast.explode(sl, p, heKg(k), frag(k));
        } else switch (k) {
            // Kh-101: 400 kg HE / penetrating
            case KH101 -> ImpactHandler.warhead(sl, p, 6.5f, 5.0f, 24, 90, 100, FxPacket.HEAVY, direct, owner);
            case KH55, KALIBR, TOMAHAWK -> ImpactHandler.warhead(sl, p, 6.0f, 4.5f, 22, 85, 90, FxPacket.HEAVY, direct, owner);
            // AGM-114 9 kg HEAT, KAB-50 (UPAB-class) 22 kg HE
            case HELLFIRE -> ImpactHandler.warhead(sl, p, 2.8f, 1.0f, 6, 70, 30, FxPacket.LIGHT, direct, owner);
            case KAB50 -> ImpactHandler.warhead(sl, p, 3.5f, 2.0f, 9, 55, 50, FxPacket.LIGHT, direct, owner);
            // Shahed-136/238: 50 kg HE-frag with pre-formed fragments; Shahed-131: 15-20 kg; Liutyi 75 kg
            case S136, S238 -> ImpactHandler.warhead(sl, p, 5.5f, 3.0f, 16, 45, 140, FxPacket.MEDIUM, direct, owner);
            case S131 -> ImpactHandler.warhead(sl, p, 3.8f, 2.0f, 11, 35, 90, FxPacket.LIGHT, direct, owner);
            case LIUTYI -> ImpactHandler.warhead(sl, p, 6.0f, 3.5f, 18, 50, 150, FxPacket.MEDIUM, direct, owner);
            // FPV: PG-7VL shaped charge (2.6 kg) / TM-62M anti-tank mine (7.5 kg TNT)
            case FPV7 -> ImpactHandler.warhead(sl, p, 2.6f, 0.8f, 4, 60, 20, FxPacket.LIGHT, direct, owner);
            case FPV10 -> ImpactHandler.warhead(sl, p, 6.0f, 3.0f, 12, 50, 40, FxPacket.MEDIUM, direct, owner);
            default -> ImpactHandler.crash(sl, p, 3f, direct);
        }
        if (hit != null && hit.isAlive()) {
            // direct hit: shaped charge / penetrator bonus
            float bonus = switch (k) {
                case HELLFIRE -> 120f;
                case FPV7 -> 140f;
                case FPV10 -> 90f;
                case KH101, KH55, KALIBR, TOMAHAWK -> 150f;
                default -> 40f;
            };
            hit.hurt(sl.damageSources().explosion(direct, owner), bonus);
        }
    }

    /** TNT-equivalent warhead mass, kg (open sources). */
    public static double heKg(Kind k) {
        return switch (k) {
            case KH101 -> 400;           // Kh-101: 400 kg HE / penetrating
            case KH55 -> 410;            // Kh-55SM conventional variant (Kh-555)
            case KALIBR -> 450;          // 3M14 Kalibr
            case TOMAHAWK -> 450;        // BGM-109 WDU-36/B
            case HELLFIRE -> 9;          // AGM-114 HEAT
            case KAB50 -> 22;            // UPAB-class glide bomb
            case S136, S238 -> 50;       // Shahed/Geran HE-frag
            case S131 -> 18;
            case LIUTYI -> 75;
            case FPV7 -> 2.6;            // PG-7VL
            case FPV10 -> 7.5;           // TM-62M
            default -> 0;
        };
    }

    /** Fragmentation share (0 = pure blast / shaped charge, 1 = pre-formed fragments). */
    public static double frag(Kind k) {
        return switch (k) {
            case S136, S238, S131, LIUTYI -> 1.0;
            case KAB50, FPV10 -> 0.7;
            case KH101, KH55, KALIBR, TOMAHAWK -> 0.6;
            case HELLFIRE, FPV7 -> 0.25;
            default -> 0.5;
        };
    }
}
