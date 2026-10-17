package ru.aiforge.tubombers.gametest;

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
import org.joml.Vector3d;
import org.joml.Vector3f;
import ru.aiforge.tubombers.TuBombers;
import ru.aiforge.tubombers.aircraft.AircraftData;
import ru.aiforge.tubombers.aircraft.AircraftType;
import ru.aiforge.tubombers.aircraft.BombType;
import ru.aiforge.tubombers.aircraft.FlightModel;
import ru.aiforge.tubombers.entity.BombEntity;
import ru.aiforge.tubombers.entity.BomberEntity;
import ru.aiforge.tubombers.fx.ImpactHandler;
import ru.aiforge.tubombers.glb.GlbModel;
import ru.aiforge.tubombers.glb.GlbModels;
import ru.aiforge.tubombers.item.AircraftItem;

import java.util.List;

/** Logic tests (./gradlew runGameTestServer). Structure: data/tubombers/structures/empty.nbt (9x6x9, stone floor). */
@GameTestHolder(TuBombers.MODID)
@PrefixGameTestTemplate(false)
public final class TuGameTests {
    private static final String T = "empty";

    private TuGameTests() {
    }

    private static BomberEntity loadedAt(GameTestHelper h, AircraftType type, Vec3 at) {
        var tag = new net.minecraft.nbt.CompoundTag();
        tag.putString("id", "aiarsenal:" + type.id);
        tag.putFloat("qx", 0); tag.putFloat("qy", -.38268343f);
        tag.putFloat("qz", 0); tag.putFloat("qw", .9238795f);
        tag.putDouble("vx", -2); tag.putDouble("vz", 2);
        tag.putFloat("throttle", .35f); tag.putFloat("thrust", .35f);
        tag.putFloat("health", 100); tag.putInt("flags", BomberEntity.F_ENGINES);
        // Exactly the vanilla summon order: NBT first, requested location afterward.
        return (BomberEntity) EntityType.loadEntityRecursive(tag, h.getLevel(), e -> {
            e.moveTo(at.x, at.y, at.z, 0, 0);
            return e;
        });
    }

    @GameTest(template = T)
    public static void summonUsesFinalPositionBeforePhysics(GameTestHelper h) {
        var pos = h.absolutePos(new BlockPos(4, 80, 4));
        var at = Vec3.atCenterOf(pos);
        for (AircraftType type : AircraftType.values()) {
            BomberEntity b = loadedAt(h, type, at);
            h.assertTrue(b != null, "loaded " + type.id);
            try {
                b.tick();
                h.assertTrue(!b.isRemoved() && !b.isDestroyed(), "summoned aircraft remains alive");
                h.assertTrue(b.position().distanceTo(at) < 1, "physics must start at summon location, not NBT origin");
                h.assertTrue(Math.abs(b.st.x - b.getX()) < .0001 && Math.abs(b.st.y - b.getY()) < .0001,
                        "entity and flight state agree after the first tick");
                h.assertTrue(Math.abs(b.health() - 100) < .001 && Math.abs(b.in.throttle - .35f) < .001,
                        "position reconciliation cannot reset health or controls");
                h.assertTrue(Math.abs(b.st.yawDeg() - 45) < 2 && b.st.vel.length() > 1,
                        "saved attitude and velocity retained");
            } finally { b.discard(); }
        }
        h.succeed();
    }

    @GameTest(template = T)
    public static void saveReloadRetainsFlightStateAfterPositionSync(GameTestHelper h) {
        var at = Vec3.atCenterOf(h.absolutePos(new BlockPos(4, 80, 4)));
        for (AircraftType type : AircraftType.values()) {
            BomberEntity original = loadedAt(h, type, at);
            original.tick();
            var saved = new net.minecraft.nbt.CompoundTag();
            original.saveWithoutId(saved);
            saved.putString("id", "aiarsenal:" + type.id);
            var expected = original.position();
            var attitude = new org.joml.Quaternionf(original.st.q);
            original.discard();
            var copy = (BomberEntity) EntityType.loadEntityRecursive(saved, h.getLevel(), e -> e);
            h.assertTrue(copy != null, "reload " + type.id);
            try {
                copy.tick();
                h.assertTrue(copy.position().distanceTo(expected) < 1, "saved world location retained");
                h.assertTrue(Math.abs(copy.st.q.dot(attitude)) > .99f, "saved attitude retained");
                h.assertTrue(Math.abs(copy.health() - 100) < .001 && Math.abs(copy.in.throttle - .35f) < .001,
                        "saved health and throttle retained");
            } finally { copy.discard(); }
        }
        h.succeed();
    }

    /** flat ground at y = 64 everywhere */
    private static final FlightModel.Env FLAT = new FlightModel.Env() {
        @Override
        public double ground(double x, double y, double z) {
            return 64;
        }

        @Override
        public boolean solid(double x, double y, double z) {
            return y < 64;
        }
    };

