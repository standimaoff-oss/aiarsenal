package ru.aiforge.bm21grad.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import ru.aiforge.bm21grad.Bm21Grad;
import ru.aiforge.bm21grad.ammo.RocketType;
import ru.aiforge.bm21grad.ballistics.Ballistics;
import ru.aiforge.bm21grad.entity.Bm21Entity;
import ru.aiforge.bm21grad.entity.RocketEntity;
import ru.aiforge.bm21grad.entity.SmokeScreenEntity;
import ru.aiforge.bm21grad.fx.ImpactHandler;
import ru.aiforge.bm21grad.glb.GlbModel;
import ru.aiforge.bm21grad.glb.GlbModels;

/** Logic tests (run: ./gradlew runGameTestServer). Structure: data/bm21grad/structures/empty.nbt (9x6x9, stone floor). */
@GameTestHolder(Bm21Grad.MODID)
@PrefixGameTestTemplate(false)
public final class Bm21GameTests {
    private static final String T = "empty";

    private Bm21GameTests() {
    }

    private static Bm21Entity spawnVehicle(GameTestHelper h) {
        Vec3 p = h.absoluteVec(new Vec3(4.5, 1, 4.5));
        Bm21Entity v = Bm21Entity.create(h.getLevel(), p.x, p.y, p.z, 0);
        h.getLevel().addFreshEntity(v);
        return v;
    }

    /** F-GLB: both GLB files parse; hierarchy, 40 tube nodes, all animation clips present. */
    @GameTest(template = T)
    public static void glbModelsLoad(GameTestHelper h) {
        GlbModel m = GlbModels.get(GlbModels.BM21);
        h.assertTrue(m.nodes.size() >= 60, "bm21 nodes " + m.nodes.size());
        for (int i = 0; i < Bm21Entity.TUBES; i++) h.assertTrue(Bm21Entity.rocketNode(m, i) >= 0, "tube node " + i);
        for (String a : new String[]{"wheels_roll", "steer", "turret_traverse", "launcher_elevate", "shields_lower", "fire_recoil", "engine_idle"})
            h.assertTrue(m.anim(a) != null, "missing clip " + a);
        GlbModel r = GlbModels.get(GlbModels.ROCKET);
        h.assertTrue(r.anim("spin") != null && r.anim("fins_deploy") != null, "rocket clips");
        h.assertTrue(m.triangleCount > 10000, "triangles " + m.triangleCount);
        h.succeed();
    }

    /** F-ANIM: tube direction computed from the sampled launcher_elevate/turret_traverse clips matches the commanded angles. */
    @GameTest(template = T)
    public static void animationDrivesTubeDirection(GameTestHelper h) {
        Bm21Entity v = spawnVehicle(h);
        float[][] cases = {{0, 0}, {0, 30}, {90, 45}, {-120, 20}, {180, 55}};
        for (float[] c : cases) {
            v.snapLauncher(c[0], c[1]);
            Vec3 d = v.tubeMuzzle(0)[1];
            double elev = Math.toDegrees(Math.asin(d.y));
            double yaw = Math.toDegrees(Math.atan2(-d.x, d.z));
            double wantYaw = v.getYRot() + c[0];
            h.assertTrue(Math.abs(elev - c[1]) < 0.5, "elev " + elev + " vs " + c[1]);
            h.assertTrue(Math.abs(net.minecraft.util.Mth.wrapDegrees(yaw - wantYaw)) < 0.5, "yaw " + yaw + " vs " + wantYaw);
        }
        v.discard();
        h.succeed();
    }

