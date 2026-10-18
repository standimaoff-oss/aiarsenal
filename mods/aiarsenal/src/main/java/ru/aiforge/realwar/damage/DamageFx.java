package ru.aiforge.realwar.damage;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import ru.aiforge.realwar.craft.CraftEntity;
import ru.aiforge.realwar.vehicle.VehicleEntity;

import static ru.aiforge.realwar.damage.DamageState.*;

/** r18: client-side damage effects (engine smoke, fire, burning wrecks) - plain level particles, common code. */
public final class DamageFx {
    private DamageFx() {
    }

    public static void vehicle(VehicleEntity v) {
        DamageState s = v.dmg;
        if (!s.any()) return;
        Level l = v.level();
        RandomSource r = l.random;
        int t = v.tickCount;
        VehicleDamage.Geo g = VehicleDamage.geo(v);
        double ez = g.engineFront() ? g.cz() + g.hz() * 0.6 : g.cz() - g.hz() * 0.7;
        Vec3 eng = v.localToWorld(g.cx(), g.cy() + g.hy() + 0.1, ez);
        if (s.hurtBit(ENGINE) && t % 3 == 0) puff(l, ru.aiforge.arsenal.volumetric.VolumetricParticles.SMOKE.get(), eng, r, 0.4, 0.05);
        if (s.broken(ENGINE) && t % 2 == 0) puff(l, ru.aiforge.arsenal.volumetric.VolumetricParticles.SMOKE.get(), eng, r, 0.4, 0.06);
        if (s.burning()) {
            // r32: the fire body is a volumetric flame volume; a few flat sparks stay for detail
            if (t % 2 == 0) puff(l, ru.aiforge.arsenal.volumetric.VolumetricParticles.FIRE.get(), eng, r, 0.5, 0.5);
            puff(l, ParticleTypes.FLAME, eng, r, 0.6, 0.03);
            if (t % 3 == 0) puff(l, ru.aiforge.arsenal.volumetric.VolumetricParticles.SMOKE.get(), eng.add(0, 0.5, 0), r, 0.4, 0.07);
            if (r.nextInt(6) == 0) puff(l, ParticleTypes.LAVA, eng, r, 0.5, 0);
        }
        if (s.dead()) {
            Vec3 top = g.yp() != null ? v.localToWorld(g.yp()[0], g.yp()[1] + 0.3, g.yp()[2]) : v.localToWorld(0, g.cy() + g.hy(), g.cz());
            if (t % 3 == 0) puff(l, ru.aiforge.arsenal.volumetric.VolumetricParticles.SMOKE.get(), top, r, 0.5, 0.05);
            if (s.broken(TURRET_GONE) && s.burning()) {
                if (t % 2 == 0) puff(l, ru.aiforge.arsenal.volumetric.VolumetricParticles.FIRE.get(), top, r, 0.8, 0.6);
                puff(l, ParticleTypes.FLAME, top, r, 0.6, 0.06);
            }
        }
        if (s.repairMod >= 0 && !s.burning() && t % 8 == 0 && Math.abs(v.speedMs) < 0.3) {
            Vec3 p = v.localToWorld((r.nextDouble() - 0.5) * g.hx() * 2, g.cy() + g.hy(), g.cz() + (r.nextDouble() - 0.5) * g.hz() * 2);
            l.addParticle(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 0, 0.05, 0);
        }
    }

    public static void craft(CraftEntity c) {
        DamageState s = c.dmg;
        if (!s.any()) return;
        Level l = c.level();
        RandomSource r = l.random;
        int t = c.tickCount;
        Vec3 eng = c.localToWorld(CraftDamage.enginePoint(c));
        boolean big = c.kind.water != null && c.kind.water.length() > 60;
        double sp = big ? 2.5 : 0.4;
        if (s.hurtBit(ENGINE) && t % 2 == 0) puff(l, ru.aiforge.arsenal.volumetric.VolumetricParticles.SMOKE.get(), eng, r, sp, 0.04);
        if (s.broken(ENGINE)) puff(l, big ? ru.aiforge.arsenal.volumetric.VolumetricParticles.SMOKE.get() : ru.aiforge.arsenal.volumetric.VolumetricParticles.SMOKE.get(), eng, r, sp, 0.05);
        if (s.burning()) {
            Vec3 fp = c.localToWorld(CraftDamage.firePoint(c));
            if (t % 2 == 0) puff(l, ru.aiforge.arsenal.volumetric.VolumetricParticles.FIRE.get(), fp, r, big ? 2.2 : 0.6, big ? 0.9 : 0.5);
            for (int i = 0; i < (big ? 4 : 1); i++) puff(l, ParticleTypes.FLAME, fp, r, big ? 3.0 : 0.5, 0.04);
            puff(l, ru.aiforge.arsenal.volumetric.VolumetricParticles.SMOKE.get(), fp, r, big ? 3.0 : 0.5, 0.08);
            if (t % 3 == 0) puff(l, ru.aiforge.arsenal.volumetric.VolumetricParticles.SMOKE.get(), fp, r, big ? 3.0 : 0.3, 0.1);
        }
        for (int i = 0; i < N_GUNS; i++) {
            if (s.broken(GUN0 + i) && t % 4 == 0) {
                Vec3 p = CraftDamage.gunPoint(c, i);
                if (p != null) puff(l, ru.aiforge.arsenal.volumetric.VolumetricParticles.SMOKE.get(), c.localToWorld(p), r, 0.8, 0.05);
            }
        }
        if (s.broken(WING_L) && t % 2 == 0) puff(l, ru.aiforge.arsenal.volumetric.VolumetricParticles.SMOKE.get(), c.localToWorld(CraftDamage.wingRoot(c, true)), r, 0.2, 0.0);
        if (s.broken(WING_R) && t % 2 == 0) puff(l, ru.aiforge.arsenal.volumetric.VolumetricParticles.SMOKE.get(), c.localToWorld(CraftDamage.wingRoot(c, false)), r, 0.2, 0.0);
        if ((s.broken(ROTOR) || s.broken(TAIL_ROTOR)) && t % 2 == 0) puff(l, ru.aiforge.arsenal.volumetric.VolumetricParticles.SMOKE.get(), eng, r, 0.4, 0.05);
    }

    static void puff(Level l, ParticleOptions p, Vec3 c, RandomSource r, double spread, double up) {
        l.addParticle(p, c.x + (r.nextDouble() - 0.5) * spread, c.y + (r.nextDouble() - 0.5) * spread * 0.5, c.z + (r.nextDouble() - 0.5) * spread,
                (r.nextDouble() - 0.5) * 0.02, up, (r.nextDouble() - 0.5) * 0.02);
    }
}