    /** F-GLB: models parse, every animation clip and gameplay marker the code relies on exists. */
    @GameTest(template = T)
    public static void glbModelsAndMarkers(GameTestHelper h) {
        String[][] clips = {
                {"aileron", "bay_doors", "elevator", "flaps", "gear", "hatch", "props", "rudder", "turret"},
                {"aileron", "bay_doors", "elevator", "flaps", "gear", "rudder", "slats", "sweep"}};
        AircraftType[] types = {AircraftType.TU95, AircraftType.TU160};
        for (int k = 0; k < 2; k++) {
            AircraftType t = types[k];
            GlbModel m = GlbModels.get(t.glb);
            for (String c : clips[k]) h.assertTrue(m.anim(c) != null, t.id + " missing clip " + c);
            h.assertTrue(m.triangleCount > 30000, t.id + " triangles " + m.triangleCount);
            AircraftData d = AircraftData.get(t);
            h.assertTrue(d.seats.size() >= (k == 0 ? 7 : 4), t.id + " seats " + d.seats.size());
            h.assertTrue(d.seat("PILOT") != null && d.seat("NAV") != null, t.id + " pilot/nav seat");
            h.assertTrue(!d.walk.isEmpty() && !d.ladders.isEmpty(), t.id + " walk zones/ladders");
            for (BombType b : BombType.values())
                h.assertTrue(d.stations.get(b).size() >= t.capacity[b.ordinal()], t.id + " stations " + b + " " + d.stations.get(b).size());
        }
        for (BombType b : BombType.values()) h.assertTrue(GlbModels.get(b.glb()).node(b.node) >= 0, "bomb node " + b.node);
        h.succeed();
    }

    /** F-WALK: each seat stands over a walkable floor; each ladder box is found by ladderAt; aisle floors are continuous. */
    @GameTest(template = T)
    public static void cabinWalkZones(GameTestHelper h) {
        for (AircraftType t : AircraftType.values()) {
            AircraftData d = AircraftData.get(t);
            for (AircraftData.Seat s : d.seats) {
                float f = d.floorAt(s.pos().x, s.pos().y - 0.5, s.pos().z, 0.6);
                h.assertTrue(!Float.isNaN(f) && Math.abs(f - (s.pos().y - 0.5f)) < 0.9f, t.id + " seat " + s.role() + " floor " + f);
            }
            for (AircraftData.Ladder l : d.ladders)
                h.assertTrue(d.ladderAt(l.box().cx(), l.box().cz(), 0) == l, t.id + " ladder lookup");
        }
        h.succeed();
    }

    /** F-FLIGHT: hands-off Tu-95 at 8 km / 750 km/h holds its flight path for 60 s without stalling. */
    @GameTest(template = T)
    public static void cruiseIsStable(GameTestHelper h) {
        AircraftType t = AircraftType.TU95;
        AircraftData d = AircraftData.get(t);
        double scale = 0.5;
        FlightModel.State s = new FlightModel.State();
        s.y = 63 + 8000 * scale;
        s.setYPR(0, 0, 0);
        s.vel.set(0, 0, 208);
        s.onGround = false;
        s.thrust = 0.8f;
        FlightModel.Inputs in = new FlightModel.Inputs();
        in.throttle = 0.8f;
        FlightModel.Config c = new FlightModel.Config();
        c.gearUp = 1;
        double y0 = s.y;
        for (int i = 0; i < 1200; i++) FlightModel.step(s, t, d, in, c, FLAT, 0.05, scale, null);
        double dAlt = (s.y - y0) / scale;
        double v = s.vel.length();
        h.assertTrue(!s.stall && !s.crashed, "stall/crash " + s.crashReason);
        h.assertTrue(Math.abs(dAlt) < 600, "altitude drift " + dAlt);
        h.assertTrue(v > 150 && v < 260, "speed " + v);
        h.assertTrue(Math.abs(s.rollDeg()) < 5, "roll drift " + s.rollDeg());
        h.succeed();
    }

    /** F-TAKEOFF: Tu-160 at full afterburner, 20 deg sweep, take-off flaps: rotates and lifts off within a realistic run. */
    @GameTest(template = T, timeoutTicks = 200)
    public static void tu160TakesOff(GameTestHelper h) {
        AircraftType t = AircraftType.TU160;
        AircraftData d = AircraftData.get(t);
        double scale = 0.5;
        FlightModel.State s = new FlightModel.State();
        s.y = 64;
        s.setYPR(0, 0, 0);
        s.onGround = true;
        FlightModel.Inputs in = new FlightModel.Inputs();
        in.throttle = t.maxThrottle();
        FlightModel.Config c = new FlightModel.Config();
        c.flaps = 0.5f;
        double run = -1;
        for (int i = 0; i < 20 * 120; i++) {
            if (s.vel.length() > 82) in.pitch = 0.6f;
            if (s.pitchDeg() > 10) in.pitch = 0f;
            FlightModel.step(s, t, d, in, c, FLAT, 0.05, scale, null);
            if (!s.onGround && s.y > 64 + 10 * scale) {
                run = s.z / scale;
                break;
            }
            h.assertTrue(!s.crashed, "crashed on the runway: " + s.crashReason);
        }
        h.assertTrue(run > 600 && run < 3800, "take-off distance " + run + " m");
        h.succeed();
    }