    /** F-BALLISTICS: the solver's elevation, flown by the same integrator, lands within 1 block. */
    @GameTest(template = T)
    public static void ballisticSolutionHits(GameTestHelper h) {
        double thrust = Ballistics.thrust(1, 1);
        for (double dist : new double[]{80, 200, 400, 600, 740}) {
            Ballistics.Solution s = Ballistics.solve(dist, 0, thrust, 0);
            h.assertTrue(s.ok(), "no solution for " + dist);
            Ballistics.Shot shot = Ballistics.simulate(s.elevationDeg(), thrust, 0, 0);
            h.assertTrue(Math.abs(shot.horizontalDistance() - dist) < 1.0, "miss " + shot.horizontalDistance() + " vs " + dist);
        }
        h.assertTrue(!Ballistics.solve(5000, 0, thrust, 0).ok(), "5000 must be out of range");
        h.assertTrue(Ballistics.mils(0).equals("30-00"), "south = 30-00, got " + Ballistics.mils(0));
        h.succeed();
    }

    /** F-RELOAD: rockets in the rack are loaded into the tubes one by one. */
    @GameTest(template = T, timeoutTicks = 300)
    public static void reloadFromRack(GameTestHelper h) {
        Bm21Entity v = spawnVehicle(h);
        v.addToRack(RocketType.INCENDIARY, 3);
        v.setAmmoSelection(RocketType.INCENDIARY);
        v.startReload(false);
        h.succeedWhen(() -> {
            h.assertTrue(v.loadedCount(RocketType.INCENDIARY) >= 2, "loaded " + v.loadedCount());
            h.assertTrue(v.rackCount(RocketType.INCENDIARY) <= 1, "rack " + v.rackCount(RocketType.INCENDIARY));
            v.discard();
        });
    }

    /** F-FIRE: a fire mission lays the launcher, lowers the shield and launches real rocket entities. */
    @GameTest(template = T, timeoutTicks = 400)
    public static void fireMissionLaunches(GameTestHelper h) {
        Bm21Entity v = spawnVehicle(h);
        v.loadAll(RocketType.SMOKE);
        v.setFireMode(2);
        BlockPos tgt = BlockPos.containing(v.getX(), v.getY() - 1, v.getZ() - 250); // behind the vehicle (north)
        v.setTarget(tgt);
        v.startFire(null);
        h.succeedWhen(() -> {
            h.assertTrue(v.loadedCount() <= 38, "still loaded " + v.loadedCount());
            h.assertTrue(v.hasState(Bm21Entity.ST_SHIELDS), "shield not lowered");
            h.assertTrue(v.getElevation() > 5, "not elevated");
            v.discard();
        });
    }

    /** F-ROCKET: a flying rocket entity detonates on impact. */
    @GameTest(template = T, timeoutTicks = 200)
    public static void rocketDetonatesOnImpact(GameTestHelper h) {
        int before = ImpactHandler.detonations;
        Vec3 p = h.absoluteVec(new Vec3(4.5, 5, 4.5));
        RocketEntity r = RocketEntity.launch(h.getLevel(), RocketType.SMOKE, p, new Vec3(0, -1, 0), 0, null, null);
        r.age = 100;
        h.getLevel().addFreshEntity(r);
        h.succeedWhen(() -> h.assertTrue(ImpactHandler.detonations > before && r.isRemoved(), "no detonation"));
    }

    /** F-HE: HE-FRAG warhead breaks blocks and kills/damages a pig 3 blocks away with fragments. */
    @GameTest(template = T, timeoutTicks = 60)
    public static void heFragDamages(GameTestHelper h) {
        h.setBlock(new BlockPos(4, 1, 4), Blocks.DIRT);
        Pig pig = h.spawn(EntityType.PIG, new BlockPos(7, 1, 4));
        float hp = pig.getHealth();
        Vec3 c = h.absoluteVec(new Vec3(4.5, 2.2, 4.5));
        ImpactHandler.detonate(h.getLevel(), c, RocketType.HE_FRAG, null, null, new Vec3(0, -1, 0));
        h.runAfterDelay(2, () -> {
            h.assertBlockNotPresent(Blocks.DIRT, new BlockPos(4, 1, 4));
            h.assertTrue(!pig.isAlive() || pig.getHealth() < hp, "pig unharmed");
            h.succeed();
        });
    }

