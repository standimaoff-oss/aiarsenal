package ru.aiforge.arsenal.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import ru.aiforge.arsenal.AiArsenal;
import ru.aiforge.arsenal.core.Kind;
import ru.aiforge.arsenal.core.Warheads;
import ru.aiforge.arsenal.entity.FpvDrone;
import ru.aiforge.arsenal.entity.MissileEntity;
import ru.aiforge.arsenal.entity.NukeEntity;
import ru.aiforge.arsenal.item.DetonatorItem;
import ru.aiforge.arsenal.nuke.ChargeRegistry;
import ru.aiforge.arsenal.nuke.NuclearBlast;
import ru.aiforge.arsenal.registry.ArsenalRegistry;
import ru.aiforge.tubombers.aircraft.AircraftType;
import ru.aiforge.tubombers.aircraft.BombType;
import ru.aiforge.tubombers.entity.BomberEntity;
import ru.aiforge.tubombers.glb.GlbModel;
import ru.aiforge.tubombers.glb.GlbModels;
import ru.aiforge.tubombers.item.AircraftItem;

import java.util.List;

/** Drone / missile / nuke logic tests (./gradlew runGameTestServer), template data/aiarsenal/structures/empty.nbt. */
@GameTestHolder(AiArsenal.MODID)
@PrefixGameTestTemplate(false)
public final class ArsenalGameTests {
    private static final String T = "empty";

    private ArsenalGameTests() {
    }

    /** A-GLB: every kind has its root node in its GLB, plus the animation clips the code drives. */
    @GameTest(template = T)
    public static void glbRootsAndClips(GameTestHelper h) {
        for (Kind k : Kind.values()) {
            GlbModel m = GlbModels.get(k.glb);
            h.assertTrue(m != null && m.node(k.root) >= 0, k.id + ": root node " + k.root + " missing in " + k.glb + ".glb");
        }
        h.assertTrue(GlbModels.get("cruise").anim("launcher_erect") != null, "launcher_erect clip");
        h.assertTrue(GlbModels.get("nukes").anim("yarstel_erect") != null, "yarstel_erect clip");
        h.assertTrue(GlbModels.get("shahed").node("RAIL_Seat") >= 0, "RAIL_Seat marker");
        for (String n : new String[]{"S1", "S2", "S3", "Bus"}) h.assertTrue(GlbModels.get("nukes").node("YARS_" + n) >= 0, "YARS_" + n);
        for (int i = 0; i < 4; i++) h.assertTrue(GlbModels.get("recon").node("MQ9_Store" + i) >= 0, "MQ9_Store" + i);
        h.succeed();
    }

    /** A-YIELD: destruction radius grows monotonically with yield (cube-root scaling) and is capped for the Tsar Bomba. */
    @GameTest(template = T)
    public static void blastRadiusScaling(GameTestHelper h) {
        float r1 = NuclearBlast.radius(Kind.RDS1.yieldKt()), r2 = NuclearBlast.radius(Kind.YARS.yieldKt()),
                r3 = NuclearBlast.radius(Kind.AN602.yieldKt());
        h.assertTrue(r1 > 20 && r1 < r2 && r2 < r3, "radius order " + r1 + " < " + r2 + " < " + r3);
        h.assertTrue(r3 <= 190f * 1.001f * ru.aiforge.arsenal.ArsenalConfig.NUKE_RADIUS_SCALE.get(), "AN602 cap " + r3);
        // 25x the yield -> ~2.9x the radius
        float k = NuclearBlast.radius(22f * 25) / NuclearBlast.radius(22f);
        h.assertTrue(k > 2.7f && k < 3.1f, "cube-root law " + k);
        h.succeed();
    }

    /** A-MISSILE: a Hellfire in terminal guidance hits a designated ground point. */
    @GameTest(template = T, timeoutTicks = 120)
    public static void missileHitsTarget(GameTestHelper h) {
        BlockPos tgt = h.absolutePos(new BlockPos(7, 1, 7));
        Vec3 start = h.absoluteVec(new Vec3(1.5, 5, 1.5));   // inside the test volume (the area is walled by barriers)
        Vec3 dir = Vec3.atCenterOf(tgt).subtract(start).normalize();
        int before = Warheads.count;
        MissileEntity m = MissileEntity.create(h.getLevel(), Kind.HELLFIRE, start, dir.scale(1.5), tgt, null, null, MissileEntity.TERMINAL);
        h.getLevel().addFreshEntity(m);
        h.succeedWhen(() -> {
            h.assertTrue(Warheads.count > before, "not detonated yet");
            h.assertTrue(m.isRemoved(), "missile still alive");
            Vec3 e = m.position();
            h.assertTrue(e.distanceTo(Vec3.atCenterOf(tgt)) < 4, "miss distance " + e.distanceTo(Vec3.atCenterOf(tgt)));
        });
    }

