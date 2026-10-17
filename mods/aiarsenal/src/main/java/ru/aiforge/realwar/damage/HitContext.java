package ru.aiforge.realwar.damage;

import javax.annotation.Nullable;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * r18: where and how a vehicle was hit. The weapon sets it around its {@code hurt()} call (same server thread), the
 * vehicle's module damage model reads it. Without a context the damage model falls back to a hit at a random point.
 */
public final class HitContext {
    public enum Kind { AP, HE, PROX, BULLET, BLAST }

    /** pos = world impact point, dir = flight direction (unit or zero), power: AP = damage, HE / BLAST = TNT kg,
     *  PROX = warhead damage at zero distance, BULLET = damage. */
    public record Hit(Vec3 pos, Vec3 dir, Kind kind, double power) {}

    private static final ThreadLocal<Hit> CUR = new ThreadLocal<>();

    private HitContext() {
    }

    @Nullable
    public static Hit get() {
        return CUR.get();
    }

    public static boolean hurt(Entity e, DamageSource src, float amount, Vec3 pos, @Nullable Vec3 dir, Kind kind, double power) {
        Hit prev = CUR.get();
        CUR.set(new Hit(pos, dir == null ? Vec3.ZERO : dir, kind, power));
        try {
            return e.hurt(src, amount);
        } finally {
            CUR.set(prev);
        }
    }

    /**
     * r20: the projectile behind a hit, for the armour / penetration model and the shooter's hit camera.
     * shooter = entity id of the firing vehicle, craft or player (-1 unknown), speed = impact speed (m/s),
     * origin = launch point (distance on the hit camera).
     */
    public record Shot(int shooter, Armour.Ammo ammo, double speed, Vec3 origin) {}

    private static final ThreadLocal<Shot> SHOT = new ThreadLocal<>();

    @Nullable
    public static Shot shot() {
        return SHOT.get();
    }

    public static boolean hurt(Entity e, DamageSource src, float amount, Vec3 pos, @Nullable Vec3 dir, Kind kind, double power, @Nullable Shot shot) {
        Shot prev = SHOT.get();
        SHOT.set(shot);
        try {
            return hurt(e, src, amount, pos, dir, kind, power);
        } finally {
            SHOT.set(prev);
        }
    }
}
