package ru.aiforge.realwar.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;
import ru.aiforge.realwar.blast.ModDamage;
import ru.aiforge.realwar.damage.Armour;
import ru.aiforge.realwar.damage.DamageState;
import ru.aiforge.realwar.damage.HitContext;
import ru.aiforge.realwar.damage.VehicleDamage;
import ru.aiforge.realwar.ordnance.Ballistics;
import ru.aiforge.realwar.registry.ModEntities;
import ru.aiforge.realwar.vehicle.GunVehicle;
import ru.aiforge.realwar.vehicle.VehicleEntity;
import ru.aiforge.realwar.vehicle.VehicleKind;

/** r20: armour / penetration model (War Thunder style) and the hit camera data. */
@GameTestHolder("aiarsenal")
@PrefixGameTestTemplate(false)
public final class PenetrationTests {
    private PenetrationTests() {
    }

    private static final float[] ZERO = {0f, 0f};

    private static Armour.Result front(VehicleKind k, Ballistics.Profile p, double speed, boolean upper) {
        VehicleDamage.Geo g = VehicleDamage.geo(k, true, true);
        double y = upper ? g.cy() + g.hy() * 0.55 : g.cy() - g.hy() * 0.3;
        Vector3d p0 = new Vector3d(0, y, g.cz() + g.hz());
        return Armour.solve(g, k, false, ZERO, p0, new Vector3d(0, 0, -1), Armour.Ammo.of(p), speed, 0.99);
    }

    private static double v0(Ballistics.Profile p) {
        return Armour.Ammo.of(p).v0();
    }

    private static String s(Armour.Result r) {
        return "res=" + r.result() + " zone=" + Armour.ZONE_KEYS[r.zone()] + " pen=" + Math.round(r.pen()) + " eff=" + Math.round(r.eff()) + " ang=" + Math.round(r.angle());
    }

    /** Classic duels with published numbers: BR-365 vs Tiger front at the muzzle (pen) and at long range (no pen),
     *  Pzgr 39 vs the 60 deg T-34 glacis (pen), the 2 cm APCR vs the same glacis (no pen), M829A4 vs T-90M turret
     *  front (pen), 8.8 cm vs T-90M (no pen), a grazing hit (ricochet), the de Marre fall-off and the spall seed. */
    @GameTest(template = "arena", batch = "rw_pen", timeoutTicks = 40)
    public static void penetration_math_matches_published_values(GameTestHelper h) {
        Armour.Result r = front(VehicleKind.TIGER, Ballistics.Profile.S85_AP, v0(Ballistics.Profile.S85_AP), true);
        h.assertTrue(r.zone() == Armour.HULL_UF && r.result() == Armour.PEN, "BR-365 у дульного среза должен пробивать ВЛД Тигра: " + s(r));
        r = front(VehicleKind.TIGER, Ballistics.Profile.S85_AP, v0(Ballistics.Profile.S85_AP) * 0.75, true);
        h.assertTrue(r.result() == Armour.NO_PEN, "BR-365 на 75 % скорости не должен пробивать 100 мм Тигра: " + s(r));
        r = front(VehicleKind.T34, Ballistics.Profile.K75_AP, v0(Ballistics.Profile.K75_AP), true);
        h.assertTrue(r.result() == Armour.PEN && r.eff() > 70 && r.eff() < 90 && Math.abs(r.angle() - 60) < 2, "7.5 cm Pzgr 39 vs ВЛД Т-34 45 мм / 60°: " + s(r));
        r = front(VehicleKind.T34, Ballistics.Profile.KW20_AP, v0(Ballistics.Profile.KW20_AP), true);
        h.assertTrue(r.result() != Armour.PEN, "2 cm Pzgr 40 не должен пробивать ВЛД Т-34: " + s(r));
        VehicleDamage.Geo g = VehicleDamage.geo(VehicleKind.T90M, true, true);
        float[] tb = Armour.turretBox(g, VehicleKind.T90M);
        h.assertTrue(tb != null, "нет коробки башни Т-90М");
        Vector3d tp = new Vector3d(tb[0] + tb[3] * 0.65, tb[1], tb[2] + tb[5] + 0.01);   // turret cheek, clear of the gun
        r = Armour.solve(g, VehicleKind.T90M, false, ZERO, tp, new Vector3d(0, 0, -1), Armour.Ammo.of(Ballistics.Profile.T120_AP), v0(Ballistics.Profile.T120_AP), 0.99);
        h.assertTrue(r.zone() == Armour.TUR_F && r.result() == Armour.PEN, "M829A4 должен пробивать лоб башни Т-90М (700 мм): " + s(r));
        r = Armour.solve(g, VehicleKind.T90M, false, ZERO, tp, new Vector3d(0, 0, -1), Armour.Ammo.of(Ballistics.Profile.K88_AP), v0(Ballistics.Profile.K88_AP), 0.99);
        h.assertTrue(r.result() == Armour.NO_PEN, "8.8 cm не может пробить лоб Т-90М: " + s(r));
        // grazing shot along the Tiger's left side: ~80 deg to the plate -> ricochet
        VehicleDamage.Geo gt = VehicleDamage.geo(VehicleKind.TIGER, true, true);
        Vector3d sd = new Vector3d(-0.17, 0, -0.985).normalize();
        Vector3d sp = new Vector3d(gt.cx() + gt.hx(), gt.cy() + gt.hy() * 0.6, gt.cz());
        r = Armour.solve(gt, VehicleKind.TIGER, false, ZERO, sp, sd, Armour.Ammo.of(Ballistics.Profile.K75_AP), v0(Ballistics.Profile.K75_AP), 0.5);
        h.assertTrue(r.result() == Armour.RICOCHET, "скользящее попадание 80° должно дать рикошет: " + s(r));
        Armour.Ammo a = Armour.Ammo.of(Ballistics.Profile.T125_AP);
        h.assertTrue(a.penAt(a.v0()) > a.penAt(a.v0() * 0.85) && Math.abs(a.penAt(a.v0()) - 700) < 1, "падение бронепробития с дистанцией");
        h.assertTrue(Armour.Ammo.of(Ballistics.Profile.T120_HE).type() == Armour.Type.HEAT, "M830A1 - кумулятивный");
        Vector3d d = new Vector3d(0, 0, 1);
        Vector3d[] s1 = Armour.spall(42L, d, 25, 8), s2 = Armour.spall(42L, d, 25, 8);
        for (int i = 0; i < 8; i++) {
            h.assertTrue(s1[i].distance(s2[i]) < 1e-9, "осколки не детерминированы");
            h.assertTrue(Math.toDegrees(Math.acos(s1[i].dot(d))) <= 25.01, "осколок вне конуса");
        }
        h.succeed();
    }