    /** A-DETONATOR: an armed RDS-1 is registered with the charge registry and detonates remotely by id (airburst, no block damage). */
    @GameTest(template = T)
    public static void remoteDetonation(GameTestHelper h) {
        NuclearBlast.testNoBlocks = true;
        Vec3 p = h.absoluteVec(new Vec3(4.5, 260, 4.5));   // far above the ground: burst height > 1.2 R -> no surface effects
        NukeEntity n = NukeEntity.create(h.getLevel(), Kind.RDS1, p, Vec3.ZERO, 0, ChargeRegistry.FUZE_REMOTE, null, false);
        ChargeRegistry reg = ChargeRegistry.get(h.getLevel().getServer());
        ChargeRegistry.Charge c = reg.byEntity(n.getUUID());
        h.assertTrue(c != null && c.kind == Kind.RDS1 && c.fuze == ChargeRegistry.FUZE_REMOTE, "charge not registered");
        h.assertTrue(reg.byId(c.id) == c, "lookup by id");
        int before = NuclearBlast.detonations;
        h.assertTrue(DetonatorItem.detonate(h.getLevel().getServer(), c, null), "detonate returned false");
        h.assertTrue(NuclearBlast.detonations == before + 1, "no detonation");
        h.assertTrue(reg.byId(c.id) == null, "charge still registered");
        h.assertTrue(n.isRemoved(), "bomb entity still present");
        NuclearBlast.testNoBlocks = false;
        h.succeed();
    }

    /** A-FPV: both quads can hover (TWR > 1), the heavy 10" one is less agile; idle drone sits on the ground with full battery. */
    @GameTest(template = T, timeoutTicks = 60)
    public static void fpvHoverAndBattery(GameTestHelper h) {
        FpvDrone a = ArsenalRegistry.FPV.get().create(h.getLevel()), b = ArsenalRegistry.FPV.get().create(h.getLevel());
        a.setKind(Kind.FPV7);
        b.setKind(Kind.FPV10);
        h.assertTrue(a.twr() > 1.5 && b.twr() > 1.2 && b.twr() < a.twr(), "TWR " + a.twr() + " / " + b.twr());
        h.assertTrue(Kind.FPV10.power > Kind.FPV7.power, "payload order");
        Vec3 p = h.absoluteVec(new Vec3(4.5, 3, 4.5));
        a.setPos(p.x, p.y, p.z);
        h.getLevel().addFreshEntity(a);
        h.runAfterDelay(40, () -> {
            h.assertTrue(a.isAlive() && a.state() == FpvDrone.IDLE, "idle state");
            h.assertTrue(a.getY() < p.y - 0.5, "idle drone did not settle: " + a.getY());
            h.assertTrue(a.batteryTicks() > 7000, "battery drained while idle " + a.batteryTicks());
            a.discard();
            h.succeed();
        });
    }

    /** A-TU-KH101: a Tu-95MS loaded with Kh-101 releases them from the rotary launcher as powered missiles. */
    @GameTest(template = T, timeoutTicks = 300)
    public static void tuReleasesKh101(GameTestHelper h) {
        Vec3 c = h.absoluteVec(new Vec3(4.5, 300, 4.5));
        BomberEntity b = AircraftItem.spawn(h.getLevel(), AircraftType.TU95, c.x, c.y, c.z, 0);
        b.startAirborne(150);
        ru.aiforge.realwar.gametest.TestChunks.follow(b);
        b.setLoad(BombType.KH101, 2);
        b.setControl("bay", 1);
        b.setControl("mode", BomberEntity.MODE_SALVO);
        b.setControl("release_now", 1);
        b.crewActionAsServer(BomberEntity.A_RELEASE);
        // r5: wait for the condition instead of a fixed tick - on a loaded server the bomber may tick less often
        h.succeedWhen(() -> {
            List<MissileEntity> ms = h.getLevel().getEntitiesOfClass(MissileEntity.class, new AABB(c, c).inflate(600));
            h.assertTrue(b.releasedTotal == 2, "released " + b.releasedTotal + " age " + b.tickCount + " bayReady " + b.bayReady() + " left " + b.bombCount()
                    + " pos " + b.position() + " ticking " + h.getLevel().isPositionEntityTicking(b.blockPosition()) + " removed " + b.isRemoved());
            h.assertTrue(b.bombCount() == 0, "left " + b.bombCount());
            h.assertTrue(!ms.isEmpty() || Warheads.count > 0, "no missile entities");
            for (MissileEntity m : ms) h.assertTrue(m.kind() == Kind.KH101, "kind " + m.kind());
            ms.forEach(MissileEntity::discard);
            b.discard();
        });
    }
}
