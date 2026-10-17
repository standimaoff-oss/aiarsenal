package ru.aiforge.realwar.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import ru.aiforge.realwar.blast.ModDamage;
import ru.aiforge.realwar.craft.BoatEntity;
import ru.aiforge.realwar.craft.CraftEntity;
import ru.aiforge.realwar.craft.CraftRegistry;
import ru.aiforge.realwar.damage.CraftDamage;
import ru.aiforge.realwar.damage.DamageState;
import ru.aiforge.realwar.damage.DebrisEntity;
import ru.aiforge.realwar.damage.HitContext;
import ru.aiforge.realwar.damage.VehicleDamage;
import ru.aiforge.realwar.registry.ModEntities;
import ru.aiforge.realwar.vehicle.GunVehicle;
import ru.aiforge.realwar.vehicle.VehicleEntity;

import java.util.List;

/** r18: War Thunder style module damage - tracks, ammo rack, rotor, wing, hull holes. */
@GameTestHolder("aiarsenal")
@PrefixGameTestTemplate(false)
public final class DamageTests {
    private DamageTests() {
    }

    private static int flat(GameTestHelper h, int rx, int rz, int half) {
        ServerLevel l = h.getLevel();
        BlockPos c = h.absolutePos(new BlockPos(rx, 0, rz));
        TestChunks.force(l, Vec3.atCenterOf(c));
        int top = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.getX(), c.getZ());
        for (int dx = -half; dx <= half; dx++)
            for (int dz = -half; dz <= half; dz++)
                for (int y = top; y <= top + 12; y++) l.setBlock(new BlockPos(c.getX() + dx, y, c.getZ() + dz), Blocks.AIR.defaultBlockState(), 2);
        return top;
    }

    @SuppressWarnings("unchecked")
    private static <T extends VehicleEntity> T vehicle(ServerLevel level, EntityType<?> type, double x, double y, double z) {
        VehicleEntity e = (VehicleEntity) type.create((Level) level);
        e.moveTo(x, y, z, 0f, 0f);
        level.addFreshEntity(e);
        TestChunks.follow(e);
        return (T) e;
    }

    private static <T extends CraftEntity> T craft(GameTestHelper h, EntityType<T> type, Vec3 p) {
        T e = type.create(h.getLevel());
        e.moveTo(p.x, p.y, p.z, 0f, 0f);
        e.initFresh();
        h.getLevel().addFreshEntity(e);
        TestChunks.follow(e);
        return e;
    }

    private static List<DebrisEntity> debris(ServerLevel l, Vec3 c, double r, int mode) {
        return l.getEntitiesOfClass(DebrisEntity.class, new AABB(c, c).inflate(r), d -> mode < 0 || d.mode() == mode);
    }

    /** The body box of a ground vehicle (model frame), the same box VehicleDamage uses. */
    private static float[] body(VehicleEntity v) {
        for (int i = 0; i < v.spec.partKinds().length; i++) if ("body".equals(v.spec.partKinds()[i])) return v.spec.parts()[i];
        return new float[]{0, v.spec.height() * 0.4f, 0, v.spec.aabbW() * 0.5f, v.spec.height() * 0.4f, v.spec.length() * 0.45f};
    }

    /** An AP round fired down onto the left track breaks it: the track rolls off as debris, the tank cannot drive,
     *  then the crew repairs it in place (15 s) and the debris is cleared. */
    @GameTest(template = "arena", batch = "rw_dmg", timeoutTicks = 520)
    public static void ap_hit_throws_track_and_crew_repairs_it(GameTestHelper h) {
        ServerLevel l = h.getLevel();
        int top = flat(h, 15, 15, 7);
        BlockPos c = h.absolutePos(new BlockPos(15, 0, 15));
        GunVehicle t = vehicle(l, ModEntities.T90M.get(), c.getX() + 0.5, top, c.getZ() + 0.5);
        int[] tick = {0};
        h.onEachTick(() -> {
            tick[0]++;
            if (tick[0] == 20) {
                float[] b = body(t);
                Vec3 p = t.localToWorld(b[0] + b[3] - 0.2, b[1] + b[4] + 2.0, b[2]);
                HitContext.hurt(t, ModDamage.source(l, ModDamage.ORDNANCE), 300f, p, new Vec3(0, -1, 0), HitContext.Kind.AP, 300);
                h.assertTrue(t.dmg().broken(DamageState.TRACK_L), "левая гусеница не сбита: bits=" + Integer.toBinaryString(t.dmg().broken));
                h.assertFalse(t.dmg().broken(DamageState.TRACK_R), "правая гусеница не должна пострадать");
                h.assertFalse(t.dmg().dead(), "танк не должен погибнуть от попадания в гусеницу");
                h.assertFalse(VehicleDamage.canDrive(t), "без гусеницы танк не должен ехать");
                h.assertTrue(debris(l, t.position(), 8, DebrisEntity.TRACK).size() == 1, "нет обломка-гусеницы (TRACK)");
            }
            if (tick[0] > 20 && !t.dmg().broken(DamageState.TRACK_L)) {
                h.assertTrue(tick[0] > 300, "ремонт слишком быстрый: " + tick[0]);
                h.assertTrue(VehicleDamage.canDrive(t), "после ремонта танк должен ехать");
                h.assertTrue(debris(l, t.position(), 8, DebrisEntity.TRACK).isEmpty(), "старая гусеница не убрана после ремонта");
                t.discard();
                h.succeed();
            }
            if (tick[0] > 500) h.fail("гусеница не отремонтирована за 480 тиков");
        });
    }

    /** A full T-90M carousel cooking off kills the tank and throws the turret high into the air. */
    @GameTest(template = "arena", batch = "rw_dmg_boom", timeoutTicks = 120)
    public static void ammo_cookoff_tosses_turret(GameTestHelper h) {
        ServerLevel l = h.getLevel();
        int top = flat(h, 15, 15, 7);
        BlockPos c = h.absolutePos(new BlockPos(15, 0, 15));
        GunVehicle t = vehicle(l, ModEntities.T90M.get(), c.getX() + 0.5, top, c.getZ() + 0.5);
        double[] maxY = {0};
        h.runAfterDelay(10, () -> {
            t.fillMagazine();
            h.assertTrue(t.loadedCount() > 0, "магазин пуст");
            VehicleDamage.detonate(l, t);
            h.assertTrue(t.dmg().dead(), "подрыв БК должен уничтожить танк");
            h.assertTrue(t.dmg().broken(DamageState.TURRET_GONE), "башня не сорвана");
            h.assertTrue(debris(l, t.position(), 10, DebrisEntity.RIGID).size() == 1, "нет обломка-башни");
        });
        h.onEachTick(() -> {
            List<DebrisEntity> d = debris(l, t.position(), 60, DebrisEntity.RIGID);
            if (!d.isEmpty()) maxY[0] = Math.max(maxY[0], d.get(0).getY() - top);
        });
        h.runAfterDelay(80, () -> {
            h.assertTrue(maxY[0] > 3.0, "башня подлетела только на " + String.format("%.1f", maxY[0]) + " м");
            h.assertTrue(VehicleDamage.handlesDeath(t) && !t.isRemoved(), "корпус остаётся горящим остовом");
            for (DebrisEntity d : debris(l, t.position(), 80, -1)) d.discard();
            t.discard();
            h.succeed();
        });
    }

    /** A proximity burst at the Ka-52 hub breaks the rotor blades: debris flies off, lift is gone. */
    @GameTest(template = "arena", batch = "rw_dmg", timeoutTicks = 80)
    public static void prox_burst_breaks_heli_rotor(GameTestHelper h) {
        ServerLevel l = h.getLevel();
        int top = flat(h, 15, 15, 9);
        BlockPos c = h.absolutePos(new BlockPos(15, 0, 15));
        CraftEntity k = craft(h, CraftRegistry.KA52.get(), new Vec3(c.getX() + 0.5, top, c.getZ() + 0.5));
        h.runAfterDelay(10, () -> {
            Vec3 hub = k.localToWorld(0, k.kind.heli.hubY(), 0);
            HitContext.hurt(k, ModDamage.source(l, ModDamage.ORDNANCE), 20f, hub, null, HitContext.Kind.PROX, 220);
            h.assertTrue(k.dmg().broken(DamageState.ROTOR), "винт не сломан");
            h.assertTrue(CraftDamage.rotorLift(k) == 0, "сломанный винт не должен давать подъёмную силу");
            h.assertTrue(!debris(l, k.position(), 12, DebrisEntity.ROTOR).isEmpty(), "нет обломков лопастей");
        });
        h.runAfterDelay(60, () -> {
            for (DebrisEntity d : debris(l, k.position(), 80, -1)) d.discard();
            k.discard();
            h.succeed();
        });
    }

    /** An AP hit through the Bf 109 left outer wing tears it off at the cut line. */
    @GameTest(template = "arena", batch = "rw_dmg", timeoutTicks = 80)
    public static void ap_hit_tears_plane_wing_off(GameTestHelper h) {
        ServerLevel l = h.getLevel();
        int top = flat(h, 15, 15, 9);
        BlockPos c = h.absolutePos(new BlockPos(15, 0, 15));
        CraftEntity p = craft(h, CraftRegistry.BF109.get(), new Vec3(c.getX() + 0.5, top, c.getZ() + 0.5));
        h.runAfterDelay(10, () -> {
            Vec3 root = CraftDamage.wingRoot(p, true);
            Vec3 at = p.localToWorld(root.x + 1.0, root.y + 3.0, root.z);
            HitContext.hurt(p, ModDamage.source(l, ModDamage.ORDNANCE), 30f, at, p.localToWorld(root.x + 1.0, root.y - 3.0, root.z).subtract(at).normalize(),
                    HitContext.Kind.AP, 600);
            h.assertTrue(p.dmg().broken(DamageState.WING_L), "левое крыло не оторвано: bits=" + Integer.toBinaryString(p.dmg().broken));
            h.assertFalse(p.dmg().broken(DamageState.WING_R), "правое крыло цело");
            h.assertTrue(CraftDamage.liftFactor(p) < 0.6, "подъёмная сила без крыла");
            h.assertTrue(debris(l, p.position(), 12, DebrisEntity.RIGID).size() == 1, "нет обломка крыла");
        });
        h.runAfterDelay(60, () -> {
            for (DebrisEntity d : debris(l, p.position(), 80, -1)) d.discard();
            p.discard();
            h.succeed();
        });
    }

    /** An AP round through the Raptor's side below the waterline leaves a hole: water floods in, then the crew patches it. */
    @GameTest(template = "arena", batch = "rw_dmg", timeoutTicks = 200)
    public static void hull_hole_below_waterline_floods(GameTestHelper h) {
        ServerLevel l = h.getLevel();
        BlockPos o = h.absolutePos(BlockPos.ZERO);
        int surf = pit(h, o, -6, 6, -2, 26, 4);
        BoatEntity b = craft(h, CraftRegistry.RAPTOR.get(), new Vec3(o.getX() + 0.5, surf - 0.9, o.getZ() + 12.5));
        h.runAfterDelay(30, () -> {
            float y = b.kind.water.draft() * 0.4f;
            Vec3 at = b.localToWorld(6, y, 0);
            HitContext.hurt(b, ModDamage.source(l, ModDamage.ORDNANCE), 20f, at, b.localToWorld(-6, y, 0).subtract(at).normalize(), HitContext.Kind.AP, 120);
            h.assertTrue(b.dmg().openHoles() > 0, "нет пробоины");
        });
        h.runAfterDelay(120, () -> {
            h.assertTrue(b.dmg().flood > 0, "вода не поступает через пробоину");
            h.assertFalse(b.dmg().dead(), "одна пробоина не топит катер");
            b.discard();
            h.succeed();
        });
    }

    private static int pit(GameTestHelper h, BlockPos o, int x0, int x1, int z0, int z1, int depth) {
        ServerLevel l = h.getLevel();
        for (int z = z0 - 16; z <= z1 + 16; z += 16) for (int x = x0 - 16; x <= x1 + 16; x += 16) TestChunks.force(l, Vec3.atCenterOf(o.offset(x, 0, z)));
        int top = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, o.getX() + (x0 + x1) / 2, o.getZ() + (z0 + z1) / 2);
        BlockState water = Blocks.WATER.defaultBlockState(), stone = Blocks.STONE.defaultBlockState(), air = Blocks.AIR.defaultBlockState();
        int floor = Math.max(top - depth - 1, l.getMinBuildHeight());
        int surf = floor + depth + 1;
        for (int x = x0 - 1; x <= x1 + 1; x++)
            for (int z = z0 - 1; z <= z1 + 1; z++) {
                boolean wall = x < x0 || x > x1 || z < z0 || z > z1;
                for (int y = floor; y < surf + 20; y++) {
                    BlockState s = y == floor ? stone : y >= surf ? air : (wall ? stone : water);
                    l.setBlock(new BlockPos(o.getX() + x, y, o.getZ() + z), s, 2);
                }
            }
        return surf;
    }
}
