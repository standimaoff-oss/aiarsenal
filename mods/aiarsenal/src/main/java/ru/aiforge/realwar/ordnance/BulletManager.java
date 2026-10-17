/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.core.BlockPos
 *  net.minecraft.core.Position
 *  net.minecraft.core.particles.ParticleOptions
 *  net.minecraft.core.particles.ParticleTypes
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.entity.LivingEntity
 *  net.minecraft.world.level.ClipContext
 *  net.minecraft.world.level.ClipContext$Block
 *  net.minecraft.world.level.ClipContext$Fluid
 *  net.minecraft.world.level.Level
 *  net.minecraft.world.level.LevelAccessor
 *  net.minecraft.world.phys.AABB
 *  net.minecraft.world.phys.BlockHitResult
 *  net.minecraft.world.phys.HitResult$Type
 *  net.minecraft.world.phys.Vec3
 *  net.minecraftforge.event.TickEvent$LevelTickEvent
 *  net.minecraftforge.event.TickEvent$Phase
 *  net.minecraftforge.event.level.LevelEvent$Unload
 *  net.minecraftforge.eventbus.api.SubscribeEvent
 *  net.minecraftforge.fml.common.Mod$EventBusSubscriber
 */
package ru.aiforge.realwar.ordnance;

import ru.aiforge.realwar.RWConfig;
import ru.aiforge.realwar.blast.ModDamage;
import ru.aiforge.realwar.ordnance.RocketEntity;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="aiarsenal")
public final class BulletManager {
    /** gametest diagnostics */
    public static final double DRAG = 0.985;
    public static final double GRAVITY = 0.02;
    public static final float DAMAGE = 5.0f;
    private static final int MAX_BULLETS = 3000;
    private static final Map<ServerLevel, List<Bullet>> BULLETS = new IdentityHashMap<ServerLevel, List<Bullet>>();

    private BulletManager() {
    }

    public static int lifeTicks() {
        double v0 = 10.0;
        double r = Math.min((double)((Integer)RWConfig.COMMON.flakRange.get()).intValue(), v0 / 0.015000000000000013 * 0.9);
        return (int)Math.ceil(Math.log(1.0 - r * 0.015000000000000013 / v0) / Math.log(0.985));
    }

    public static void spawn(ServerLevel level, Vec3 pos, Vec3 vel, int owner, boolean tracer) {
        List l = BULLETS.computeIfAbsent(level, k -> new ArrayList());
        if (l.size() >= 3000) {
            return;
        }
        Bullet b = new Bullet();
        b.pos = pos;
        b.vel = vel;
        b.owner = owner;
        b.tracer = tracer;
        l.add(b);
    }

    public static int count(ServerLevel level) {
        List<Bullet> l = BULLETS.get(level);
        return l == null ? 0 : l.size();
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent e) {
        Level level;
        if (e.phase != TickEvent.Phase.END || !((level = e.level) instanceof ServerLevel)) {
            return;
        }
        ServerLevel sl = (ServerLevel)level;
        List<Bullet> list = BULLETS.get(sl);
        if (list == null || list.isEmpty()) {
            return;
        }
        int life = BulletManager.lifeTicks();
        Iterator<Bullet> it = list.iterator();
        while (it.hasNext()) {
            Bullet b = it.next();
            if (BulletManager.step(sl, b, life)) continue;
            it.remove();
        }
    }

    @SubscribeEvent
    public static void onUnload(LevelEvent.Unload e) {
        LevelAccessor levelAccessor = e.getLevel();
        if (levelAccessor instanceof ServerLevel) {
            ServerLevel sl = (ServerLevel)levelAccessor;
            BULLETS.remove(sl);
        }
    }

    private static boolean step(ServerLevel sl, Bullet b, int life) {
        BlockHitResult bh;
        Vec3 from = b.pos;
        b.vel = new Vec3(b.vel.x * 0.985, b.vel.y * 0.985 - 0.02, b.vel.z * 0.985);
        Vec3 to = from.add(b.vel);
        ++b.age;
        double bestD = Double.MAX_VALUE;
        Entity hitEntity = null;
        Vec3 hitPos = null;
        if (sl.hasChunkAt(BlockPos.containing((Position)to)) && (bh = sl.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null))).getType() != HitResult.Type.MISS) {
            bestD = bh.getLocation().distanceToSqr(from);
            hitPos = bh.getLocation();
        }
        AABB box = new AABB(from, to).inflate(0.6);
        // r20: vehicles by their real oriented shape (square entity AABBs miss the bow and the stern)
        for (Entity e : sl.getEntities((Entity)null, box.inflate(20.0), x -> x instanceof ru.aiforge.realwar.vehicle.VehicleEntity && x.isAlive())) {
            if (e.getId() == b.owner || e.getVehicle() != null && e.getVehicle().getId() == b.owner) continue;
            Vec3 c = ru.aiforge.realwar.damage.VehicleHit.clip((ru.aiforge.realwar.vehicle.VehicleEntity) e, from, to);
            if (c == null) continue;
            double d = c.distanceToSqr(from);
            if (d < bestD) {
                bestD = d;
                hitEntity = e;
                hitPos = c;
            }
        }
        for (Entity e : sl.getEntities((Entity)null, box, x -> !(x instanceof ru.aiforge.realwar.vehicle.VehicleEntity) && x.isAlive() && !x.isSpectator() && (x.isPickable() || x instanceof LivingEntity) && !(x instanceof RocketEntity))) {
            double d;
            Optional c;
            if (e.getId() == b.owner || e.getVehicle() != null && e.getVehicle().getId() == b.owner || !(c = e.getBoundingBox().inflate(0.25).clip(from, to)).isPresent() || !((d = ((Vec3)c.get()).distanceToSqr(from)) < bestD)) continue;
            bestD = d;
            hitEntity = e;
            hitPos = (Vec3)c.get();
        }
        if (hitPos != null) {
            if (hitEntity != null) {
                hitEntity.invulnerableTime = 0;
                ru.aiforge.realwar.damage.HitContext.hurt(hitEntity, ModDamage.source((Level)sl, ModDamage.BULLET), 5.0f, hitPos, b.vel.normalize(), ru.aiforge.realwar.damage.HitContext.Kind.BULLET, 5.0);
                hitEntity.invulnerableTime = 0;
                sl.sendParticles((ParticleOptions)ParticleTypes.CRIT, hitPos.x, hitPos.y, hitPos.z, 3, 0.1, 0.1, 0.1, 0.2);
            } else if (b.tracer || sl.random.nextInt(3) == 0) {
                sl.sendParticles((ParticleOptions)ru.aiforge.arsenal.volumetric.VolumetricParticles.SMOKE.get(), hitPos.x, hitPos.y, hitPos.z, 2, 0.1, 0.1, 0.1, 0.02);
            }
            return false;
        }
        b.pos = to;
        if (b.age >= life) {
            if (b.tracer) {
                sl.sendParticles((ParticleOptions)ParticleTypes.POOF, to.x, to.y, to.z, 2, 0.15, 0.15, 0.15, 0.01);
            }
            return false;
        }
        return true;
    }

    private static final class Bullet {
        Vec3 pos;
        Vec3 vel;
        int age;
        int owner;
        boolean tracer;

        private Bullet() {
        }
    }
}