    /** F-SMOKE: smoke rocket creates a persistent smoke screen. */
    @GameTest(template = T, timeoutTicks = 60)
    public static void smokeScreenCreated(GameTestHelper h) {
        Vec3 c = h.absoluteVec(new Vec3(4.5, 1.2, 4.5));
        ImpactHandler.detonate(h.getLevel(), c, RocketType.SMOKE, null, null, new Vec3(0, -1, 0));
        h.runAfterDelay(2, () -> {
            h.assertTrue(!h.getLevel().getEntitiesOfClass(SmokeScreenEntity.class, new AABB(c, c).inflate(3)).isEmpty(), "no smoke");
            h.succeed();
        });
    }

    /** F-DAMAGE: enough explosive damage destroys the vehicle (wreck state, no passengers). */
    @GameTest(template = T, timeoutTicks = 100)
    public static void vehicleDestroyed(GameTestHelper h) {
        Bm21Entity v = spawnVehicle(h);
        h.runAfterDelay(1, () -> {
            v.hurt(v.damageSources().explosion(null, null), 10000);
            h.assertTrue(v.isDestroyed(), "not destroyed, hp " + v.getHealth());
            v.discard();
            h.succeed();
        });
    }

    /** F-GUNNER: gunner mode lowers the shield, blocks driving, lays the launcher on the streamed aim, survives a target designation, and stows on exit. */
    @GameTest(template = T, timeoutTicks = 400)
    public static void gunnerModeLaysLauncher(GameTestHelper h) {
        Bm21Entity v = spawnVehicle(h);
        v.loadAll(RocketType.HE_FRAG);
        int packed = Bm21Entity.packAim(-123.4f, 37.5f);
        h.assertTrue(Math.abs(Bm21Entity.unpackTurret(packed) + 123.4f) < 0.06f && Math.abs(Bm21Entity.unpackElev(packed) - 37.5f) < 0.06f, "aim packing");
        v.aimDirect(90, 30);
        h.assertTrue(!v.isGunnerMode(), "aim ignored outside gunner mode");
        v.toggleGunner();
        h.assertTrue(v.isGunnerMode() && v.hasState(Bm21Entity.ST_SHIELDS), "gunner mode + combat position");
        v.aimDirect(90, 30);
        v.setTarget(new BlockPos((int) v.getX() + 200, (int) v.getY() - 1, (int) v.getZ()));
        h.succeedWhen(() -> {
            h.assertTrue(Math.abs(net.minecraft.util.Mth.wrapDegrees(v.getTurret() - 90)) < 0.3f, "turret " + v.getTurret());
            h.assertTrue(Math.abs(v.getElevation() - 30) < 0.3f, "elev " + v.getElevation());
            v.toggleGunner();
            h.assertTrue(!v.isGunnerMode() && !v.hasState(Bm21Entity.ST_SHIELDS), "stowed on exit");
            v.discard();
        });
    }

    /** F-GUNNER: the predicted impact point of a laying (flat plane), fed back into the fire-control solver, gives the same elevation. */
    @GameTest(template = T)
    public static void predictedImpactMatchesSolver(GameTestHelper h) {
        Bm21Entity v = spawnVehicle(h);
        v.loadAll(RocketType.HE_FRAG);
        for (float elev : new float[]{8, 20, 35}) {
            double flat = v.getY(); // the test world's real terrain is irrelevant here: fly over a flat plane at the vehicle's level
            Vec3 p = v.predictImpact(90, elev, (x, z) -> flat);
            h.assertTrue(p != null, "no impact for " + elev);
            Ballistics.Solution s = v.solve(BlockPos.containing(p.x, p.y - 0.01, p.z), RocketType.HE_FRAG);
            h.assertTrue(s.ok(), "no solution back for " + elev + ": " + s.reason());
            h.assertTrue(Math.abs(s.elevationDeg() - elev) < 0.6, "elev " + s.elevationDeg() + " vs " + elev + " impact " + p + " vehicle " + v.position() + " pivot " + v.launcherPivot());
        }
        v.discard();
        h.succeed();
    }
}