    /** WWI: verify the QF 6-pdr armour faces and that an exposed lower-run hit cannot invent a hull penetration. */
    @GameTest(template = "arena", batch = "rw_pen", timeoutTicks = 40)
    public static void mark_i_armour_and_track_layers(GameTestHelper h) {
        VehicleDamage.Geo g = VehicleDamage.geo(VehicleKind.MARK_I, true, true);
        h.assertTrue(!g.turret(), "Mark I side sponsons are not a rotating turret");
        Armour.Result r = front(VehicleKind.MARK_I, Ballistics.Profile.QF6_AP,
                v0(Ballistics.Profile.QF6_AP), true);
        h.assertTrue(r.zone() == Armour.HULL_UF && r.result() == Armour.PEN && r.nominal() == 12f,
                "57 mm QF 6-pdr AP through the 12 mm front plate: " + s(r));
        Vector3d side = new Vector3d(1.82, 1.23, 0.0); // outer face of the right sponson, clear of the track loop
        r = Armour.solve(g, VehicleKind.MARK_I, false, ZERO, side, new Vector3d(-1, 0, 0),
                Armour.Ammo.of(Ballistics.Profile.QF6_AP), v0(Ballistics.Profile.QF6_AP), 0.99);
        h.assertTrue(r.zone() == Armour.HULL_S && r.result() == Armour.PEN && r.nominal() == 8f,
                "57 mm QF 6-pdr AP through the 8 mm sponson: " + s(r));
        Vector3d track = new Vector3d(1.50, 0.13, -2.45);
        r = Armour.solve(g, VehicleKind.MARK_I, false, ZERO, track, new Vector3d(-1, 0, 0),
                Armour.Ammo.of(Ballistics.Profile.QF6_AP), v0(Ballistics.Profile.QF6_AP), 0.99);
        h.assertTrue(r.zone() == Armour.TRACK && r.result() == Armour.MODULE && r.module() == DamageState.TRACK_L
                        && !r.viaTrack(),
                "the exposed lower run damages the left track only; it is below and behind no hull plate: " + s(r));
        h.succeed();
    }

    @SuppressWarnings("unchecked")
    private static <T extends VehicleEntity> T vehicle(ServerLevel level, EntityType<?> type, double x, double y, double z) {
        VehicleEntity e = (VehicleEntity) type.create((Level) level);
        e.moveTo(x, y, z, 0f, 0f);
        level.addFreshEntity(e);
        TestChunks.follow(e);
        return (T) e;
    }

