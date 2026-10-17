package ru.aiforge.realwar.gametest;

import com.mojang.authlib.GameProfile;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import ru.aiforge.realwar.craft.BoatEntity;
import ru.aiforge.realwar.craft.CraftEntity;
import ru.aiforge.realwar.craft.CraftKind;
import ru.aiforge.realwar.craft.CraftOrdnance;
import ru.aiforge.realwar.craft.CraftRegistry;
import ru.aiforge.realwar.craft.Flares;
import ru.aiforge.realwar.craft.HeliEntity;
import ru.aiforge.realwar.glb.GlbModel;
import ru.aiforge.realwar.handheld.AtMissileEntity;
import ru.aiforge.realwar.handheld.HandheldKind;

/** Reply 6: helicopters (flight model, hover mode), boats (planing hull, waterjets), submarine (ballast), weapons. */
@GameTestHolder("aiarsenal")
@PrefixGameTestTemplate(false)
public final class CraftTests {
    private CraftTests() {}

    private static int ground(GameTestHelper h, int rx, int rz) {
        BlockPos c = h.absolutePos(new BlockPos(rx, 0, rz));
        return h.getLevel().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.getX(), c.getZ());
    }

    /** Clears a column of air above a pad so a hovering helicopter cannot touch anything. */
    private static void clearSky(GameTestHelper h, int rx, int rz, int half, int top, int up) {
        ServerLevel l = h.getLevel();
        BlockPos c = h.absolutePos(new BlockPos(rx, 0, rz));
        for (int dx = -half; dx <= half; dx++)
            for (int dz = -half; dz <= half; dz++)
                for (int y = top; y <= top + up; y++) l.setBlock(new BlockPos(c.getX() + dx, y, c.getZ() + dz), Blocks.AIR.defaultBlockState(), 2);
    }

    /** Water pit dug into the ground: x in [x0,x1], z in [z0,z1] (relative), depth blocks; returns the water surface Y. */
    private static int pit(GameTestHelper h, int x0, int x1, int z0, int z1, int depth) {
        return pit(h, h.absolutePos(BlockPos.ZERO), x0, x1, z0, z1, depth);
    }

    private static int pit(GameTestHelper h, BlockPos o, int x0, int x1, int z0, int z1, int depth) {
        ServerLevel l = h.getLevel();
        for (int z = z0 - 16; z <= z1 + 16; z += 16) for (int x = x0 - 16; x <= x1 + 16; x += 16) TestChunks.force(l, Vec3.atCenterOf(o.offset(x, 0, z)));
        int top = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, o.getX() + (x0 + x1) / 2, o.getZ() + (z0 + z1) / 2);
        BlockState water = Blocks.WATER.defaultBlockState(), stone = Blocks.STONE.defaultBlockState(), air = Blocks.AIR.defaultBlockState();
        // superflat test world: only 4 blocks above bedrock - deeper basins become a raised stone tank
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

    private static <T extends CraftEntity> T spawn(GameTestHelper h, EntityType<T> type, Vec3 p, float yaw) {
        T e = type.create(h.getLevel());
        e.moveTo(p.x, p.y, p.z, yaw, 0f);
        e.initFresh();
        h.getLevel().addFreshEntity(e);
        TestChunks.follow(e);
        return e;
    }

    private static float yawDelta(CraftEntity e, float y0) {
        return Math.abs(Mth.wrapDegrees(e.getYRot() - y0));
    }

    // ------------------------------------------------------------------------------------------- models

    @GameTest(template = "arena", batch = "rw_craft")
    public static void craft_models_nodes_and_budget(GameTestHelper h) {
        String[][] need = {
                {"ka52", "rotor_upper", "rotor_lower", "rotor_disc", "rotor_disc2", "gun_yaw", "gun_pitch", "gear_nose", "gear_L", "gear_R", "tads", "atgm_0", "atgm_11"},
                {"ah64", "rotor_main", "rotor_tail", "rotor_disc", "tail_disc", "gun_yaw", "gun_pitch", "tads", "atgm_0", "atgm_7"},
                {"raptor", "turret_yaw", "turret_pitch", "prop_L", "prop_R", "rudder_L", "rudder_R", "radar"},
                {"cb90", "turret_yaw", "turret_pitch", "jet_L", "jet_R"},
                {"sub636", "prop", "rudder", "planes_bow", "planes_stern", "periscope", "snorkel", "radar"},
                {"craft_ordnance", "vikhr", "hellfire", "s8", "hydra", "torpedo"}};
        for (String[] n : need) {
            for (String suffix : n[0].equals("craft_ordnance") ? new String[]{""} : new String[]{"", "_lod1"}) {
                String path = "/assets/aiarsenal/models/glb/" + n[0] + suffix + ".glb";
                try (InputStream in = CraftTests.class.getResourceAsStream(path)) {
                    h.assertTrue(in != null, "нет " + path);
                    GlbModel m = GlbModel.read(in);
                    Set<String> names = new HashSet<>();
                    int tris = 0;
                    for (GlbModel.Node nd : m.nodes) {
                        names.add(nd.name);
                        if (nd.mesh >= 0) for (GlbModel.Primitive p : m.meshes.get(nd.mesh).prims) tris += p.triangleCount();
                    }
                    h.assertTrue(tris > 400 && tris < 60000, n[0] + suffix + ": треугольников " + tris);
                    if (suffix.isEmpty()) for (int i = 1; i < n.length; i++) h.assertTrue(names.contains(n[i]), n[0] + ": нет узла " + n[i]);
                } catch (java.io.IOException ex) {
                    h.fail(n[0] + ": " + ex);
                }
            }
        }
        for (CraftKind k : CraftKind.values()) h.assertTrue(k.model.startsWith("models/glb/"), "путь модели " + k);
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------- helicopters

    @GameTest(template = "arena", batch = "rw_craft", timeoutTicks = 300)
    public static void heli_hover_mode_holds_altitude_and_level(GameTestHelper h) {
        int top = ground(h, 2, 2);
        clearSky(h, 2, 2, 9, top, 70);
        Vec3 p = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(2, 0, 2))).add(0, top + 40 - h.absolutePos(BlockPos.ZERO).getY(), 0);
        HeliEntity c = spawn(h, CraftRegistry.KA52.get(), p, 0f);
        c.setEngine(true);
        c.setRotorState(1.0f, 0.66f);
        c.setFlag(CraftEntity.FLAG_STAB, true);
        c.roll = 12f;                      // disturbed attitude: hover mode must level the airframe
        c.setForcedInput(0f);
        double y0 = p.y;
        h.runAfterDelay(120, () -> {
            h.assertTrue(Math.abs(c.getY() - y0) < 3.0, "висение: уход высоты " + String.format("%.2f", c.getY() - y0) + " м");
            h.assertTrue(Math.abs(c.roll) < 3f, "висение: крен не убран " + c.roll);
            h.assertTrue(c.vel.length() < 2.5, "висение: скорость " + c.vel.length());
            h.assertTrue(c.rotor() > 0.9f, "обороты НВ " + c.rotor());
            c.discard();
            h.succeed();
        });
    }

    /** Regression (found in game): hover mode switched on during a full-collective climb, with a mouse yaw input,
     *  must capture the altitude smoothly - no collective dump into a fast sink / vortex ring. */
    @GameTest(template = "arena", batch = "rw_craft", timeoutTicks = 400)
    public static void heli_hover_mode_engaged_in_a_climb_does_not_sink(GameTestHelper h) {
        int top = ground(h, 2, 2);
        clearSky(h, 2, 2, 9, top, 110);
        Vec3 p = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(2, 0, 2))).add(0, top + 60 - h.absolutePos(BlockPos.ZERO).getY(), 0);
        HeliEntity c = spawn(h, CraftRegistry.KA52.get(), p, 0f);
        c.setEngine(true);
        c.setRotorState(1.0f, 1.0f);
        c.vel = new Vec3(0, 8.5, 0);
        c.setFlag(CraftEntity.FLAG_STAB, true);
        c.setForcedInput(0f, 0f, 12f, 0f);
        double[] minVy = {0}, maxY = {p.y};
        h.runAfterDelay(15, () -> c.setForcedInput(0f));
        h.onEachTick(() -> {
            c.setOnGround(true);          // stale network flag, as on the pilot's client - physics must not trust it
            minVy[0] = Math.min(minVy[0], c.vel.y);
            maxY[0] = Math.max(maxY[0], c.getY());
        });
        h.runAfterDelay(260, () -> {
            String st = String.format("minVy %.1f, overshoot %.1f m, final dY %.1f m, Vy %.1f, coll %.2f",
                    minVy[0], maxY[0] - p.y, c.getY() - p.y, c.vel.y, c.collective());
            h.assertTrue(minVy[0] > -6.0, "висение при наборе: провал " + st);
            h.assertTrue(Math.abs(c.vel.y) < 1.0, "висение при наборе: не стабилизировался " + st);
            c.discard();
            h.succeed();
        });
    }

    @GameTest(template = "arena", batch = "rw_craft", timeoutTicks = 300)
    public static void heli_mouse_cyclic_rolls_manual_but_not_in_hover_mode(GameTestHelper h) {
        int top = ground(h, 2, 2);
        clearSky(h, 2, 2, 12, top, 90);
        BlockPos o = h.absolutePos(BlockPos.ZERO);
        Vec3 pa = Vec3.atBottomCenterOf(o.offset(-10, 0, 2)).add(0, top + 60 - o.getY(), 0);
        Vec3 pb = Vec3.atBottomCenterOf(o.offset(14, 0, 2)).add(0, top + 60 - o.getY(), 0);
        HeliEntity a = spawn(h, CraftRegistry.AH64.get(), pa, 0f);
        HeliEntity b = spawn(h, CraftRegistry.AH64.get(), pb, 0f);
        for (HeliEntity c : new HeliEntity[]{a, b}) {
            c.setEngine(true);
            c.setRotorState(1.0f, 0.68f);
            c.vel = c.basis()[0].scale(30.0);     // cruising 108 km/h
            c.setForcedInput(0f, 0f, 3.0f, 0f);  // mouse right 3 deg/tick
        }
        b.setFlag(CraftEntity.FLAG_STAB, true);
        float ya = a.getYRot(), yb = b.getYRot();
        h.runAfterDelay(10, () -> { a.setForcedInput(0f); b.setForcedInput(0f); });
        h.runAfterDelay(22, () -> {
            h.assertTrue(Math.abs(a.roll) > 8f, "ручной режим: мышь не даёт крен (" + a.roll + ")");
            h.assertTrue(Math.abs(b.roll) < 3f, "режим висения: крен должен быть отключён (" + b.roll + ")");
            h.assertTrue(yawDelta(b, yb) > 4f, "режим висения: мышь X должна разворачивать (" + yawDelta(b, yb) + "°)");
        });
        h.runAfterDelay(80, () -> {
            h.assertTrue(yawDelta(a, ya) > 4f, "координированный разворот с креном: курс изменился лишь на " + yawDelta(a, ya) + "°");
            a.discard();
            b.discard();
            h.succeed();
        });
    }

    @GameTest(template = "arena", batch = "rw_craft", timeoutTicks = 200)
    public static void heli_without_power_descends_in_autorotation(GameTestHelper h) {
        int top = ground(h, 2, 2);
        clearSky(h, 2, 2, 9, top, 90);
        BlockPos o = h.absolutePos(BlockPos.ZERO);
        Vec3 p = Vec3.atBottomCenterOf(o.offset(2, 0, 2)).add(0, top + 80 - o.getY(), 0);
        HeliEntity c = spawn(h, CraftRegistry.KA52.get(), p, 0f);
        c.setEngine(false);
        c.setRotorState(1.0f, 0.2f);
        c.setForcedInput(0f);
        h.runAfterDelay(40, () -> {
            h.assertTrue(c.vel.y < -3.0, "без двигателя вертолёт должен снижаться: Vy " + c.vel.y);
            h.assertTrue(c.rotor() > 0.45f, "авторотация должна поддерживать обороты НВ: " + c.rotor());
            c.discard();
            h.succeed();
        });
    }

    // ------------------------------------------------------------------------------------------- boats

    @GameTest(template = "arena", batch = "rw_craft", timeoutTicks = 300)
    public static void boat_floats_at_its_draft_and_accelerates(GameTestHelper h) {
        int surf = pit(h, -5, 5, -2, 60, 4);
        BlockPos o = h.absolutePos(BlockPos.ZERO);
        Vec3 p = new Vec3(o.getX() + 0.5, surf - 0.9, o.getZ() + 20.5);
        BoatEntity b = spawn(h, CraftRegistry.RAPTOR.get(), p, 0f);
        b.setEngine(true);
        h.runAfterDelay(30, () -> {
            double draft = surf - b.getY();
            h.assertTrue(draft > 0.3 && draft < 1.6, "«Раптор» сидит неправильно: осадка " + String.format("%.2f", draft));
            b.setForcedInput(1f);          // telegraph ahead
        });
        h.runAfterDelay(110, () -> {
            double sp = Math.sqrt(b.vel.x * b.vel.x + b.vel.z * b.vel.z);
            double moved = Math.sqrt(Math.pow(b.getX() - p.x, 2) + Math.pow(b.getZ() - p.z, 2));
            h.assertTrue(sp > 3.0, "«Раптор» не разгоняется: " + String.format("%.2f", sp) + " м/с");
            h.assertTrue(moved > 4.0, "«Раптор» не сдвинулся: " + String.format("%.2f", moved));
            h.assertTrue(b.getY() > surf - 2.0, "«Раптор» тонет: y " + b.getY() + " / вода " + surf);
            b.discard();
            h.succeed();
        });
    }

    @GameTest(template = "arena", batch = "rw_craft", timeoutTicks = 300)
    public static void waterjets_turn_on_the_spot_props_do_not(GameTestHelper h) {
        int surf = pit(h, -14, 14, -2, 30, 4);
        BlockPos o = h.absolutePos(BlockPos.ZERO);
        BoatEntity jet = spawn(h, CraftRegistry.CB90.get(), new Vec3(o.getX() + 7.5, surf - 0.8, o.getZ() + 14.5), 0f);
        BoatEntity prop = spawn(h, CraftRegistry.RAPTOR.get(), new Vec3(o.getX() - 6.5, surf - 0.9, o.getZ() + 14.5), 0f);
        jet.setEngine(true);
        prop.setEngine(true);
        float yj = jet.getYRot(), yp = prop.getYRot();
        h.runAfterDelay(20, () -> { jet.setForcedInput(0f, 1f); prop.setForcedInput(0f, 1f); });
        h.runAfterDelay(80, () -> {
            float dj = yawDelta(jet, yj), dp = yawDelta(prop, yp);
            h.assertTrue(dj > 8f, "CB90 (водомёты) должен разворачиваться на месте: " + dj + "°");
            h.assertTrue(dp < dj * 0.5f, "винтовой «Раптор» без хода не должен крутиться как водомётный: " + dp + "° vs " + dj + "°");
            jet.discard();
            prop.discard();
            h.succeed();
        });
    }

    @GameTest(template = "arena", batch = "rw_craft", timeoutTicks = 500)
    public static void submarine_floods_to_dive_and_blows_to_surface(GameTestHelper h) {
        BlockPos o = h.absolutePos(BlockPos.ZERO).offset(2400, 0, 0);   // fresh terrain far from the other test plots
        int surf = pit(h, o, -8, 8, -2, 82, 22);
        Vec3 p = new Vec3(o.getX() + 0.5, surf - 6.2, o.getZ() + 40.5);
        BoatEntity s = spawn(h, CraftRegistry.SUB636.get(), p, 0f);
        s.setEngine(true);
        s.setForcedInput(0f);
        double[] y = new double[2];
        h.runAfterDelay(40, () -> {
            y[0] = s.getY();
            h.assertTrue(Math.abs(surf - s.getY() - 6.2) < 2.5, "ПЛ в надводном положении: осадка " + (surf - s.getY()));
            s.setBallast(1.03f);           // main ballast flooded
        });
        h.runAfterDelay(160, () -> {
            y[1] = s.getY();
            h.assertTrue(y[0] - y[1] > 2.0, "приём балласта: ПЛ должна погружаться (" + String.format("%.2f", y[0] - y[1]) + " м)");
            s.setBallast(0f);              // emergency blow
        });
        h.runAfterDelay(300, () -> {
            h.assertTrue(s.getY() - y[1] > 1.0, "продувка: ПЛ должна всплывать (" + String.format("%.2f", s.getY() - y[1]) + " м)");
            s.discard();
            h.succeed();
        });
    }

    // ------------------------------------------------------------------------------------------- weapons

    @GameTest(template = "arena", batch = "rw_craft", timeoutTicks = 200)
    public static void flares_decoy_an_igla(GameTestHelper h) {
        int top = ground(h, 2, 2);
        clearSky(h, 2, 2, 9, top, 50);
        BlockPos o = h.absolutePos(BlockPos.ZERO);
        Vec3 p = Vec3.atBottomCenterOf(o.offset(2, 0, 2)).add(0, top + 30 - o.getY(), 0);
        HeliEntity c = spawn(h, CraftRegistry.KA52.get(), p, 0f);
        c.setEngine(true);
        c.setRotorState(1.0f, 0.66f);
        c.setFlag(CraftEntity.FLAG_STAB, true);
        c.setForcedInput(0f);
        Vec3 from = p.add(110, -20, 0);
        TestChunks.path(h.getLevel(), from, p);
        FakePlayer shooter = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "rw-igla"));
        shooter.moveTo(from.x, from.y, from.z);
        int s0 = Flares.seduced;
        Flares.forceResult = true;
        AtMissileEntity.launch(h.getLevel(), shooter, HandheldKind.IGLA, from, p.subtract(from).normalize(), c.getId(), null);
        h.runAfterDelay(4, () -> {
            int before = c.ammo(c.kind.loadIndex(ru.aiforge.realwar.craft.CraftWeapon.FLARES));
            c.fireFlares();
            // regression: the counter must change through SynchedEntityData (dirty -> sent to clients)
            h.assertTrue(c.ammo(c.kind.loadIndex(ru.aiforge.realwar.craft.CraftWeapon.FLARES)) == before - 4 && c.getEntityData().isDirty(),
                    "расход ловушек не синхронизируется с клиентом");
        });
        h.succeedWhen(() -> {
            h.assertTrue(Flares.seduced > s0, "ловушки не увели «Иглу»");
            Flares.forceResult = null;
            c.discard();
        });
    }

    @GameTest(template = "arena", batch = "rw_craft", timeoutTicks = 200)
    public static void vikhr_rides_the_beam_to_the_target(GameTestHelper h) {
        ServerLevel l = h.getLevel();
        int top = ground(h, 2, 2);
        BlockPos o = h.absolutePos(BlockPos.ZERO);
        Vec3 from = new Vec3(o.getX() + 2.5, top + 6, o.getZ() + 2.5);
        Vec3 tp = from.add(0, -4.6, 140);
        TestChunks.path(l, from, tp);
        IronGolem g = EntityType.IRON_GOLEM.create(l);
        g.moveTo(tp.x, top, tp.z, 0f, 0f);
        g.setNoAi(true);
        l.addFreshEntity(g);
        Vec3 aim = new Vec3(tp.x, top + 1.4, tp.z);
        Vec3 dir = aim.subtract(from).normalize();
        int d0 = CraftOrdnance.detonations;
        h.runAfterDelay(20, () -> {      // forced chunks need a few ticks to become entity-ticking
            CraftOrdnance m = CraftOrdnance.launch(l, CraftOrdnance.Type.VIKHR, null, from, dir.add(0.06, 0.04, 0).normalize(), Vec3.ZERO, -1);
            m.setBeam(from, dir);
        });
        h.succeedWhen(() -> h.assertTrue(!g.isAlive() || g.getHealth() < g.getMaxHealth() * 0.5f,
                "«Вихрь» не поразил цель по лучу: " + (CraftOrdnance.detonations > d0 ? CraftOrdnance.lastDetonation : "не взорвался") + ", цель " + g.blockPosition().toShortString()));
    }

    /** r7: 3M-14 Kalibr - submerged launch, capsule ascent, booster at the surface, cruise and dive onto the point. */
    @GameTest(template = "arena", batch = "rw_kalibr", timeoutTicks = 500)
    public static void kalibr_launches_from_under_water_and_hits_the_point(GameTestHelper h) {
        ServerLevel l = h.getLevel();
        BlockPos o = h.absolutePos(BlockPos.ZERO).offset(-2400, 0, 0);
        int surf = pit(h, o, -3, 3, -3, 3, 12);
        Vec3 from = new Vec3(o.getX() + 0.5, surf - 8.0, o.getZ() + 0.5);
        int g = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, o.getX(), o.getZ() + 700);
        Vec3 tp = new Vec3(o.getX() + 0.5, g, o.getZ() + 700.5);
        TestChunks.path(l, from, tp.add(0, 60, 0));
        int d0 = CraftOrdnance.detonations;
        h.runAfterDelay(20, () -> {
            CraftOrdnance k = CraftOrdnance.launch(l, CraftOrdnance.Type.KALIBR, null, from, new Vec3(0, 0, 1), Vec3.ZERO, -1);
            k.v = new Vec3(0, 6, 4);
            k.setSpot(tp);
            TestChunks.follow(k);
        });
        h.succeedWhen(() -> {
            h.assertTrue(CraftOrdnance.detonations > d0 && CraftOrdnance.lastDetonation.startsWith("KALIBR"), "Kalibr has not detonated yet: " + CraftOrdnance.lastDetonation);
            double miss = CraftOrdnance.lastDetonationPos.distanceTo(tp);
            h.assertTrue(miss < 12, "Kalibr missed: " + String.format("%.1f", miss) + " m, " + CraftOrdnance.lastDetonation);
        });
    }

    /** r7: a submarine resting on the sea bed keeps its buoyancy (columns used to read "dry") and can blow to rise. */
    @GameTest(template = "arena", batch = "rw_kalibr", timeoutTicks = 640)
    public static void submarine_on_the_bottom_blows_and_rises(GameTestHelper h) {
        BlockPos o = h.absolutePos(BlockPos.ZERO).offset(0, 0, 2400);
        int surf = pit(h, o, -8, 8, -2, 82, 17);
        Vec3 p = new Vec3(o.getX() + 0.5, surf - 6.2, o.getZ() + 40.5);
        BoatEntity s = spawn(h, CraftRegistry.SUB636.get(), p, 0f);
        s.setEngine(true);
        s.setForcedInput(0f);
        s.setBallast(1.03f);
        double[] y = new double[1];
        h.runAfterDelay(320, () -> {
            y[0] = s.getY();
            h.assertTrue(surf - s.getY() > 13.0, "sub should be on the bottom: keel " + String.format("%.2f", surf - s.getY()) + " m");
            h.assertTrue(s.subFrac > 0.97f, "on the bottom the hull must count as submerged: " + s.subFrac);
            h.assertTrue(Math.abs(s.pitch) < 8f, "hull must lie level on the bottom, pitch " + String.format("%.1f", s.pitch));
            s.setBallast(0f);
        });
        h.runAfterDelay(560, () -> {
            h.assertTrue(s.getY() - y[0] > 2.0, "blowing on the bottom: sub must rise (" + String.format("%.2f", s.getY() - y[0]) + " m)");
            s.discard();
            h.succeed();
        });
    }
}
