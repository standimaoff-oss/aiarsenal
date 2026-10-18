package ru.aiforge.realwar.gametest;

import com.mojang.authlib.GameProfile;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import ru.aiforge.realwar.glb.GlbModel;
import ru.aiforge.realwar.ordnance.AirTargets;
import ru.aiforge.realwar.ordnance.Ballistics;
import ru.aiforge.realwar.ordnance.ShellEntity;
import ru.aiforge.realwar.registry.ModEntities;
import ru.aiforge.realwar.vehicle.GunVehicle;
import ru.aiforge.realwar.vehicle.Specs;
import ru.aiforge.realwar.vehicle.VehicleEntity;
import ru.aiforge.realwar.vehicle.VehicleKind;
import ru.aiforge.realwar.vehicle.VehicleSpec;

/** Reply 4: tanks, IFV and artillery (GunVehicle + ShellEntity). */
@GameTestHolder(value = "aiarsenal")
@PrefixGameTestTemplate(value = false)
public final class GunTests {
    private GunTests() {
    }

    private static ServerPlayer mock(GameTestHelper h) {
        return new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "rw-gun"));
    }

    /** Flat stone strip from (x0,z0) to (x1,z1) (absolute), air above; returns ground y. */
    private static int strip(ServerLevel level, int x0, int z0, int x1, int z1, int y0) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); ++x) {
            for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); ++z) {
                for (int y = y0 - 1; y <= y0 + 12; ++y) {
                    level.setBlock(new BlockPos(x, y, z), y == y0 - 1 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
        return y0;
    }

    @SuppressWarnings("unchecked")
    private static <T extends VehicleEntity> T spawn(ServerLevel level, EntityType<?> type, double x, double y, double z, float yaw) {
        VehicleEntity e = (VehicleEntity) type.create((Level) level);
        e.moveTo(x, y, z, yaw, 0.0f);
        level.addFreshEntity(e);
        return (T) e;
    }

    private static void look(ServerPlayer p, Vec3 at) {
        Vec3 d = at.subtract(p.getEyePosition());
        p.setYRot((float) Math.toDegrees(Math.atan2(-d.x, d.z)));
        p.setXRot((float) -Math.toDegrees(Math.atan2(d.y, d.horizontalDistance())));
    }

    /** Every gun vehicle GLB: turret chain, muzzle reachable, tracked hulls carry wheels + track clips. */
    @GameTest(template = "arena", batch = "rw_gun", timeoutTicks = 40)
    public static void gun_models_rigged(GameTestHelper h) {
        VehicleKind[] ks = {VehicleKind.T90M, VehicleKind.ABRAMS, VehicleKind.BMP3, VehicleKind.MSTA, VehicleKind.D30, VehicleKind.M777, VehicleKind.PZ2, VehicleKind.PZ4, VehicleKind.TIGER, VehicleKind.STURMTIGER, VehicleKind.MAUS, VehicleKind.RATTE, VehicleKind.T34, VehicleKind.KV2, VehicleKind.IS2, VehicleKind.KV5, VehicleKind.BRADLEY, VehicleKind.TOS1A, VehicleKind.MARK_I, VehicleKind.WIRBELWIND, VehicleKind.ZSU37, VehicleKind.GEPARD, VehicleKind.SHILKA};
        VehicleSpec[] ss = {Specs.T90M, Specs.ABRAMS, Specs.BMP3, Specs.MSTA, Specs.D30, Specs.M777, Specs.PZ2, Specs.PZ4, Specs.TIGER, Specs.STURMTIGER, Specs.MAUS, Specs.RATTE, Specs.T34, Specs.KV2, Specs.IS2, Specs.KV5, Specs.BRADLEY, Specs.TOS1A, Specs.MARK_I, Specs.WIRBELWIND, Specs.ZSU37, Specs.GEPARD, Specs.SHILKA};
        for (int i = 0; i < ks.length; ++i) {
            VehicleKind k = ks[i];
            GunVehicle.Cfg c = GunVehicle.cfg(k);
            String path = "/assets/aiarsenal/models/glb/" + ss[i].id() + ".glb";
            try (InputStream in = GunTests.class.getResourceAsStream(path)) {
                h.assertTrue(in != null, path + " missing");
                GlbModel m = GlbModel.read(in);
                for (String n : new String[]{"launcher_yaw", "launcher_pitch", "barrel"}) {
                    h.assertTrue(m.nodeIndex(n) >= 0, ss[i].id() + ": node " + n);
                }
                if (k.isSpaag()) {
                    for (String n : new String[]{"radar_dish", "sam_l", "sam_r"}) {
                        h.assertTrue(m.nodeIndex(n) >= 0, ss[i].id() + ": spaag node " + n);
                    }
                }
                if (k == VehicleKind.MARK_I) {
                    for (String n : new String[]{"launcher_yaw_left", "launcher_pitch_left", "barrel_left", "MarkI_link_L_00", "MarkI_link_R_00"})
                        h.assertTrue(m.nodeIndex(n) >= 0, "mark_i: node " + n);
                }
                if (c.tracked()) {
                    for (String n : new String[]{"rw_L_0", "rw_R_0", "spk_L", "spk_R", "idl_L", "idl_R"}) {
                        h.assertTrue(m.nodeIndex(n) >= 0, ss[i].id() + ": node " + n);
                    }
                    for (String n : new String[]{"track_L", "track_R"}) {
                        GlbModel.Animation a = m.animation(n);
                        h.assertTrue(a != null && a.duration > 0.01f, ss[i].id() + ": clip " + n);
                    }
                }
                // r5 budget: detailed but light enough for several vehicles on screen (also with shader packs)
                int tris = m.totalTriangles();
                int maxT = k == VehicleKind.RATTE ? 64000 : 32000;   // 35 m land cruiser: 3 belts per side + walkable interior; LOD1 drops the interior and halves the rest
                h.assertTrue(c.tracked() ? tris > 9000 && tris < maxT : tris > 3000 && tris < 12000, ss[i].id() + ": triangles " + tris);
                if (c.tracked()) {
                    // distance LOD: same node names, clearly fewer triangles
                    String lp = path.replace(".glb", "_lod1.glb");
                    try (InputStream li = GunTests.class.getResourceAsStream(lp)) {
                        h.assertTrue(li != null, lp + " missing");
                        GlbModel lm = GlbModel.read(li);
                        h.assertTrue(lm.totalTriangles() < tris * 0.6, ss[i].id() + ": LOD triangles " + lm.totalTriangles());
                        for (GlbModel.Node nd : lm.nodes) {
                            h.assertTrue(nd.mesh < 0 || m.nodeIndex(nd.name) >= 0, ss[i].id() + ": LOD node " + nd.name);
                        }
                    }
                }
            } catch (IOException e) {
                h.fail("GLB read " + path + ": " + e);
            }
        }
        h.succeed();
    }

    /** r10: every tracked vehicle (modern + WW2) drives off under throttle - diagnostics are numeric. */
    @GameTest(template = "arena", batch = "rw_drive", timeoutTicks = 1400)
    public static void all_tanks_drive(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos c = h.absolutePos(new BlockPos(15, 0, 15));
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.getX(), c.getZ());
        VehicleKind[] ks = java.util.Arrays.stream(VehicleKind.values()).filter(k -> k.tracked() || k == VehicleKind.BM13).toArray(VehicleKind[]::new);
        strip(level, c.getX() - 30, c.getZ() - 30, c.getX() + 30 + 0, c.getZ() + 60, y);
        TestChunks.path(level, Vec3.atCenterOf(c), Vec3.atCenterOf(c).add(0, 0, 60));
        int[] idx = {0, 0};
        VehicleEntity[] cur = {null};
        Vec3[] p0 = {null};
        StringBuilder bad = new StringBuilder();
        h.onEachTick(() -> {
            if (cur[0] == null) {
                if (idx[0] >= ks.length) {
                    if (bad.length() > 0) h.fail("not driving: " + bad);
                    else h.succeed();
                    idx[0] = 1 << 20;
                    return;
                }
                EntityType<?> type = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getValue(new net.minecraft.resources.ResourceLocation("aiarsenal", ks[idx[0]].spec.id()));
                cur[0] = spawn(level, type, c.getX() + 0.5, y, c.getZ() + 0.5, 0.0f);
                TestChunks.follow(cur[0]);
                cur[0].setEngine(true);
                cur[0].setFuel(1.0f);
                p0[0] = cur[0].position();
                idx[1] = 0;
                return;
            }
            VehicleEntity v = cur[0];
            idx[1]++;
            v.setForcedInput(1.0f, 0.0f, false);
            if (idx[1] >= 60) {
                double moved = v.position().subtract(p0[0]).horizontalDistance();
                ru.aiforge.realwar.RealWar.LOGGER.info("[drive] {} moved {} v={} y={} ground={}", ks[idx[0]], String.format("%.2f", moved), String.format("%.2f", v.speedMs), String.format("%.2f", v.getY() - p0[0].y), v.onGround());
                if (moved < 1.5 || v.speedMs < 1.0) bad.append(ks[idx[0]]).append('(').append(String.format("%.2f", moved)).append(") ");
                v.discard();
                cur[0] = null;
                idx[0]++;
            }
        });
    }

    /** Skid steer: full steer at standstill pivots the T-90M on the spot, then throttle drives it off. */
    @GameTest(template = "arena", batch = "rw_gun", timeoutTicks = 300)
    public static void tank_pivots_and_drives(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos c = h.absolutePos(new BlockPos(15, 0, 15));
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.getX(), c.getZ());
        strip(level, c.getX() - 8, c.getZ() - 8, c.getX() + 8, c.getZ() + 60, y);
        TestChunks.path(level, Vec3.atCenterOf(c), Vec3.atCenterOf(c).add(0, 0, 60));
        GunVehicle t = spawn(level, ModEntities.T90M.get(), c.getX() + 0.5, y, c.getZ() + 0.5, 0.0f);
        TestChunks.follow(t);
        t.setEngine(true);
        t.setFuel(1.0f);
        Vec3 p0 = t.position();
        float yaw0 = t.getYRot();
        int[] phase = {0, 0};
        float[] yawPivot = {0};
        h.onEachTick(() -> {
            phase[1]++;
            if (phase[0] == 0) {
                t.setForcedInput(0.0f, 1.0f, false);
                if (phase[1] >= 60) {
                    float dy = Math.abs(net.minecraft.util.Mth.wrapDegrees(t.getYRot() - yaw0));
                    double moved = t.position().subtract(p0).horizontalDistance();
                    h.assertTrue(dy > 20.0f, "pivot turn too small: " + dy + " deg");
                    h.assertTrue(moved < 1.5, "pivot should be on the spot, moved " + moved);
                    yawPivot[0] = t.getYRot();
                    t.setYRot(0.0f);
                    phase[0] = 1;
                    phase[1] = 0;
                }
            } else {
                t.setForcedInput(1.0f, 0.0f, false);
                if (phase[1] >= 100) {
                    double moved = t.position().subtract(p0).horizontalDistance();
                    h.assertTrue(moved > 10.0, "tank should accelerate: moved " + moved + " blocks, v=" + t.speedMs);
                    h.assertTrue(t.speedMs > 4.0, "speed " + t.speedMs);
                    t.discard();
                    h.succeed();
                }
            }
        });
    }

    /** Gunner lases a Grad 45 blocks ahead, fires the main gun: the round must hit and damage the target. */
    @GameTest(template = "arena", batch = "rw_gun", timeoutTicks = 400)
    public static void t90_round_hits_vehicle(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos c = h.absolutePos(new BlockPos(5, 0, 5));
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.getX(), c.getZ());
        strip(level, c.getX() - 6, c.getZ() - 6, c.getX() + 6, c.getZ() + 56, y);
        TestChunks.path(level, Vec3.atCenterOf(c), Vec3.atCenterOf(c).add(0, 0, 56));
        GunVehicle t = spawn(level, ModEntities.T90M.get(), c.getX() + 0.5, y, c.getZ() + 0.5, 0.0f);
        VehicleEntity tgt = spawn(level, ModEntities.GRAD.get(), c.getX() + 0.5, y, c.getZ() + 45.5, 90.0f);
        TestChunks.follow(t, tgt);
        t.fillMagazine();
        ServerPlayer gunner = mock(h);
        h.assertTrue(t.enterSeat(gunner, t.operatorSeat()), "gunner seat");   // r9: one seat drives + fires
        float hp0 = tgt.hp();
        int[] n = {0};
        h.onEachTick(() -> {
            n[0]++;
            look(gunner, tgt.position().add(0, 1.4, 0));
            t.handleControl(gunner, 0, 0, n[0] > 30 && n[0] < 200, false, 0);
            if (!tgt.isAlive() || tgt.hp() < hp0 - 1.0f) {
                h.assertTrue(t.loadedCount() < t.kind.magazine, "no round was expended");
                gunner.stopRiding();
                t.discard();
                if (tgt.isAlive()) tgt.discard();
                h.succeed();
            }
        });
    }

    /** r21: the driver is killed -> the tank cannot drive; the loader-less T-90M moves the commander into the driver seat. */
    @GameTest(template = "arena", batch = "rw_gun", timeoutTicks = 400)
    public static void crew_driver_replaced(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos c = h.absolutePos(new BlockPos(20, 0, 5));
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.getX(), c.getZ());
        GunVehicle t = spawn(level, ModEntities.T90M.get(), c.getX() + 0.5, y, c.getZ() + 0.5, 0.0f);
        int[] r = ru.aiforge.realwar.damage.Crew.roles(t.kind);
        h.assertTrue(r.length == 3 && r[0] == ru.aiforge.realwar.damage.Crew.DRIVER, "T-90M crew D G C");
        h.assertTrue(ru.aiforge.realwar.damage.VehicleDamage.canDrive(t), "fresh tank drives");
        t.dmg.crew[0] = 0f;
        h.assertFalse(ru.aiforge.realwar.damage.VehicleDamage.canDrive(t), "dead driver must stop the tank");
        h.assertFalse(t.dmg.dead(), "one man lost is not a knock-out");
        h.onEachTick(() -> {
            if (t.dmg.dead()) h.fail("knocked out instead of replacing");
            if (ru.aiforge.realwar.damage.VehicleDamage.canDrive(t)) {
                h.assertTrue(t.dmg.crew[2] <= 0f && t.dmg.crew[1] > 0f, "commander took the driver seat, gunner stays");
                h.assertFalse(ru.aiforge.realwar.damage.Crew.ok(t, ru.aiforge.realwar.damage.Crew.COMMANDER), "commander seat now empty");
                t.discard();
                h.succeed();
            }
        });
    }

    /** r21: ground types: soil in the rain is mud, snow layers are snow; mud and snow slow tracks less than wheels. */
    @GameTest(template = "arena", batch = "rw_gun")
    public static void terrain_mud_snow(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos p = h.absolutePos(new BlockPos(30, 1, 30));
        level.setBlock(p.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(p, Blocks.SNOW.defaultBlockState(), 3);
        h.assertTrue(ru.aiforge.realwar.vehicle.Terrain.at(level, p.getX() + 0.5, p.getY() + 0.1, p.getZ() + 0.5) == ru.aiforge.realwar.vehicle.Terrain.SNOW, "snow layer = SNOW");
        level.setBlock(p, Blocks.MUD.defaultBlockState(), 3);
        h.assertTrue(ru.aiforge.realwar.vehicle.Terrain.at(level, p.getX() + 0.5, p.getY() + 1.0, p.getZ() + 0.5) == ru.aiforge.realwar.vehicle.Terrain.BOG, "mud block = BOG");
        level.setBlock(p, Blocks.STONE.defaultBlockState(), 3);
        h.assertTrue(ru.aiforge.realwar.vehicle.Terrain.at(level, p.getX() + 0.5, p.getY() + 1.0, p.getZ() + 0.5) == ru.aiforge.realwar.vehicle.Terrain.FIRM, "stone = FIRM");
        for (int g : new int[]{ru.aiforge.realwar.vehicle.Terrain.MUD, ru.aiforge.realwar.vehicle.Terrain.SNOW, ru.aiforge.realwar.vehicle.Terrain.BOG}) {
            h.assertTrue(ru.aiforge.realwar.vehicle.Terrain.vmaxMul(g, true) > ru.aiforge.realwar.vehicle.Terrain.vmaxMul(g, false), "tracks beat wheels on " + g);
            h.assertTrue(ru.aiforge.realwar.vehicle.Terrain.rollMul(g, true) > 1.0, "drag on " + g);
        }
        level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
        h.succeed();
    }

    /** D-30 auto-lays on a map target 180 blocks away (4.5 km) and the shell lands near it. */
    @GameTest(template = "arena", batch = "rw_gun", timeoutTicks = 900)
    public static void d30_shell_lands_on_map_target(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos c = h.absolutePos(new BlockPos(25, 0, 25));
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.getX(), c.getZ());
        strip(level, c.getX() - 4, c.getZ() - 4, c.getX() + 4, c.getZ() + 4, y);
        BlockPos tp = new BlockPos(c.getX() + 60, y, c.getZ() + 170);
        TestChunks.path(level, Vec3.atCenterOf(c), Vec3.atCenterOf(tp));
        int ty = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, tp.getX(), tp.getZ());
        Vec3 target = new Vec3(tp.getX() + 0.5, ty, tp.getZ() + 0.5);
        GunVehicle g = spawn(level, ModEntities.D30.get(), c.getX() + 0.5, y, c.getZ() + 0.5, 0.0f);
        TestChunks.follow(g);
        g.fillMagazine();
        ServerPlayer gunner = mock(h);
        h.assertTrue(g.enterSeat(gunner, g.operatorSeat()), "gunner seat");
        g.setMapTarget(gunner, new BlockPos(tp.getX(), ty, tp.getZ()));
        ShellEntity[] shell = {null};
        Vec3[] last = {null};
        int[] n = {0};
        h.onEachTick(() -> {
            n[0]++;
            g.handleControl(gunner, 0, 0, shell[0] == null, false, 0);
            if (shell[0] == null) {
                List<ShellEntity> l = level.getEntitiesOfClass(ShellEntity.class, new AABB(g.blockPosition()).inflate(12));
                if (!l.isEmpty()) {
                    shell[0] = l.get(0);
                    TestChunks.follow(shell[0]);
                }
                return;
            }
            if (shell[0].isAlive()) {
                last[0] = shell[0].position();
                return;
            }
            double miss = Math.hypot(last[0].x - target.x, last[0].z - target.z);
            h.assertTrue(miss < 14.0, "D-30 miss " + String.format("%.1f", miss) + " blocks (" + Math.round(miss * Ballistics.scaleK()) + " m) at 4.5 km");
            gunner.stopRiding();
            g.discard();
            h.succeed();
        });
    }

    /** Shells are not radar / SAM targets and their profiles are flagged as shells. */
    @GameTest(template = "arena", batch = "rw_gun", timeoutTicks = 40)
    public static void shells_are_not_air_targets(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        Vec3 at = h.absoluteVec(new Vec3(10.5, 6.0, 10.5));
        for (Ballistics.Profile p : new Ballistics.Profile[]{Ballistics.Profile.T125_HE, Ballistics.Profile.T120_AP, Ballistics.Profile.MSTA_HE, Ballistics.Profile.M777_HE, Ballistics.Profile.QF6_HE, Ballistics.Profile.QF6_AP}) {
            h.assertTrue(p.isShell(), p + " should be a shell profile");
            ShellEntity s = ShellEntity.fire(level, p, at, new Vec3(0, 1, 0), -1);
            h.assertTrue(!AirTargets.isAirTarget(s), p + " shell counted as air target");
            s.discard();
        }
        h.assertTrue(!Ballistics.Profile.GRAD.isShell(), "Grad rocket is not a shell");
        h.succeed();
    }

    /** Spec geometry and kind table agree for all six gun vehicles (muzzle in front of pivot, seats present). */
    @GameTest(template = "arena", batch = "rw_gun", timeoutTicks = 40)
    public static void gun_specs_consistent(GameTestHelper h) {
        VehicleKind[] ks = {VehicleKind.T90M, VehicleKind.ABRAMS, VehicleKind.BMP3, VehicleKind.MSTA, VehicleKind.D30, VehicleKind.M777, VehicleKind.PZ2, VehicleKind.PZ4, VehicleKind.TIGER, VehicleKind.STURMTIGER, VehicleKind.MAUS, VehicleKind.RATTE, VehicleKind.T34, VehicleKind.KV2, VehicleKind.IS2, VehicleKind.KV5, VehicleKind.BRADLEY, VehicleKind.MARK_I, VehicleKind.WIRBELWIND, VehicleKind.ZSU37, VehicleKind.GEPARD, VehicleKind.SHILKA};
        for (VehicleKind k : ks) {
            VehicleSpec s = k.spec;
            h.assertTrue(k.isGun(), k + " isGun");
            h.assertTrue(s.tubes().length >= 1 && s.tubes()[0][2] + s.pitchPivot()[2] > 0.8f, k + ": muzzle must be ahead of the turret pivot");
            h.assertTrue(s.seats().length >= (k.drivable() ? 2 : 1), k + ": seats");
            h.assertTrue(GunVehicle.cfg(k).primary().isShell(), k + ": primary profile");
            h.assertTrue(k.tracked() == GunVehicle.cfg(k).tracked(), k + ": tracked flag");
        }
        h.succeed();
    }

    /** Reply 12: Bradley (M242 dual feed + TOW), TOS-1A (24 tubes, rams), Ural-4320 (6 wheels, 14 seats). */
    @GameTest(template = "arena", batch = "rw_gun", timeoutTicks = 40)
    public static void r12_vehicles_consistent(GameTestHelper h) {
        h.assertTrue(GunVehicle.cfg(VehicleKind.BRADLEY).modes().length == 4, "Bradley: HE, AP, TOW, coax");
        h.assertTrue(Specs.TOS1A.tubes().length == VehicleKind.TOS1A.magazine && VehicleKind.TOS1A.magazine == 24, "TOS-1A: 24 tubes");
        h.assertTrue(!Ballistics.Profile.TOS220.isShell() && Ballistics.Profile.TOS220.tntKg >= 50.0, "TOS 220 mm thermobaric rocket");
        h.assertTrue(VehicleKind.TOS1A.artillery() && VehicleKind.TOS1A.tracked(), "TOS-1A flags");
        h.assertTrue(!VehicleKind.URAL.isGun() && Specs.URAL.seats().length >= 14, "Ural: transport, 14 seats got " + Specs.URAL.seats().length);
        for (String g : new String[]{"rocket_tos220", "tow_missile", "ural", "ural_lod1"}) {
            h.assertTrue(GunTests.class.getResource("/assets/aiarsenal/models/glb/" + g + ".glb") != null, g + ".glb");
        }
        try (InputStream in = GunTests.class.getResourceAsStream("/assets/aiarsenal/models/glb/tos1a.glb")) {
            GlbModel m = GlbModel.read(in);
            for (int k = 0; k < 24; k++) h.assertTrue(m.nodeIndex("rkt_" + k) >= 0, "tos1a: rkt_" + k);
            for (String n : new String[]{"ram_L", "ram1_L", "ram2_L", "ram_R", "ram1_R", "ram2_R"}) h.assertTrue(m.nodeIndex(n) >= 0, "tos1a: " + n);
        } catch (java.io.IOException e) {
            h.fail("tos1a.glb: " + e);
        }
        try (InputStream in = GunTests.class.getResourceAsStream("/assets/aiarsenal/models/glb/bradley.glb")) {
            GlbModel m = GlbModel.read(in);
            for (String n : new String[]{"tow_l", "tow_r"}) h.assertTrue(m.nodeIndex(n) >= 0, "bradley: " + n);
        } catch (java.io.IOException e) {
            h.fail("bradley.glb: " + e);
        }
        try (InputStream in = GunTests.class.getResourceAsStream("/assets/aiarsenal/models/glb/ural.glb")) {
            GlbModel m = GlbModel.read(in);
            for (int k = 0; k < 6; k++) h.assertTrue(m.nodeIndex("wheel_" + k) >= 0, "ural: wheel_" + k);
            h.assertTrue(m.nodeIndex("steering_wheel") >= 0, "ural: steering_wheel");
        } catch (java.io.IOException e) {
            h.fail("ural.glb: " + e);
        }
        h.succeed();
    }

    /** Reply 8: WW2 weapon modes, Ratte barrels alternate, Maus second gun, Sturmtiger rocket mortar, BM-13 as MLRS. */
    @GameTest(template = "arena", batch = "rw_gun", timeoutTicks = 40)
    public static void ww2_weapons_consistent(GameTestHelper h) {
        h.assertTrue(GunVehicle.cfg(VehicleKind.MAUS).modes().length == 4, "Maus: HE, AP, 7.5 cm, MG");
        h.assertTrue(GunVehicle.cfg(VehicleKind.RATTE).modes().length == 3, "Ratte: 28 cm + 12.8 cm rear turret + MG 151");
        h.assertTrue(GunVehicle.cfg(VehicleKind.STURMTIGER).modes().length == 1, "Sturmtiger: RW 61 only");
        h.assertTrue(GunVehicle.cfg(VehicleKind.TIGER).modes().length == 3, "Tiger: HE, AP, MG");
        for (VehicleKind k : new VehicleKind[]{VehicleKind.PZ2, VehicleKind.PZ4, VehicleKind.TIGER, VehicleKind.MAUS, VehicleKind.RATTE,
                VehicleKind.T34, VehicleKind.KV2, VehicleKind.IS2, VehicleKind.KV5}) {
            h.assertTrue(k.ww2() && k.tracked() && k.isGun(), k + " flags");
            h.assertTrue(k.spec.tubes().length >= 2, k + ": main gun + coaxial MG tubes");
            float[] mg = k.spec.tubes()[k.spec.tubes().length - 1];
            h.assertTrue(Math.abs(mg[0]) < 4.0f, k + ": MG tube x " + mg[0]);
        }
        float[][] rt = Specs.RATTE.tubes();
        h.assertTrue(rt[0][0] > 1.0f && rt[1][0] < -1.0f && Math.abs(rt[0][2] - 14.2f) < 0.05f, "Ratte twin barrels");
        h.assertTrue(VehicleKind.RATTE.magazine % 2 == 0, "Ratte magazine pairs");
        h.assertTrue(Ballistics.Profile.RW61.isShell() && Ballistics.Profile.RW61.tntKg > 100.0, "RW 61 charge");
        h.assertTrue(VehicleKind.STURMTIGER.artillery() && VehicleKind.STURMTIGER.elMax >= 85.0f, "Sturmtiger high-angle");
        h.assertTrue(!Ballistics.Profile.M13.isShell(), "M-13 is a rocket");
        h.assertTrue(VehicleKind.BM13.isMlrs() && !VehicleKind.BM13.isGun(), "BM-13 kind");
        h.assertTrue(Specs.BM13.tubes().length == VehicleKind.BM13.magazine, "BM-13: 16 rockets on 8 rails, got " + Specs.BM13.tubes().length);
        h.assertTrue(GunTests.class.getResource("/assets/aiarsenal/models/glb/rocket_m13.glb") != null, "rocket_m13.glb");
        try (InputStream in = GunTests.class.getResourceAsStream("/assets/aiarsenal/models/glb/bm13.glb")) {
            GlbModel m = GlbModel.read(in);
            for (String n : new String[]{"launcher_yaw", "launcher_pitch", "rocket_load", "jack_0", "jack_1", "wheel_0", "wheel_5", "plug_0", "plug_15", "steering_wheel"}) {
                h.assertTrue(m.nodeIndex(n) >= 0, "bm13: node " + n);
            }
        } catch (IOException e) {
            h.fail("bm13.glb: " + e);
        }
        try (InputStream in = GunTests.class.getResourceAsStream("/assets/aiarsenal/models/glb/ratte.glb")) {
            GlbModel m = GlbModel.read(in);
            for (String n : new String[]{"sec_yaw", "spkb1_L", "spkb2_R", "idlb2_L", "rw_L_32", "rw_R_32"}) {
                h.assertTrue(m.nodeIndex(n) >= 0, "ratte: node " + n);
            }
        } catch (IOException e) {
            h.fail("ratte.glb: " + e);
        }
        h.succeed();
    }

    /** Tiger I: an 8.8 cm round leaves the barrel and damages a vehicle 40 blocks ahead. */
    @GameTest(template = "arena", batch = "rw_gun", timeoutTicks = 500)
    public static void tiger_round_hits_vehicle(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos c = h.absolutePos(new BlockPos(5, 0, 5));
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.getX(), c.getZ());
        strip(level, c.getX() - 6, c.getZ() - 6, c.getX() + 6, c.getZ() + 50, y);
        TestChunks.path(level, Vec3.atCenterOf(c), Vec3.atCenterOf(c).add(0, 0, 50));
        GunVehicle t = spawn(level, ModEntities.TIGER.get(), c.getX() + 0.5, y, c.getZ() + 0.5, 0.0f);
        VehicleEntity tgt = spawn(level, ModEntities.GRAD.get(), c.getX() + 0.5, y, c.getZ() + 40.5, 90.0f);
        TestChunks.follow(t, tgt);
        t.fillMagazine();
        ServerPlayer gunner = mock(h);
        h.assertTrue(t.enterSeat(gunner, t.operatorSeat()), "gunner seat");   // r9: one seat drives + fires
        float hp0 = tgt.hp();
        int[] n = {0};
        h.onEachTick(() -> {
            n[0]++;
            look(gunner, tgt.position().add(0, 1.4, 0));
            t.handleControl(gunner, 0, 0, n[0] > 60 && n[0] < 400, false, 0);
            if (!tgt.isAlive() || tgt.hp() < hp0 - 1.0f) {
                h.assertTrue(t.loadedCount() < t.kind.magazine, "no round was expended");
                gunner.stopRiding();
                t.discard();
                if (tgt.isAlive()) tgt.discard();
                h.succeed();
            }
        });
    }

    /** 4 SPAAGs (Wirbelwind, ZSU-37, Gepard, Shilka): independent HE/AP ammo belts + radar-slaved SAMs seduced by flares. */
    @GameTest(template = "arena", batch = "rw_gun", timeoutTicks = 100)
    public static void spaag_dual_belts_and_radar_sam(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos c = h.absolutePos(new BlockPos(5, 0, 5));
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.getX(), c.getZ());
        for (VehicleKind k : new VehicleKind[]{VehicleKind.WIRBELWIND, VehicleKind.ZSU37, VehicleKind.GEPARD, VehicleKind.SHILKA}) {
            h.assertTrue(k.isSpaag() && k.isGun() && k.tracked(), k + ": SPAAG flags");
            GunVehicle.Cfg cfg = GunVehicle.cfg(k);
            h.assertTrue(cfg.modes().length == 3, k + ": 3 modes (HE belt, AP belt, SAM)");
            h.assertTrue(!cfg.primary().kinetic() && cfg.secondary().kinetic(), k + ": HE + AP belts");
            h.assertTrue(cfg.third() == Ballistics.Profile.AA_SAM, k + ": radar SAM mode");
        }
        GunVehicle shilka = spawn(level, ModEntities.SHILKA.get(), c.getX() + 0.5, y, c.getZ() + 0.5, 0.0f);
        ServerPlayer gunner = mock(h);
        try {
            shilka.fillMagazine();
            h.assertTrue(shilka.isDualBelt() && shilka.beltCapacity() == 30, "Shilka dual belt capacity");
            h.assertTrue(shilka.loadedOfType(0) == 30 && shilka.loadedOfType(1) == 30, "both belts initially loaded 30/30");
            h.assertTrue(shilka.radarActive() && shilka.radarRange() >= 250.0, "Shilka onboard radar active");
            h.assertTrue(shilka.enterSeat(gunner, shilka.operatorSeat()), "enter Shilka");
            // Fire 1 burst on HE belt (slot 0): only HE belt decreases; AP belt stays at 30
            shilka.handleControl(gunner, 0, 0, true, false, 0);
            shilka.tick();
            h.assertTrue(shilka.loadedOfType(0) == 29 && shilka.loadedOfType(1) == 30,
                    "HE shot must not consume AP belt: HE=" + shilka.loadedOfType(0) + " AP=" + shilka.loadedOfType(1));
            // Release trigger for 1 tick to clear fireCd, then switch to AP belt (slot 1) and fire
            shilka.handleControl(gunner, 0, 0, false, false, 0);
            shilka.tick();
            shilka.selectSlot(gunner, 1);
            shilka.handleControl(gunner, 0, 0, true, false, 0);
            shilka.tick();
            h.assertTrue(shilka.loadedOfType(0) == 29 && shilka.loadedOfType(1) == 29,
                    "AP shot must not consume HE belt: HE=" + shilka.loadedOfType(0) + " AP=" + shilka.loadedOfType(1));
        } finally {
            gunner.stopRiding();
            shilka.discard();
        }
        h.succeed();
    }
}