    /** Live hits on a Tiger: a 2 cm APCR bounces off the front (tiny hull damage, no modules), the 8.8 cm Pzgr 39
     *  into the rear goes through and wrecks modules; the hit camera data is filled in both cases. */
    @GameTest(template = "arena", batch = "rw_pen", timeoutTicks = 80)
    public static void live_hits_resolve_armour_and_fill_hit_camera(GameTestHelper h) {
        ServerLevel l = h.getLevel();
        BlockPos c = h.absolutePos(new BlockPos(15, 0, 15));
        TestChunks.force(l, Vec3.atCenterOf(c));
        int top = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.getX(), c.getZ());
        for (int dx = -7; dx <= 7; dx++)
            for (int dz = -7; dz <= 7; dz++)
                for (int y = top; y <= top + 8; y++) l.setBlock(new BlockPos(c.getX() + dx, y, c.getZ() + dz), Blocks.AIR.defaultBlockState(), 2);
        GunVehicle t = vehicle(l, ModEntities.TIGER.get(), c.getX() + 0.5, top, c.getZ() + 0.5);
        h.runAfterDelay(10, () -> {
            VehicleDamage.Geo g = VehicleDamage.geo(t);
            float hp0 = t.hp();
            Vec3 p = t.localToWorld(0, g.cy() + g.hy() * 0.55, g.cz() + g.hz() + 0.02);
            Vec3 dir = t.localToWorld(0, g.cy() + g.hy() * 0.55, g.cz()).subtract(p).normalize();
            VehicleDamage.lastCam = null;
            HitContext.hurt(t, ModDamage.source(l, ModDamage.ORDNANCE), 20f, p, dir, HitContext.Kind.AP, 20,
                    new HitContext.Shot(-1, Armour.Ammo.of(Ballistics.Profile.KW20_AP), v0(Ballistics.Profile.KW20_AP), p.add(dir.scale(-300))));
            VehicleDamage.Cam cam = VehicleDamage.lastCam;
            h.assertTrue(cam != null, "нет данных камеры попадания");
            h.assertTrue(cam.res().result() != Armour.PEN, "2 см не должен пробить Тигр в лоб: " + s(cam.res()));
            h.assertTrue(hp0 - t.hp() < 4f, "непробитие сняло слишком много: " + (hp0 - t.hp()));
            h.assertTrue(cam.dist() > 100, "дистанция не посчитана: " + cam.dist());
            float hp1 = t.hp();
            Vec3 pr = t.localToWorld(0, g.cy() + g.hy() * 0.3, g.cz() - g.hz() - 0.02);
            Vec3 dr = t.localToWorld(0, g.cy() + g.hy() * 0.3, g.cz()).subtract(pr).normalize();
            HitContext.hurt(t, ModDamage.source(l, ModDamage.ORDNANCE), 60f, pr, dr, HitContext.Kind.AP, 60,
                    new HitContext.Shot(-1, Armour.Ammo.of(Ballistics.Profile.K88_AP), v0(Ballistics.Profile.K88_AP), pr.add(dr.scale(-200))));
            cam = VehicleDamage.lastCam;
            h.assertTrue(cam != null && cam.res().result() == Armour.PEN && cam.res().zone() == Armour.HULL_R, "8.8 cm в корму должен пробить: "
                    + (cam == null ? "null" : s(cam.res())));
            h.assertTrue(cam.mods().length > 0, "пробитие в корму не повредило ни одного модуля");
            h.assertTrue(hp1 - t.hp() > 30f || t.dmg().dead(), "пробитие должно снять полный урон: " + (hp1 - t.hp()));
            t.discard();
            h.succeed();
        });
    }

    /** r20: rounds hit the bow / stern of a long hull (outside the square entity AABB) and miss just past the bow. */
    @GameTest(template = "arena", batch = "rw_pen", timeoutTicks = 60)
    public static void long_hulls_are_hit_along_their_whole_length(GameTestHelper h) {
        ServerLevel l = h.getLevel();
        BlockPos c = h.absolutePos(new BlockPos(15, 0, 15));
        TestChunks.force(l, Vec3.atCenterOf(c));
        int top = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.getX(), c.getZ());
        GunVehicle t = vehicle(l, ModEntities.ABRAMS.get(), c.getX() + 0.5, top, c.getZ() + 0.5);
        h.runAfterDelay(10, () -> {
            VehicleDamage.Geo g = VehicleDamage.geo(t);
            double y = g.cy() + g.hy() * 0.2;
            double zBow = g.cz() + g.hz() * 0.85;
            Vec3 a = t.localToWorld(30, y, zBow), b = t.localToWorld(-30, y, zBow);
            Vec3 hit = ru.aiforge.realwar.damage.VehicleHit.clip(t, a, b);
            h.assertTrue(hit != null, "выстрел в борт у носа Абрамса не попал");
            h.assertTrue(t.getBoundingBox().clip(a, b).isEmpty(), "квадратный AABB тоже попал - тест неинформативен");
            Vec3 a2 = t.localToWorld(30, y, g.cz() + g.hz() + 1.0), b2 = t.localToWorld(-30, y, g.cz() + g.hz() + 1.0);
            h.assertTrue(ru.aiforge.realwar.damage.VehicleHit.clip(t, a2, b2) == null, "попадание в воздух перед носом");
            Vec3 st = t.localToWorld(30, y, g.cz() - g.hz() * 0.85), en = t.localToWorld(-30, y, g.cz() - g.hz() * 0.85);
            h.assertTrue(ru.aiforge.realwar.damage.VehicleHit.clip(t, st, en) != null, "корма не поражается");
            t.discard();
            h.succeed();
        });
    }
}
