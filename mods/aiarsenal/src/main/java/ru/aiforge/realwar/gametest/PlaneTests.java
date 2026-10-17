package ru.aiforge.realwar.gametest;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import ru.aiforge.realwar.aircraft.AirBombEntity;
import ru.aiforge.realwar.craft.CraftControlPacket;
import ru.aiforge.realwar.craft.CraftEntity;
import ru.aiforge.realwar.craft.CraftRegistry;
import ru.aiforge.realwar.craft.CraftWeapon;
import ru.aiforge.realwar.craft.PlaneEntity;
import ru.aiforge.realwar.glb.GlbModel;

/** Reply 11: WW2 aeroplanes - take-off run, trimmed level flight, stall, bomb release through the bay, AI gunners. */
@GameTestHolder("aiarsenal")
@PrefixGameTestTemplate(false)
public final class PlaneTests {
    private PlaneTests() {}

    private static PlaneEntity spawn(GameTestHelper h, EntityType<? extends PlaneEntity> type, Vec3 p, float yaw) {
        PlaneEntity e = type.create(h.getLevel());
        e.moveTo(p.x, p.y, p.z, yaw, 0f);
        e.initFresh();
        h.getLevel().addFreshEntity(e);
        TestChunks.follow(e);
        return e;
    }

    private static Vec3 groundAt(GameTestHelper h, int rx, int rz, int up) {
        BlockPos c = h.absolutePos(new BlockPos(rx, 0, rz));
        int top = h.getLevel().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.getX(), c.getZ());
        return new Vec3(c.getX() + 0.5, top + up, c.getZ() + 0.5);
    }

    @GameTest(template = "arena", batch = "rw_plane")
    public static void plane_models_have_animated_nodes(GameTestHelper h) {
        String[][] need = {
                {"bf109", "prop", "prop_disc", "gear_L", "gear_R", "gear_T", "ail_L", "ail_R", "flap_L", "flap_R", "elev", "rud"},
                {"yak3", "prop", "prop_disc", "gear_L", "gear_R", "gear_T", "ail_L", "ail_R", "flap_L", "flap_R", "elev", "rud"},
                {"he111", "prop_0", "prop_1", "prop_disc_0", "gear_L", "gear_R", "bay_L", "bay_R", "bomb_0", "bomb_7", "bombx_1",
                        "tur_0_yaw", "tur_2_pitch", "elev", "rud"},
                {"pe8", "prop_0", "prop_3", "prop_disc_3", "gear_L", "gear_R", "bomb_15", "bombk_3", "bombf_0", "tur_0_yaw",
                        "tur_4_pitch", "tur_1_ring", "elev", "rud"},
                {"su57", "gear_N", "gear_L", "gear_R", "gear_door_closed", "elev_L", "elev_R", "rud_L", "rud_R", "levcon_L", "ail_R",
                        "flap_L", "bay_L", "bay_R", "noz_L", "nozy_R", "ab_L", "abx_R", "glow_L", "pod_3", "kh38_5", "fab_5"}};
        for (String[] n : need) {
            try (java.io.InputStream in = PlaneTests.class.getResourceAsStream("/assets/aiarsenal/models/glb/" + n[0] + ".glb")) {
                h.assertTrue(in != null, "нет модели " + n[0]);
                GlbModel m = GlbModel.read(in);
                java.util.Set<String> names = new java.util.HashSet<>();
                for (GlbModel.Node nd : m.nodes) names.add(nd.name);
                for (int i = 1; i < n.length; i++) h.assertTrue(names.contains(n[i]), n[0] + ": нет узла " + n[i]);
            } catch (java.io.IOException ex) {
                h.fail(ex.toString());
            }
        }
        h.succeed();
    }

    /** Full throttle on the (superflat) runway, stick back once flying speed is reached: the Bf 109 must get airborne. */
    @GameTest(template = "arena", batch = "rw_plane", timeoutTicks = 900)
    public static void bf109_takes_off_from_flat_ground(GameTestHelper h) {
        // private elevated runway (the other test arenas on the ground would be in the way of a 400 m take-off run)
        Vec3 g = groundAt(h, 2, 2, 0);
        int ry = (int) g.y + 40;
        net.minecraft.server.level.ServerLevel l = h.getLevel();
        net.minecraft.world.level.block.state.BlockState st = net.minecraft.world.level.block.Blocks.STONE.defaultBlockState();
        for (int z = -4; z <= 460; z++) {
            if (z % 16 == 0) TestChunks.force(l, new Vec3(g.x, ry, g.z + z));
            for (int x = -4; x <= 4; x++) l.setBlock(BlockPos.containing(g.x + x, ry, g.z + z), st, 2);
        }
        Vec3 p = new Vec3(g.x, ry + 1, g.z);
        PlaneEntity c = spawn(h, CraftRegistry.BF109.get(), p, 0f);
        c.setEngine(true);
        double y0 = p.y;
        h.onEachTick(() -> {
            double v = c.vel.length();
            c.setForcedInput(1f, 0f, 0f, v > c.vStall * 1.1 && v > 10 ? 0.9f : 0f);
        });
        h.succeedWhen(() -> {
            h.assertTrue(c.isAlive(), "самолёт разрушен");
            h.assertTrue(c.airborne() && c.getY() > y0 + 5, String.format("не взлетел: v=%.1f vs=%.1f dy=%.1f", c.vel.length(), c.vStall, c.getY() - y0));
            c.discard();
        });
    }

    /**
     * r12 Su-57: full stick back at 120 m/s. Normal (FBW) mode holds the angle of attack near the 24 deg limit; the
     * super-manoeuvre mode (N) lets the nose go past 40 deg (TVC), and the afterburner lights above 86 % throttle.
     */
    @GameTest(template = "arena", batch = "rw_plane", timeoutTicks = 300)
    public static void su57_fbw_limit_vs_supermanoeuvre(GameTestHelper h) {
        Vec3 p = groundAt(h, 2, 2, 120);
        PlaneEntity a = spawn(h, CraftRegistry.SU57.get(), p, 0f);
        PlaneEntity b = spawn(h, CraftRegistry.SU57.get(), p.add(40, 0, 0), 0f);
        for (PlaneEntity c : new PlaneEntity[]{a, b}) {
            c.setEngine(true);
            c.setFlying(70.0);          // super-manoeuvres are flown slow: at 120 m/s the 9 g limit caps AoA in both modes
            c.aux2 = 1f;
        }
        b.setFlag(CraftEntity.FLAG_STAB, true);
        float[] maxA = new float[2];
        h.onEachTick(() -> {
            a.setForcedInput(1f, 0f, 0f, 8f);
            b.setForcedInput(1f, 0f, 0f, 8f);
            maxA[0] = Math.max(maxA[0], a.aoa);
            maxA[1] = Math.max(maxA[1], b.aoa);
        });
        h.runAfterDelay(70, () -> {
            h.assertTrue(maxA[0] < 30f, String.format("FBW limit exceeded: aoa %.1f", maxA[0]));
            h.assertTrue(maxA[1] > 38f, String.format("manoeuvre mode aoa only %.1f", maxA[1]));
            h.assertTrue(a.ab > 0.5f, String.format("no afterburner: ab=%.2f thr=%.2f", a.ab, a.aux2));
            a.discard();
            b.discard();
            h.succeed();
        });
    }

    /** Hands-off trimmed cruise: the He 111 keeps altitude and wings level. */
    @GameTest(template = "arena", batch = "rw_plane", timeoutTicks = 300)
    public static void he111_trimmed_level_flight(GameTestHelper h) {
        Vec3 p = groundAt(h, 2, 2, 90);
        PlaneEntity c = spawn(h, CraftRegistry.HE111.get(), p, 0f);
        c.setFlying(85.0);
        c.setForcedInput(0f);
        double y0 = p.y;
        h.runAfterDelay(160, () -> {
            double dy = c.getY() - y0;
            h.assertTrue(Math.abs(dy) < 8.0, String.format("уход высоты %.1f", dy));
            h.assertTrue(Math.abs(c.roll) < 6f, "крен " + c.roll);
            h.assertTrue(c.vel.length() > c.vStall, String.format("скорость %.1f < %.1f", c.vel.length(), c.vStall));
            c.discard();
            h.succeed();
        });
    }

    /** Engine idle, far below the stall speed: the Yak-3 must sink and drop its nose (no "hanging" in the air). */
    @GameTest(template = "arena", batch = "rw_plane", timeoutTicks = 300)
    public static void yak3_stalls_and_sinks_when_too_slow(GameTestHelper h) {
        Vec3 p = groundAt(h, 2, 2, 90);
        PlaneEntity c = spawn(h, CraftRegistry.YAK3.get(), p, 0f);
        c.setFlying(25.0);
        c.aux2 = 0f;
        c.pitch = 10f;
        c.setForcedInput(-1f, 0f, 0f, 0.6f);     // throttle back, stick held back
        double y0 = p.y;
        h.runAfterDelay(80, () -> {
            h.assertTrue(c.getY() < y0 - 3.0, String.format("не проседает: dy=%.1f", c.getY() - y0));
            h.assertTrue(c.vel.y < -3.0 || c.pitch < 5f, String.format("нос не опускается: pitch=%.1f vy=%.1f", c.pitch, c.vel.y));
            c.discard();
            h.succeed();
        });
    }

    /** Pilot selects the SC 250s: the bay doors open first, then bombs leave the aircraft. */
    @GameTest(template = "arena", batch = "rw_plane", timeoutTicks = 300)
    public static void he111_opens_bay_and_drops_bombs(GameTestHelper h) {
        Vec3 p = groundAt(h, 2, 2, 60);
        PlaneEntity c = spawn(h, CraftRegistry.HE111.get(), p, 0f);
        c.setFlying(80.0);
        FakePlayer fp = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "rw-pilot"));
        fp.moveTo(p.x, p.y, p.z);
        fp.startRiding(c, true);
        int li = c.kind.loadIndex(CraftWeapon.SC250);
        h.runAfterDelay(2, () -> {
            for (int k = 0; k < 4 && c.weaponFor(0) != li; k++) c.handleControl(fp, false, CraftControlPacket.A_WEAPON);
            h.assertTrue(c.weaponFor(0) == li, "не выбраны SC 250: " + c.weaponFor(0) + " seat " + c.seatOf(fp));
        });
        h.onEachTick(() -> {
            if (h.getTick() > 3) c.handleControl(fp, true, 0);
        });
        h.succeedWhen(() -> {
            AABB box = c.getBoundingBox().inflate(30, 120, 30);
            int bombs = h.getLevel().getEntitiesOfClass(AirBombEntity.class, box).size();
            h.assertTrue(c.bayPos > 0.84f, "створки не открыты " + c.bayPos);
            h.assertTrue(bombs > 0 && c.ammo(li) < 8, "бомбы не сброшены: " + bombs + " / " + c.ammo(li));
            fp.stopRiding();
            for (AirBombEntity b : h.getLevel().getEntitiesOfClass(AirBombEntity.class, box)) b.discard();
            c.discard();
        });
    }

    /** With a pilot aboard, the Pe-8 defensive gunners engage an airborne hostile on their own. */
    @GameTest(template = "arena", batch = "rw_plane", timeoutTicks = 300)
    public static void pe8_gunners_engage_air_target(GameTestHelper h) {
        Vec3 p = groundAt(h, 2, 2, 60);
        PlaneEntity c = spawn(h, CraftRegistry.PE8.get(), p, 0f);
        c.setFlying(70.0);
        FakePlayer fp = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "rw-pilot2"));
        fp.moveTo(p.x, p.y, p.z);
        fp.startRiding(c, true);
        Phantom ph = EntityType.PHANTOM.create(h.getLevel());
        ph.moveTo(p.x + 6, p.y + 14, p.z - 40, 0f, 0f);
        ph.setNoAi(true);
        ph.setNoGravity(true);
        ph.setInvulnerable(true);
        h.getLevel().addFreshEntity(ph);
        int gl = c.kind.loadIndex(CraftWeapon.GUNNERS);
        int a0 = c.ammo(gl);
        h.succeedWhen(() -> {
            h.assertTrue(c.ammo(gl) < a0, "стрелки не стреляют: " + c.ammo(gl));
            fp.stopRiding();
            ph.discard();
            c.discard();
        });
    }
}
