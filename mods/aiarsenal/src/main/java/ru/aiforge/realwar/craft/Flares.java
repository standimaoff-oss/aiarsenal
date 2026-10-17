package ru.aiforge.realwar.craft;

import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * IR decoy logic. Each burning flare inside the IR seeker's field of view (12 deg around the line of sight to the
 * target) gets ONE chance to seduce a given missile: 60 % (two-colour Igla-S style seekers reject the rest).
 * Once seduced the missile follows the falling flare and self-destructs when it burns out.
 */
public final class Flares {
    public static final double SEDUCE_CHANCE = 0.6;
    private static final WeakHashMap<Entity, Set<Integer>> TRIED = new WeakHashMap<>();
    /** Test hook: force the roll result (null = random). */
    public static Boolean forceResult;
    public static int seduced;

    private Flares() {}

    @Nullable
    public static Entity decoyFor(Entity missile, Entity target) {
        Vec3 m = missile.position();
        Vec3 toT = target.getBoundingBox().getCenter().subtract(m);
        double dT = toT.length();
        if (dT < 1e-3) return null;
        Vec3 los = toT.scale(1 / dT);
        Set<Integer> tried = TRIED.computeIfAbsent(missile, k -> new HashSet<>());
        for (Entity e : missile.level().getEntities(missile, target.getBoundingBox().inflate(60),
                x -> x instanceof CraftOrdnance o && o.isHotFlare())) {
            if (!tried.add(e.getId())) continue;
            Vec3 toF = e.position().subtract(m);
            double dF = toF.length();
            if (dF < 1e-3 || toF.scale(1 / dF).dot(los) < Math.cos(Math.toRadians(12))) continue;
            boolean ok = forceResult != null ? forceResult : missile.level().random.nextDouble() < SEDUCE_CHANCE;
            if (ok) {
                seduced++;
                return e;
            }
        }
        return null;
    }
}
