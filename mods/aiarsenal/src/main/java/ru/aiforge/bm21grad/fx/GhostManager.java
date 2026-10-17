package ru.aiforge.bm21grad.fx;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.aiforge.bm21grad.Bm21Grad;
import ru.aiforge.bm21grad.Config;
import ru.aiforge.bm21grad.ammo.RocketType;
import ru.aiforge.bm21grad.ballistics.Ballistics;
import ru.aiforge.bm21grad.entity.RocketEntity;
import ru.aiforge.bm21grad.registry.ModRegistry;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Keeps rockets flying through chunks that are not entity-ticking (far from players): the same
 * ballistic integration runs here; the rocket re-materialises as an entity as soon as it enters a
 * ticking chunk, or detonates on terrain (the impact chunk is loaded on demand, only near the ground).
 */
@Mod.EventBusSubscriber(modid = Bm21Grad.MODID)
public final class GhostManager {
    private static final int MAX_GHOSTS = 512;
    private static final List<Ghost> GHOSTS = new ArrayList<>();
    private static final List<Task> TASKS = new ArrayList<>();

    private GhostManager() {
    }

    private static final class Ghost {
        ResourceKey<Level> dim;
        RocketType type;
        double[] s;
        int age;
        double thrust;
        UUID owner, launcher;
        double groundHint;
        boolean whistled;
        int life;
    }

    private record Task(ResourceKey<Level> dim, long due, Vec3 pos, Vec3 vel, UUID owner) {
    }

    public static int count() {
        return GHOSTS.size();
    }

    public static void add(ServerLevel sl, RocketEntity r, Vec3 pos, Vec3 vel) {
        if (GHOSTS.size() >= MAX_GHOSTS) return;
        Ghost g = new Ghost();
        g.dim = sl.dimension();
        g.type = r.getRocketType();
        g.s = new double[]{pos.x, pos.y, pos.z, vel.x, vel.y, vel.z};
        g.age = r.age;
        g.thrust = r.thrust;
        g.owner = r.getOwner() != null ? r.getOwner().getUUID() : null;
        g.launcher = r.launcherId;
        g.groundHint = Double.isNaN(r.groundHint) ? sl.getSeaLevel() : r.groundHint;
        g.whistled = r.whistled;
        GHOSTS.add(g);
    }

    public static void scheduleBomblet(ServerLevel sl, Vec3 p, Vec3 v, Entity owner, int delay) {
        TASKS.add(new Task(sl.dimension(), sl.getGameTime() + delay, p, v, owner != null ? owner.getUUID() : null));
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.level.isClientSide || !(e.level instanceof ServerLevel sl)) return;
        if (!GHOSTS.isEmpty()) tickGhosts(sl);
        if (!TASKS.isEmpty()) tickTasks(sl);
    }

    @SubscribeEvent
    public static void onStop(ServerStoppingEvent e) {
        GHOSTS.clear();
        TASKS.clear();
    }

    private static void tickGhosts(ServerLevel sl) {
        int maxLife = Config.MAX_GHOST_TICKS.get();
        for (Iterator<Ghost> it = GHOSTS.iterator(); it.hasNext(); ) {
            Ghost g = it.next();
            if (g.dim != sl.dimension()) continue;
            if (++g.life > maxLife) {
                it.remove();
                continue;
            }
            Vec3 from = new Vec3(g.s[0], g.s[1], g.s[2]);
            Ballistics.step(g.s, g.age++, g.thrust);
            Vec3 to = new Vec3(g.s[0], g.s[1], g.s[2]);
            Vec3 vel = new Vec3(g.s[3], g.s[4], g.s[5]);
            if (to.y < sl.getMinBuildHeight() - 32) {
                it.remove();
                continue;
            }
            BlockPos bp = BlockPos.containing(to);
            if (sl.isPositionEntityTicking(bp)) {
                RocketEntity r = new RocketEntity(ModRegistry.ROCKET.get(), sl);
                r.setRocketType(g.type);
                r.setPos(to.x, to.y, to.z);
                r.setDeltaMovement(vel);
                r.thrust = g.thrust;
                r.age = g.age;
                r.launcherId = g.launcher;
                r.groundHint = g.groundHint;
                r.whistled = g.whistled;
                r.roared = true;
                if (g.owner != null) r.setOwner(sl.getEntity(g.owner));
                r.orient();
                sl.addFreshEntity(r);
                it.remove();
                continue;
            }
            if (vel.y >= 0 || to.y > g.groundHint + 48 || !sl.isInWorldBounds(bp)) continue;
            // near the ground: make sure terrain exists, then test for impact
            sl.getChunk(bp.getX() >> 4, bp.getZ() >> 4);
            Entity owner = g.owner != null ? sl.getEntity(g.owner) : null;
            if (g.type == RocketType.CLUSTER) {
                int ground = sl.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(to.x), Mth.floor(to.z));
                if (to.y - ground < 38) {
                    ImpactHandler.clusterBurst(sl, to, vel, null, owner);
                    it.remove();
                    continue;
                }
            }
            BlockHitResult hit = sl.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, null));
            if (hit.getType() != HitResult.Type.MISS) {
                Vec3 p = hit.getLocation().add(Vec3.atLowerCornerOf(hit.getDirection().getNormal()).scale(0.3));
                ImpactHandler.detonate(sl, p, g.type, null, owner, vel);
                it.remove();
            }
        }
    }

    private static void tickTasks(ServerLevel sl) {
        long now = sl.getGameTime();
        for (Iterator<Task> it = TASKS.iterator(); it.hasNext(); ) {
            Task t = it.next();
            if (t.dim != sl.dimension() || t.due > now) continue;
            it.remove();
            Vec3 p = t.pos, v = t.vel;
            Entity owner = t.owner != null ? sl.getEntity(t.owner) : null;
            for (int i = 0; i < 300; i++) {
                v = v.scale(0.96).add(0, -0.05, 0);
                Vec3 n = p.add(v);
                BlockPos bp = BlockPos.containing(n);
                if (!sl.isInWorldBounds(bp)) break;
                sl.getChunk(bp.getX() >> 4, bp.getZ() >> 4);
                BlockHitResult hit = sl.clip(new ClipContext(p, n, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, null));
                if (hit.getType() != HitResult.Type.MISS) {
                    ImpactHandler.bomblet(sl, hit.getLocation().add(0, 0.2, 0), null, owner);
                    break;
                }
                p = n;
            }
        }
    }
}