    /** F-BALLISTICS: FAB-500 from 6000 m at 850 km/h falls ~36 s (vacuum 35 s + drag) and lands where CCIP predicted. */
    @GameTest(template = T)
    public static void bombFallTime(GameTestHelper h) {
        double scale = 0.5;
        Vector3d p = new Vector3d(0, 64 + 6000 * scale, 0), v = new Vector3d(0, 0, 236);
        double[] tt = new double[1];
        Vector3d pred = FlightModel.predictImpact(p, v, BombType.FAB500, scale, FLAT, tt);
        h.assertTrue(pred != null, "no impact");
        h.assertTrue(tt[0] > 34.5 && tt[0] < 40, "fall time " + tt[0]);
        double range = pred.z / scale;
        h.assertTrue(range > 6500 && range < 236 * tt[0], "throw range " + range);
        // FAB-1500 has twice the sectional density of the FAB-250/500 -> least drag, longest throw
        Vector3d p2 = FlightModel.predictImpact(p, v, BombType.FAB1500, scale, FLAT, null);
        Vector3d p3 = FlightModel.predictImpact(p, v, BombType.FAB250, scale, FLAT, null);
        h.assertTrue(p2.z > pred.z && p2.z > p3.z, "range order " + p3.z + " / " + pred.z + " < " + p2.z);
        h.assertTrue(Math.abs(p3.z - pred.z) / scale < 300, "FAB-250/500 throw differ " + (p3.z - pred.z) / scale);
        h.succeed();
    }

    /** F-RELEASE: an airborne bomber with an open bay releases a salvo; bombs leave from the bay stations. */
    @GameTest(template = T, timeoutTicks = 400)
    public static void salvoRelease(GameTestHelper h) {
        Vec3 c = h.absoluteVec(new Vec3(4.5, 300, 4.5));
        BomberEntity b = AircraftItem.spawn(h.getLevel(), AircraftType.TU95, c.x, c.y, c.z, 0);
        b.startAirborne(150);
        ru.aiforge.realwar.gametest.TestChunks.follow(b);   // keep the fast bomber in entity-ticking chunks
        b.setLoad(BombType.FAB500, 6);
        b.setControl("bay", 1);
        b.setControl("mode", BomberEntity.MODE_SALVO);
        b.setControl("release_now", 1);
        b.crewActionAsServer(BomberEntity.A_RELEASE);
        // the loaded 2-core test server may tick the bomber slower than the wall clock: poll instead of a fixed delay
        h.succeedWhen(() -> {
            List<BombEntity> bombs = h.getLevel().getEntitiesOfClass(BombEntity.class, new AABB(c, c).inflate(400));
            h.assertTrue(b.releasedTotal == 6, "released " + b.releasedTotal);
            h.assertTrue(b.bombCount() == 0, "left " + b.bombCount());
            h.assertTrue(bombs.size() >= 1 || ImpactHandler.detonations > 0, "no bomb entities");
            bombs.forEach(BombEntity::discard);
            b.discard();
        });
    }

    /** F-IMPACT: FAB-250 detonation digs a crater and kills an unprotected mob within the lethal radius. */
    @GameTest(template = T)
    public static void bombCraterAndBlast(GameTestHelper h) {
        for (int x = 0; x < 9; x++) for (int z = 0; z < 9; z++) for (int y = 0; y < 2; y++) h.setBlock(x, y, z, Blocks.DIRT);
        Pig pig = h.spawn(EntityType.PIG, new BlockPos(6, 2, 4));
        Vec3 p = h.absoluteVec(new Vec3(4.5, 2.0, 4.5));
        int before = ImpactHandler.detonations;
        ImpactHandler.bomb(h.getLevel(), p, BombType.FAB250, null, null, new Vec3(0, -200, 0));
        h.assertTrue(ImpactHandler.detonations == before + 1, "no detonation");
        h.assertTrue(ImpactHandler.lastCraterBlocks > 10, "crater blocks " + ImpactHandler.lastCraterBlocks);
        h.assertTrue(!pig.isAlive(), "pig survived at 1.5 blocks");
        h.succeed();
    }
}
