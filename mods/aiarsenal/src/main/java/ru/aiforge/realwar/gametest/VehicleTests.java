/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.authlib.GameProfile
 *  net.minecraft.commands.arguments.EntityAnchorArgument$Anchor
 *  net.minecraft.core.BlockPos
 *  net.minecraft.gametest.framework.GameTest
 *  net.minecraft.gametest.framework.GameTestHelper
 *  net.minecraft.resources.ResourceLocation
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.server.level.ServerPlayer
 *  net.minecraft.util.Mth
 *  net.minecraft.world.effect.MobEffectInstance
 *  net.minecraft.world.effect.MobEffects
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.entity.EntityType
 *  net.minecraft.world.entity.monster.Phantom
 *  net.minecraft.world.item.Item
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.item.crafting.RecipeManager
 *  net.minecraft.world.level.ItemLike
 *  net.minecraft.world.level.Level
 *  net.minecraft.world.level.block.Blocks
 *  net.minecraft.world.level.levelgen.Heightmap$Types
 *  net.minecraft.world.phys.AABB
 *  net.minecraft.world.phys.Vec3
 *  net.minecraftforge.common.util.FakePlayer
 *  net.minecraftforge.gametest.GameTestHolder
 *  net.minecraftforge.gametest.PrefixGameTestTemplate
 */
package ru.aiforge.realwar.gametest;

import com.mojang.authlib.GameProfile;
import ru.aiforge.realwar.glb.GlbModel;
import ru.aiforge.realwar.item.VehicleItem;
import ru.aiforge.realwar.ordnance.AirTargets;
import ru.aiforge.realwar.ordnance.Ballistics;
import ru.aiforge.realwar.ordnance.RocketEntity;
import ru.aiforge.realwar.ordnance.SamMissileEntity;
import ru.aiforge.realwar.radar.RadarNet;
import ru.aiforge.realwar.radar.RadarVehicleEntity;
import ru.aiforge.realwar.registry.ModEntities;
import ru.aiforge.realwar.registry.ModItems;
import ru.aiforge.realwar.vehicle.FlakEntity;
import ru.aiforge.realwar.vehicle.LauncherVehicle;
import ru.aiforge.realwar.vehicle.MlrsEntity;
import ru.aiforge.realwar.vehicle.SamLauncherEntity;
import ru.aiforge.realwar.vehicle.VehicleEntity;
import ru.aiforge.realwar.vehicle.VehicleKind;
import ru.aiforge.realwar.vehicle.VehicleSpec;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(value="aiarsenal")
@PrefixGameTestTemplate(value=false)
public final class VehicleTests {
    private static final String T = "arena";

    private VehicleTests() {
    }

    private static int surfacePad(GameTestHelper h, int rx, int rz, int half) {
        ServerLevel level = h.getLevel();
        BlockPos c = h.absolutePos(new BlockPos(rx, 0, rz));
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.getX(), c.getZ());
        for (int dx = -half; dx <= half; ++dx) {
            for (int dz = -half; dz <= half; ++dz) {
                for (int y = top - 1; y <= top + 60; ++y) {
                    level.setBlock(new BlockPos(c.getX() + dx, y, c.getZ() + dz), y == top - 1 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
        return top;
    }

    private static <T extends VehicleEntity> T spawn(ServerLevel level, EntityType<T> type, double x, double y, double z, float yaw) {
        VehicleEntity e = (VehicleEntity)type.create((Level)level);
        e.moveTo(x, y, z, yaw, 0.0f);
        level.addFreshEntity((Entity)e);
        return (T)(e);
    }

    private static ServerPlayer mockPlayer(GameTestHelper h) {
        return new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "rw-test"));
    }

    private static Phantom spawnTarget(ServerLevel level, Vec3 at) {
        Phantom p = (Phantom)EntityType.PHANTOM.create((Level)level);
        p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
        p.setNoAi(true);
        p.setNoGravity(true);
        p.setInvulnerable(false);
        p.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 100000, 0, false, false));
        level.addFreshEntity((Entity)p);
        return p;
    }

    @GameTest(template="arena", batch="rw_veh")
    public static void vehicle_models_and_specs_consistent(GameTestHelper h) {
        int[] seats = new int[]{2, 2, 2, 1, 2, 1};
        int[] mags = new int[]{40, 12, 4, 0, 0, 0};
        VehicleKind[] ks = VehicleKind.values();
        for (int i = 0; i < ks.length; ++i) {
            VehicleKind k = ks[i];
            if (i >= seats.length) continue;   // YARS + gun vehicles: own model/rig tests
            VehicleSpec s = k.spec;
            h.assertTrue(s.seatCount() == seats[i], String.valueOf(k) + ": \u043c\u0435\u0441\u0442 " + s.seatCount());
            if (!k.isFlak()) {
                h.assertTrue(s.tubeCount() == mags[i] && k.magazine == mags[i], String.valueOf(k) + ": \u0442\u0440\u0443\u0431 " + s.tubeCount() + " \u2260 \u0431\u043e\u0435\u043a\u043e\u043c\u043f\u043b\u0435\u043a\u0442 " + k.magazine);
            }
            h.assertTrue(s.seatRoles()[0].equals(k.isFlak() ? "gunner" : (k == VehicleKind.RADAR_POST ? "operator" : "driver")), String.valueOf(k) + ": \u043f\u0435\u0440\u0432\u043e\u0435 \u043c\u0435\u0441\u0442\u043e \u2014 " + s.seatRoles()[0]);
            if (!k.isFlak() && k != VehicleKind.RADAR_POST) {
                h.assertTrue(s.seatRoles()[1].equals("gunner"), String.valueOf(k) + ": \u0432\u0442\u043e\u0440\u043e\u0435 \u043c\u0435\u0441\u0442\u043e \u2014 \u043e\u043f\u0435\u0440\u0430\u0442\u043e\u0440");
            }
            try (InputStream in = VehicleTests.class.getResourceAsStream("/assets/aiarsenal/models/glb/" + s.id() + ".glb");){
                h.assertTrue(in != null, "\u043d\u0435\u0442 GLB " + s.id());
                GlbModel m = GlbModel.read(in);
                HashSet<String> names = new HashSet<String>();
                int tris = 0;
                for (GlbModel.Node n : m.nodes) {
                    names.add(n.name);
                    if (n.mesh < 0) continue;
                    for (GlbModel.Primitive p : m.meshes.get((int)n.mesh).prims) {
                        tris += p.triangleCount();
                    }
                }
                h.assertTrue(tris > 500 && tris < 250000, s.id() + ": \u0442\u0440\u0435\u0443\u0433\u043e\u043b\u044c\u043d\u0438\u043a\u043e\u0432 " + tris);
                if (k.isFlak()) {
                    for (String n : new String[]{"turret_yaw", "gun_pitch", "barrel_0", "barrel_1", "ammo_box_0", "ammo_box_1"}) {
                        h.assertTrue(names.contains(n), s.id() + ": \u043d\u0435\u0442 \u0443\u0437\u043b\u0430 " + n);
                    }
                } else if (k.isRadar()) {
                    h.assertTrue(names.contains("radar_yaw"), s.id() + ": \u043d\u0435\u0442 \u0443\u0437\u043b\u0430 radar_yaw");
                    for (int j = 0; j < s.jacks().length; ++j) {
                        h.assertTrue(names.contains("jack_" + j), s.id() + ": \u043d\u0435\u0442 \u0443\u0437\u043b\u0430 jack_" + j);
                    }
                } else {
                    h.assertTrue(names.contains("launcher_pitch"), s.id() + ": \u043d\u0435\u0442 \u0443\u0437\u043b\u0430 launcher_pitch");
                    for (int j = 0; j < s.jacks().length; ++j) {
                        h.assertTrue(names.contains("jack_" + j), s.id() + ": \u043d\u0435\u0442 \u0443\u0437\u043b\u0430 jack_" + j);
                    }
                }
                for (int w = 0; w < s.wheels().length; ++w) {
                    h.assertTrue(names.contains("wheel_" + w), s.id() + ": \u043d\u0435\u0442 \u0443\u0437\u043b\u0430 wheel_" + w);
                }
                if (!k.isFlak() && !k.isRadar() && s.yawPivot() != null) {
                    h.assertTrue(names.contains("launcher_yaw"), s.id() + ": \u043d\u0435\u0442 \u0443\u0437\u043b\u0430 launcher_yaw");
                }
                if (!k.isSam()) continue;
                for (int c = 0; c < k.magazine; ++c) {
                    h.assertTrue(names.contains("canister_" + c) && names.contains("lid_" + c), "\u043d\u0435\u0442 \u043a\u043e\u043d\u0442\u0435\u0439\u043d\u0435\u0440\u0430 " + c);
                }
                continue;
            }
            catch (IOException ex) {
                h.fail(s.id() + ": " + String.valueOf(ex));
            }
        }
        h.assertTrue(ModEntities.ROCKET.isPresent() && ModEntities.SAM_MISSILE.isPresent(), "\u0441\u0443\u0449\u043d\u043e\u0441\u0442\u0438 \u0440\u0430\u043a\u0435\u0442 \u043d\u0435 \u0437\u0430\u0440\u0435\u0433\u0438\u0441\u0442\u0440\u0438\u0440\u043e\u0432\u0430\u043d\u044b");
        h.succeed();
    }

    @GameTest(template="arena", batch="rw_veh")
    public static void ballistics_match_real_ranges(GameTestHelper h) {
        double grad = Ballistics.range(Ballistics.Profile.GRAD, 45.0, 0.0) / 1000.0;
        double smerch = Ballistics.range(Ballistics.Profile.SMERCH, 55.0, 0.0) / 1000.0;
        h.assertTrue(grad > 19.0 && grad < 21.5, "\u0434\u0430\u043b\u044c\u043d\u043e\u0441\u0442\u044c \u00ab\u0413\u0440\u0430\u0434\u0430\u00bb " + grad + " \u043a\u043c (\u0440\u0435\u0430\u043b\u044c\u043d\u043e 20,4 \u043a\u043c)");
        h.assertTrue(smerch > 66.0 && smerch < 76.0, "\u0434\u0430\u043b\u044c\u043d\u043e\u0441\u0442\u044c \u00ab\u0421\u043c\u0435\u0440\u0447\u0430\u00bb " + smerch + " \u043a\u043c (\u0440\u0435\u0430\u043b\u044c\u043d\u043e \u0434\u043e 70 \u043a\u043c)");
        double solved = Ballistics.solveElevation(Ballistics.Profile.GRAD, 12000.0, 0.0, 5.0, 55.0);
        h.assertTrue(!Double.isNaN(solved) && Math.abs(Ballistics.range(Ballistics.Profile.GRAD, solved, 0.0) - 12000.0) < 60.0, "\u0431\u0430\u043b\u043b\u0438\u0441\u0442\u0438\u0447\u0435\u0441\u043a\u0438\u0439 \u0440\u0435\u0448\u0430\u0442\u0435\u043b\u044c: " + solved);
        h.assertTrue(Double.isNaN(Ballistics.solveElevation(Ballistics.Profile.GRAD, 60000.0, 0.0, 5.0, 55.0)), "\u0446\u0435\u043b\u044c \u0437\u0430 \u043f\u0440\u0435\u0434\u0435\u043b\u043e\u043c \u0434\u0430\u043b\u044c\u043d\u043e\u0441\u0442\u0438 \u0434\u043e\u043b\u0436\u043d\u0430 \u0434\u0430\u0432\u0430\u0442\u044c NaN");
        h.succeed();
    }

    @GameTest(template="arena", batch="rw_veh")
    public static void air_target_tag(GameTestHelper h) {
        Phantom p = VehicleTests.spawnTarget(h.getLevel(), h.absoluteVec(new Vec3(5.0, 5.0, 5.0)));
        h.assertTrue(AirTargets.isAirTarget((Entity)p), "\u0444\u0430\u043d\u0442\u043e\u043c \u0434\u043e\u043b\u0436\u0435\u043d \u0441\u0447\u0438\u0442\u0430\u0442\u044c\u0441\u044f \u0432\u043e\u0437\u0434\u0443\u0448\u043d\u043e\u0439 \u0446\u0435\u043b\u044c\u044e");
        h.assertTrue(!AirTargets.isAirTarget(EntityType.PIG.create((Level)h.getLevel())), "\u0441\u0432\u0438\u043d\u044c\u044f \u043d\u0435 \u0432\u043e\u0437\u0434\u0443\u0448\u043d\u0430\u044f \u0446\u0435\u043b\u044c");
        p.discard();
        h.succeed();
    }

    @GameTest(template="arena", batch="rw_veh")
    public static void vehicle_recipes_loaded(GameTestHelper h) {
        RecipeManager rm = h.getLevel().getRecipeManager();
        for (String id : new String[]{"chassis_6x6", "chassis_8x8", "grad_vehicle", "smerch_vehicle", "s400_vehicle", "zu23_vehicle", "grad_rocket", "smerch_rocket", "sam_48n6", "flak_ammo_box", "artillery_designator"}) {
            h.assertTrue(rm.byKey(new ResourceLocation("aiarsenal", id)).isPresent(), "\u0440\u0435\u0446\u0435\u043f\u0442 \u043d\u0435 \u0437\u0430\u0433\u0440\u0443\u0436\u0435\u043d: " + id);
        }
        h.succeed();
    }

    @GameTest(template="arena", batch="rw_veh")
    public static void vehicle_items_spawn_creative(GameTestHelper h) {
        for (VehicleKind k : VehicleKind.values()) {
            h.assertTrue(VehicleItem.typeOf(k) != null, "\u043d\u0435\u0442 \u0442\u0438\u043f\u0430 \u0441\u0443\u0449\u043d\u043e\u0441\u0442\u0438 \u0434\u043b\u044f " + String.valueOf(k));
        }
        h.succeed();
    }

    @GameTest(template="arena", batch="rw_veh", timeoutTicks=60)
    public static void seat_rules_driver_vs_gunner(GameTestHelper h) {
        // r9: ONE person runs the vehicle from seat 0 (WASD + mouse aim + LMB fire); seat 1 is a passenger without controls
        ServerLevel level = h.getLevel();
        Vec3 at = h.absoluteVec(new Vec3(15.5, 2.0, 15.5));
        MlrsEntity grad = (MlrsEntity)(VehicleTests.spawn(level, (EntityType)ModEntities.GRAD.get(), at.x, at.y, at.z, 0.0f));
        ServerPlayer op = VehicleTests.mockPlayer(h);
        ServerPlayer pax = VehicleTests.mockPlayer(h);
        h.assertTrue(grad.operatorSeat() == 0, "operator seat " + grad.operatorSeat());
        h.assertTrue(grad.enterSeat(pax, 1), "passenger seat 1");
        h.assertTrue(!grad.enterSeat(op, 1), "seat 1 taken");
        grad.handleControl(pax, 1.0f, 1.0f, true, false, 1);
        h.assertTrue(grad.throttle() == 0.0f && grad.steerCmd() == 0.0f, "passenger drives: throttle=" + grad.throttle());
        h.assertTrue(!grad.engineOn(), "passenger started the engine");
        h.assertTrue(grad.enterSeat(op, 0), "operator seat 0");
        h.assertTrue(grad.seatOf((Entity)op) == 0, "operator seat = " + grad.seatOf((Entity)op));
        grad.handleControl(op, 0.7f, -0.4f, true, false, 1);
        h.assertTrue(grad.engineOn(), "operator could not start the engine");
        h.assertTrue((double)Math.abs(grad.throttle() - 0.7f) < 0.001 && (double)Math.abs(grad.steerCmd() + 0.4f) < 0.001, "operator throttle/steer not taken");
        h.assertTrue(grad.fireHeld(0), "operator fire input lost");
        Vec3 sw = grad.seatWorld(0);
        h.assertTrue(Math.abs(op.getY() - (sw.y - (double)0.85f)) < 0.6, "operator not on his seat: y=" + op.getY() + " seat=" + sw.y);
        h.succeed();
    }

    @GameTest(template="arena", batch="rw_veh", timeoutTicks=80)
    public static void server_keeps_client_driven_position(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        Vec3 at = h.absoluteVec(new Vec3(10.5, 2.0, 10.5));
        MlrsEntity grad = (MlrsEntity)(VehicleTests.spawn(level, (EntityType)ModEntities.GRAD.get(), at.x, at.y, at.z, 0.0f));
        ServerPlayer driver = VehicleTests.mockPlayer(h);
        ServerPlayer gunner = VehicleTests.mockPlayer(h);
        h.assertTrue(grad.enterSeat(driver, 0), "\u043f\u043e\u0441\u0430\u0434\u043a\u0430 \u0432\u043e\u0434\u0438\u0442\u0435\u043b\u044f");
        h.assertTrue(grad.enterSeat(gunner, 1), "\u043f\u043e\u0441\u0430\u0434\u043a\u0430 \u043f\u0430\u0441\u0441\u0430\u0436\u0438\u0440\u0430");   // passenger: its client state must be ignored
        h.assertTrue(!grad.isControlledByLocalInstance(), "\u0432\u0430\u043d\u0438\u043b\u044c\u043d\u0430\u044f \u043f\u0440\u043e\u0432\u0435\u0440\u043a\u0430 \u0434\u0432\u0438\u0436\u0435\u043d\u0438\u044f \u043c\u0430\u0448\u0438\u043d\u044b \u0434\u043e\u043b\u0436\u043d\u0430 \u0431\u044b\u0442\u044c \u043e\u0442\u043a\u043b\u044e\u0447\u0435\u043d\u0430");
        double[] x = new double[]{at.x};
        int[] n = new int[]{0};
        h.onEachTick(() -> {
            n[0] = n[0] + 1;
            if (n[0] <= 40) {
                x[0] = x[0] + 0.4;
                grad.acceptClientState(driver, x[0], grad.getY(), at.z, 0.0f, 8.0f, 0.0f);
                grad.acceptClientState(gunner, x[0] + 50.0, grad.getY(), at.z, 0.0f, 8.0f, 0.0f);
            } else {
                h.assertTrue(Math.abs(grad.getX() - x[0]) < 0.6, "\u043c\u0430\u0448\u0438\u043d\u0430 \u043e\u0442\u043a\u0430\u0442\u0438\u043b\u0430\u0441\u044c: \u0441\u0435\u0440\u0432\u0435\u0440 x=" + grad.getX() + ", \u043a\u043b\u0438\u0435\u043d\u0442 x=" + x[0]);
                h.assertTrue(grad.getX() - at.x > 12.0, "\u043c\u0430\u0448\u0438\u043d\u0430 \u043d\u0435 \u043f\u0440\u043e\u0435\u0445\u0430\u043b\u0430: " + (grad.getX() - at.x));
                double before = grad.getX();
                grad.acceptClientState(driver, before + 300.0, grad.getY(), at.z, 0.0f, 8.0f, 0.0f);
                h.assertTrue(Math.abs(grad.getX() - before) < 0.1, "\u043d\u0435\u0440\u0435\u0430\u043b\u044c\u043d\u044b\u0439 \u0441\u043a\u0430\u0447\u043e\u043a \u043f\u0440\u0438\u043d\u044f\u0442");
                grad.ejectPassengers();
                double leftX = grad.getX();
                h.runAfterDelay(5L, () -> {
                    h.assertTrue(Math.abs(grad.getX() - leftX) < 3.0, "\u043f\u043e\u0441\u043b\u0435 \u0432\u044b\u0441\u0430\u0434\u043a\u0438 \u043c\u0430\u0448\u0438\u043d\u0430 \u0443\u0435\u0445\u0430\u043b\u0430/\u043e\u0442\u043a\u0430\u0442\u0438\u043b\u0430\u0441\u044c: " + (grad.getX() - leftX));
                    h.succeed();
                });
                n[0] = -100000;
            }
        });
    }

    @GameTest(template="arena", batch="rw_veh", timeoutTicks=60)
    public static void flak_single_gunner_seat(GameTestHelper h) {
        Vec3 at = h.absoluteVec(new Vec3(15.5, 2.0, 15.5));
        FlakEntity z = (FlakEntity)(VehicleTests.spawn(h.getLevel(), (EntityType)ModEntities.ZU23.get(), at.x, at.y, at.z, 0.0f));
        ServerPlayer a = VehicleTests.mockPlayer(h);
        ServerPlayer b = VehicleTests.mockPlayer(h);
        h.assertTrue(z.enterSeat(a, 0), "\u043d\u0430\u0432\u043e\u0434\u0447\u0438\u043a \u043d\u0435 \u0441\u0435\u043b");
        h.assertTrue(!z.enterSeat(b, 0), "\u0432\u0442\u043e\u0440\u043e\u0433\u043e \u043c\u0435\u0441\u0442\u0430 \u0443 \u0417\u0423-23-2 \u043d\u0435\u0442");
        h.assertTrue(!z.engineOn(), "\u0443 \u043e\u0440\u0443\u0434\u0438\u044f \u043d\u0435\u0442 \u0434\u0432\u0438\u0433\u0430\u0442\u0435\u043b\u044f");
        z.handleControl(a, 1.0f, 0.0f, false, false, 1);
        h.assertTrue(!z.engineOn(), "\u0417\u0423-23-2 \u043d\u0435 \u0434\u043e\u043b\u0436\u043d\u0430 \u0437\u0430\u0432\u043e\u0434\u0438\u0442\u044c\u0441\u044f");
        h.succeed();
    }

    @GameTest(template="arena", batch="rw_veh", timeoutTicks=300)
    public static void grad_fires_only_from_gunner_seat(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        int top = VehicleTests.surfacePad(h, 15, 15, 6);
        BlockPos c = h.absolutePos(new BlockPos(15, 0, 15));
        MlrsEntity grad = (MlrsEntity)(VehicleTests.spawn(level, (EntityType)ModEntities.GRAD.get(), (double)c.getX() + 0.5, top, (double)c.getZ() + 0.5, 0.0f));
        grad.fillMagazine();
        ServerPlayer driver = VehicleTests.mockPlayer(h);
        ServerPlayer gunner = VehicleTests.mockPlayer(h);
        h.assertTrue(grad.enterSeat(gunner, 0) && grad.enterSeat(driver, 1), "\u043f\u043e\u0441\u0430\u0434\u043a\u0430");   // r9: seat 0 = the one operator, seat 1 = passenger (its fire key must do nothing)
        gunner.setYRot(0.0f);   // BM-21 fires forward over the cab
        gunner.setXRot(-40.0f);
        int[] phase = new int[]{0};
        h.onEachTick(() -> {
            long t = level.getGameTime();
            if (grad.tickCount < 80) {
                grad.handleControl(driver, 0.0f, 0.0f, true, false, 0);
                grad.handleControl(gunner, 0.0f, 0.0f, false, false, 0);
                gunner.setYRot(0.0f);   // BM-21 fires forward over the cab
                gunner.setXRot(-40.0f);
                List<RocketEntity> rk = level.getEntitiesOfClass(RocketEntity.class, new AABB(grad.position(), grad.position()).inflate(60.0));
                if (!rk.isEmpty() || grad.loadedCount() != 40) {
                    h.fail("\u0432\u043e\u0434\u0438\u0442\u0435\u043b\u044c \u0441\u043c\u043e\u0433 \u0432\u044b\u0441\u0442\u0440\u0435\u043b\u0438\u0442\u044c: \u0440\u0430\u043a\u0435\u0442 " + rk.size() + ", \u0431\u043e\u0435\u043a\u043e\u043c\u043f\u043b\u0435\u043a\u0442 " + grad.loadedCount());
                }
            } else {
                grad.handleControl(driver, 0.0f, 0.0f, false, false, 0);
                grad.handleControl(gunner, 0.0f, 0.0f, grad.loadedCount() == 40, false, 0);
                gunner.setYRot(0.0f);   // BM-21 fires forward over the cab
                gunner.setXRot(-40.0f);
                if (grad.loadedCount() < 40) {
                    List<RocketEntity> rk = level.getEntitiesOfClass(RocketEntity.class, new AABB(grad.position(), grad.position()).inflate(80.0));
                    h.assertTrue(!rk.isEmpty(), "\u0431\u043e\u0435\u043a\u043e\u043c\u043f\u043b\u0435\u043a\u0442 \u0443\u043c\u0435\u043d\u044c\u0448\u0438\u043b\u0441\u044f, \u043d\u043e \u0440\u0430\u043a\u0435\u0442\u044b \u043d\u0435\u0442");
                    h.assertTrue(grad.jack() > 0.95f, "\u043f\u0443\u0441\u043a \u0431\u0435\u0437 \u043e\u043f\u0443\u0449\u0435\u043d\u043d\u044b\u0445 \u043e\u043f\u043e\u0440: " + grad.jack());
                    h.assertTrue(Math.abs(grad.el() - 40.0f) < 3.0f, "\u0443\u0433\u043e\u043b \u043c\u0435\u0441\u0442\u0430 " + grad.el() + " \u0432\u043c\u0435\u0441\u0442\u043e 40\u00b0");
                    Vec3 d = ((RocketEntity)((rk.get(0)))).position().subtract(grad.muzzleWorld(grad.firedTube()));
                    h.assertTrue(d.y > -0.5, "\u0440\u0430\u043a\u0435\u0442\u0430 \u0443\u0448\u043b\u0430 \u0432\u043d\u0438\u0437: " + String.valueOf(d));
                    rk.forEach(Entity::discard);
                    h.succeed();
                }
            }
        });
    }

    @GameTest(template="arena", batch="rw_veh", timeoutTicks=400)
    public static void grad_reload_takes_time_and_item(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        Vec3 at = h.absoluteVec(new Vec3(15.5, 2.0, 15.5));
        MlrsEntity grad = (MlrsEntity)(VehicleTests.spawn(level, (EntityType)ModEntities.GRAD.get(), at.x, at.y, at.z, 0.0f));
        grad.setLoadedMask(grad.loadedMask());
        ServerPlayer gunner = VehicleTests.mockPlayer(h);
        h.assertTrue(grad.enterSeat(gunner, 0), "\u043f\u043e\u0441\u0430\u0434\u043a\u0430");
        gunner.getInventory().add(new ItemStack((ItemLike)ModItems.GRAD_ROCKET.get(), 2));
        int[] started = new int[]{-1};
        h.onEachTick(() -> {
            grad.handleControl(gunner, 0.0f, 0.0f, false, true, 0);
            if (grad.reloadTube() >= 0 && started[0] < 0) {
                started[0] = grad.tickCount;
            }
            if (grad.loadedCount() >= 2) {
                int dt = grad.tickCount - started[0] + 0;
                h.assertTrue(started[0] >= 0 && dt >= VehicleKind.GRAD.reloadTicks, "\u0437\u0430\u0440\u044f\u0436\u0430\u043d\u0438\u0435 \u0441\u043b\u0438\u0448\u043a\u043e\u043c \u0431\u044b\u0441\u0442\u0440\u043e\u0435: " + dt + " \u0442\u0438\u043a\u043e\u0432");
                int left = 0;
                for (ItemStack st : gunner.getInventory().items) {
                    if (!st.is((Item)ModItems.GRAD_ROCKET.get())) continue;
                    left += st.getCount();
                }
                h.assertTrue(left == 0, "\u0441\u043d\u0430\u0440\u044f\u0434\u044b \u043d\u0435 \u0440\u0430\u0441\u0445\u043e\u0434\u0443\u044e\u0442\u0441\u044f: \u043e\u0441\u0442\u0430\u043b\u043e\u0441\u044c " + left);
                h.succeed();
            }
        });
    }

    @GameTest(template="arena", batch="rw_veh", timeoutTicks=900)
    public static void s400_locks_and_kills_air_target(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        int top = VehicleTests.surfacePad(h, 15, 15, 6);
        BlockPos c = h.absolutePos(new BlockPos(15, 0, 15));
        SamLauncherEntity s = (SamLauncherEntity)VehicleTests.spawn(level, (EntityType)ModEntities.S400.get(), (double)c.getX() + 0.5, top, (double)c.getZ() + 0.5, 0.0f);
        s.fillMagazine();
        RadarVehicleEntity post = (RadarVehicleEntity)VehicleTests.spawn(level, (EntityType)ModEntities.RADAR_POST.get(), (double)c.getX() + 0.5 - 14.0, top, (double)c.getZ() + 0.5, 0.0f);
        ServerPlayer gunner = VehicleTests.mockPlayer(h);
        h.assertTrue(s.enterSeat(gunner, 0), "\u043f\u043e\u0441\u0430\u0434\u043a\u0430");
        Phantom target = VehicleTests.spawnTarget(level, new Vec3((double)c.getX() + 0.5 + 60.0, (double)(top + 45), (double)c.getZ() + 0.5 + 25.0));
        gunner.setPos((double)c.getX() + 0.5, (double)(top + 3), (double)c.getZ() + 0.5);
        gunner.lookAt(EntityAnchorArgument.Anchor.EYES, target.position().add(0.0, (double)target.getBbHeight() * 0.5, 0.0));
        int[] stage = new int[]{0};
        h.onEachTick(() -> {
            boolean lockAction = s.tickCount == 10;
            boolean fire = s.locked();
            s.handleControl(gunner, 0.0f, 0.0f, fire, false, lockAction ? 2 : 0);
            if (!target.isAlive() || target.isRemoved()) {
                h.assertTrue(s.loadedCount() == 3, "\u0446\u0435\u043b\u044c \u0441\u0431\u0438\u0442\u0430, \u043d\u043e \u0438\u0437\u0440\u0430\u0441\u0445\u043e\u0434\u043e\u0432\u0430\u043d\u043e \u0440\u0430\u043a\u0435\u0442: " + (4 - s.loadedCount()));
                post.discard();
                h.succeed();
                return;
            }
            if (s.tickCount == 200) {
                h.assertTrue(s.lockId() == target.getId(), "\u0446\u0435\u043b\u044c \u043d\u0435 \u0437\u0430\u0445\u0432\u0430\u0447\u0435\u043d\u0430 \u0420\u041b\u0421: lock=" + s.lockId() + ", target=" + target.getId() + ", \u0440\u0430\u0434\u0430\u0440=" + String.valueOf(s.radarTargets().stream().map(e -> e.getId() + "@" + e.blockPosition().toShortString()).toList()));
                h.assertTrue(s.el() > 80.0f, "\u043f\u0443\u0441\u043a\u043e\u0432\u0430\u044f \u043d\u0435 \u043f\u043e\u0434\u043d\u044f\u043b\u0430 \u043a\u043e\u043d\u0442\u0435\u0439\u043d\u0435\u0440\u044b: " + s.el());
            }
            if (s.loadedCount() == 4 && s.tickCount > 400) {
                h.fail("\u043f\u0443\u0441\u043a\u0430 \u043d\u0435 \u0431\u044b\u043b\u043e: \u0437\u0430\u0445\u0432\u0430\u0442 " + s.lockProgress() + ", \u0443\u0433\u043e\u043b " + s.el());
            }
            if (s.tickCount > 850) {
                List ms = level.getEntitiesOfClass(SamMissileEntity.class, new AABB(target.position(), target.position()).inflate(300.0));
                h.fail("\u0446\u0435\u043b\u044c \u043d\u0435 \u043f\u043e\u0440\u0430\u0436\u0435\u043d\u0430: \u0440\u0430\u043a\u0435\u0442 \u0432 \u0432\u043e\u0437\u0434\u0443\u0445\u0435 " + ms.size() + ", \u0434\u0438\u0441\u0442\u0430\u043d\u0446\u0438\u044f " + (ms.isEmpty() ? -1.0 : ((SamMissileEntity)((ms.get(0)))).position().distanceTo(target.position())));
            }
        });
    }

    @GameTest(template="arena", batch="rw_veh", timeoutTicks=300)
    public static void zu23_hits_air_target(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        int top = VehicleTests.surfacePad(h, 15, 15, 8);
        BlockPos c = h.absolutePos(new BlockPos(15, 0, 15));
        FlakEntity z = (FlakEntity)(VehicleTests.spawn(level, (EntityType)ModEntities.ZU23.get(), (double)c.getX() + 0.5, top, (double)c.getZ() + 0.5, 0.0f));
        z.setAmmo(100);
        ServerPlayer gunner = VehicleTests.mockPlayer(h);
        h.assertTrue(z.enterSeat(gunner, 0), "\u043f\u043e\u0441\u0430\u0434\u043a\u0430");
        double el = Math.toRadians(25.0);
        Phantom[] target = {null};
        float[] hp0 = {0};
        h.onEachTick(() -> {
            gunner.setYRot(0.0f);
            gunner.setXRot(-25.0f);
            if (target[0] == null) {
                z.handleControl(gunner, 0.0f, 0.0f, false, false, 0);
                if (z.tickCount < 10) return;
                // the gun has settled on the pad: put the target on the barrel line 40 m out
                Vec3 muz = z.barrelMuzzle(0, 0.0, 25.0);
                Vec3 tpos = muz.add(0.0, 40.0 * Math.sin(el), 40.0 * Math.cos(el));
                TestChunks.path(level, muz, tpos.add(0, 0, 8));
                target[0] = VehicleTests.spawnTarget(level, tpos);
                hp0[0] = target[0].getHealth();
                return;
            }
            z.handleControl(gunner, 0.0f, 0.0f, z.tickCount > 15, false, 0);
            if (target[0].getHealth() < hp0[0] || !target[0].isAlive()) {
                h.assertTrue(z.ammo() < 100, "\u0446\u0435\u043b\u044c \u043f\u043e\u0440\u0430\u0436\u0435\u043d\u0430 \u0431\u0435\u0437 \u0440\u0430\u0441\u0445\u043e\u0434\u0430 \u0431\u043e\u0435\u043f\u0440\u0438\u043f\u0430\u0441\u043e\u0432");
                h.succeed();
            } else if (z.tickCount > 250) {
                h.fail("no hit on the air target: ammo " + z.ammo() + ", az=" + z.az() + " el=" + z.el() + " muzzle " + z.barrelMuzzle(0, z.az(), z.el()) + " tgt " + target[0].position());
            }
        });
    }

    @GameTest(template="arena", batch="rw_veh", timeoutTicks=220)
    public static void map_target_lays_gun(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        Vec3 at = h.absoluteVec(new Vec3(10.5, 2.0, 10.5));
        MlrsEntity grad = (MlrsEntity)(VehicleTests.spawn(level, (EntityType)ModEntities.GRAD.get(), at.x, at.y, at.z, 180.0f));
        ServerPlayer gunner = VehicleTests.mockPlayer(h);
        h.assertTrue(grad.enterSeat(gunner, 0), "\u043f\u043e\u0441\u0430\u0434\u043a\u0430 \u043e\u043f\u0435\u0440\u0430\u0442\u043e\u0440\u0430");
        BlockPos tp = BlockPos.containing((double)(at.x + 120.0), (double)at.y, (double)(at.z - 300.0));
        grad.setMapTarget(gunner, tp);
        h.assertTrue(grad.autoAim(), "\u0430\u0432\u0442\u043e\u043d\u0430\u0432\u0435\u0434\u0435\u043d\u0438\u0435 \u0432\u043a\u043b\u044e\u0447\u0435\u043d\u043e \u043d\u0430\u0437\u043d\u0430\u0447\u0435\u043d\u0438\u0435\u043c \u0446\u0435\u043b\u0438");
        h.assertTrue(grad.mapTarget() != null, "\u0446\u0435\u043b\u044c \u0441\u043e\u0445\u0440\u0430\u043d\u0435\u043d\u0430");
        h.runAfterDelay(200L, () -> {
            Vec3 t = grad.mapTarget();
            Vec3 m = grad.muzzleWorld(0);
            double k = Ballistics.scaleK();
            double rng = Math.hypot(t.x - m.x, t.z - m.z) * k;
            double el = Ballistics.solveElevation(grad.profile(), rng, (t.y - m.y) * k, grad.kind.elMin, grad.kind.elMax);
            h.assertTrue(!Double.isNaN(el), "\u0440\u0435\u0448\u0435\u043d\u0438\u0435 \u0434\u043e\u043b\u0436\u043d\u043e \u0441\u0443\u0449\u0435\u0441\u0442\u0432\u043e\u0432\u0430\u0442\u044c, \u0434\u0430\u043b\u044c\u043d\u043e\u0441\u0442\u044c " + rng);
            h.assertTrue(Math.abs((double)grad.el() - el) < 1.0, "\u0443\u0433\u043e\u043b \u043c\u0435\u0441\u0442\u0430 " + grad.el() + " \u0432\u043c\u0435\u0441\u0442\u043e " + el);
            Vec3 d = grad.launchDir();
            float want = (float)Math.toDegrees(Math.atan2(-(t.x - m.x), t.z - m.z));
            h.assertTrue(Math.abs(Mth.wrapDegrees((float)(LauncherVehicle.yawOf(d) - want))) < 3.0f, "\u0430\u0437\u0438\u043c\u0443\u0442 " + LauncherVehicle.yawOf(d) + " \u0432\u043c\u0435\u0441\u0442\u043e " + want);
            grad.setMapTarget(gunner, null);
            h.assertTrue(!grad.autoAim() && grad.mapTarget() == null, "\u0446\u0435\u043b\u044c \u0441\u043d\u044f\u0442\u0430");
            h.succeed();
        });
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @GameTest(template="arena", batch="rw_veh", timeoutTicks=120)
    public static void sam_cue_by_coordinates_locks_nearest(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        Vec3 at = h.absoluteVec(new Vec3(10.5, 2.0, 10.5));
        SamLauncherEntity s = (SamLauncherEntity)VehicleTests.spawn(level, (EntityType)ModEntities.S400.get(), at.x, at.y, at.z, 0.0f);
        RadarVehicleEntity post = (RadarVehicleEntity)VehicleTests.spawn(level, (EntityType)ModEntities.RADAR_POST.get(), at.x - 16.0, at.y, at.z, 0.0f);
        ServerPlayer op = VehicleTests.mockPlayer(h);
        h.assertTrue(s.enterSeat(op, 1), "\u043f\u043e\u0441\u0430\u0434\u043a\u0430 \u043e\u043f\u0435\u0440\u0430\u0442\u043e\u0440\u0430");
        Vec3 pn = at.add(8.0, 12.0, 6.0);
        Vec3 pf = at.add(-7.0, 16.0, 8.0);
        for (Vec3 p : new Vec3[]{pn, pf}) {
            for (Phantom st : level.getEntitiesOfClass(Phantom.class, new AABB(p, p).inflate(2.5))) {
                st.discard();
            }
        }
        Phantom near = VehicleTests.spawnTarget(level, pn);
        Phantom far = VehicleTests.spawnTarget(level, pf);
        try {
            s.setMapTarget(op, BlockPos.containing((double)(at.x + 7.0), (double)(at.y + 11.0), (double)(at.z + 7.0)));
            Entity got = level.getEntity(s.lockId());
            h.assertTrue(got != null && got.position().distanceTo(pn) < 3.0, "\u0437\u0430\u0445\u0432\u0430\u0447\u0435\u043d\u0430 \u043d\u0435 \u0431\u043b\u0438\u0436\u0430\u0439\u0448\u0430\u044f \u043a \u0442\u043e\u0447\u043a\u0435 \u0446\u0435\u043b\u044c: " + s.lockId() + " (near=" + near.getId() + "@" + near.blockPosition().toShortString() + ", far=" + far.getId() + ", \u0440\u0430\u0434\u0430\u0440 \u0432\u0438\u0434\u0438\u0442 " + String.valueOf(s.radarTargets().stream().map(e -> e.getId() + "@" + e.blockPosition().toShortString()).toList()) + ")");
            s.setMapTarget(op, BlockPos.containing((double)(at.x - 6.0), (double)(at.y + 15.0), (double)(at.z + 8.0)));
            Entity got2 = level.getEntity(s.lockId());
            h.assertTrue(got2 != null && got2.position().distanceTo(pf) < 3.0, "\u0437\u0430\u0445\u0432\u0430\u0442 \u0432\u0442\u043e\u0440\u043e\u0439 \u0446\u0435\u043b\u0438 \u043f\u043e \u043a\u043e\u043e\u0440\u0434\u0438\u043d\u0430\u0442\u0430\u043c: " + s.lockId() + ", far=" + far.getId());
        }
        finally {
            near.discard();
            far.discard();
            post.discard();
        }
        h.succeed();
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @GameTest(template="arena", batch="rw_blind", timeoutTicks=100)
    public static void s400_blind_without_external_radar(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        Vec3 at = h.absoluteVec(new Vec3(10.5, 2.0, 10.5));
        SamLauncherEntity s = (SamLauncherEntity)VehicleTests.spawn(level, (EntityType)ModEntities.S400.get(), at.x, at.y, at.z, 0.0f);
        Vec3 pt = at.add(8.0, 12.0, 6.0);
        for (Phantom st : level.getEntitiesOfClass(Phantom.class, new AABB(pt, pt).inflate(2.5))) {
            st.discard();
        }
        Phantom t = VehicleTests.spawnTarget(level, pt);
        try {
            h.assertTrue(RadarNet.sources(s).isEmpty() && s.radarTargets().isEmpty(), "\u0421-400 \u0431\u0435\u0437 \u0432\u043d\u0435\u0448\u043d\u0435\u0439 \u0420\u041b\u0421 \u0432\u0438\u0434\u0438\u0442 \u0446\u0435\u043b\u0438: " + s.radarTargets().size());
        }
        finally {
            t.discard();
        }
        h.succeed();
    }

    /** Yars TEL GLB: 16 wheels, steered axles, cab steering wheel + gauge needles, TPK + missile, erect/jacks clips. */
    @GameTest(template="arena", batch="rw_yars", timeoutTicks=40)
    public static void yars_model_rig(GameTestHelper h) {
        try (InputStream in = VehicleTests.class.getResourceAsStream("/assets/aiarsenal/glb/yarstel.glb")) {
            h.assertTrue(in != null, "yarstel.glb missing");
            GlbModel m = GlbModel.read(in);
            for (int a = 0; a < 8; ++a) {
                for (String side : new String[]{"L", "R"}) {
                    h.assertTrue(m.nodeIndex("YARSTEL_W" + a + side) >= 0, "wheel YARSTEL_W" + a + side);
                }
            }
            for (int a : new int[]{0, 1, 2, 5, 6, 7}) {
                h.assertTrue(m.nodeIndex("YARSTEL_St" + a + "L") >= 0 || m.nodeIndex("YARSTEL_St" + a) >= 0, "steer node axle " + a);
            }
            for (String n : new String[]{"steering_wheel", "needle_speed", "needle_rpm", "needle_fuel", "needle_temp", "YARSTEL_Missile", "YARSTEL_Cap"}) {
                h.assertTrue(m.nodeIndex(n) >= 0, "node " + n);
            }
            GlbModel.Animation er = m.animation("yarstel_erect");
            GlbModel.Animation jk = m.animation("yarstel_jacks");
            h.assertTrue(er != null && er.duration > 3.5f, "clip yarstel_erect");
            h.assertTrue(jk != null && jk.duration > 1.0f, "clip yarstel_jacks");
            int tris = m.totalTriangles();
            h.assertTrue(tris > 40000 && tris < 250000, "triangles " + tris);
        } catch (IOException e) {
            h.fail("GLB read: " + e);
        }
        VehicleSpec s = VehicleKind.YARS.spec;
        h.assertTrue(s.seatCount() == 2 && s.tubeCount() == 1 && VehicleKind.YARS.magazine == 1, "Yars spec seats/tubes");
        h.succeed();
    }

    /** Regression: loading the Yars by right-clicking it with the missile (survival) completes the 10 s reload,
     *  consumes the item and leaves the missile in the TPK (it used to vanish after 2 s). */
    @GameTest(template="arena", batch="rw_yars", timeoutTicks=500)
    public static void yars_loads_by_right_click(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        int top = VehicleTests.surfacePad(h, 15, 15, 13);
        BlockPos c = h.absolutePos(new BlockPos(15, 0, 15));
        ru.aiforge.realwar.vehicle.YarsVehicle y = (ru.aiforge.realwar.vehicle.YarsVehicle)VehicleTests.spawn(level, (EntityType)ModEntities.YARS.get(), (double)c.getX() + 0.5, top, (double)c.getZ() + 0.5, 0.0f);
        ServerPlayer p = VehicleTests.mockPlayer(h);
        p.moveTo(c.getX() + 0.5, top, c.getZ() + 6.5);
        net.minecraft.world.item.ItemStack st = new net.minecraft.world.item.ItemStack(ru.aiforge.arsenal.registry.ArsenalRegistry.item(ru.aiforge.arsenal.core.Kind.YARS));
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, st);
        h.assertTrue(y.loadedCount() == 0, "starts empty");
        y.interact(p, net.minecraft.world.InteractionHand.MAIN_HAND);
        h.runAfterDelay(60, () -> h.assertTrue(y.reloadTube() >= 0 || y.loadedCount() == 1, "reload dropped after 3 s"));
        h.succeedWhen(() -> {
            h.assertTrue(y.loadedCount() == 1, "not loaded yet, reload tube " + y.reloadTube() + " t " + y.reloadTicks());
            h.assertTrue(p.getMainHandItem().isEmpty(), "missile item not consumed");
            y.discard();
        });
    }

    /** Radar launch: a map click only designates; ПУСК (radarLaunch) -> jacks -> TPK to 90 deg -> ICBM leaves the canister. */
    @GameTest(template="arena", batch="rw_yars", timeoutTicks=600)
    public static void yars_radar_launch_sequence(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        int top = VehicleTests.surfacePad(h, 15, 15, 13);
        BlockPos c = h.absolutePos(new BlockPos(15, 0, 15));
        ru.aiforge.realwar.vehicle.YarsVehicle y = (ru.aiforge.realwar.vehicle.YarsVehicle)VehicleTests.spawn(level, (EntityType)ModEntities.YARS.get(), (double)c.getX() + 0.5, top, (double)c.getZ() + 0.5, 0.0f);
        y.fillMagazine();
        ServerPlayer driver = VehicleTests.mockPlayer(h);
        ServerPlayer op = VehicleTests.mockPlayer(h);
        h.assertTrue(y.enterSeat(driver, 0) && y.enterSeat(op, 1), "seating");
        h.assertTrue(y.loadedCount() == 1 && y.stage() == 1, "loaded, no target: stage " + y.stage());
        // radar contact: a ground vehicle seen by a linked radar post
        RadarVehicleEntity post = (RadarVehicleEntity)VehicleTests.spawn(level, (EntityType)ModEntities.RADAR_POST.get(), (double)c.getX() + 0.5 - 10.0, top, (double)c.getZ() + 0.5 + 9.0, 0.0f);
        // r21: next to the post (same chunk as the post: a freshly placed test may straddle a chunk that is not entity-ticking yet)
        MlrsEntity enemy = (MlrsEntity)VehicleTests.spawn(level, (EntityType)ModEntities.GRAD.get(), post.getX() - 2.0, top, post.getZ(), 0.0f);
        List<Entity> pic = RadarNet.picture(y);
        h.assertTrue(pic.contains(enemy), "ground contact not on the Yars picture: " + pic.size());
        h.assertTrue(RadarNet.typeOf(enemy) == RadarNet.T_GROUND, "contact class " + RadarNet.typeOf(enemy));
        // designate a map point far away: no erection yet
        BlockPos far = new BlockPos(c.getX() + 3000, 70, c.getZ() + 3000);
        y.setMapTarget(op, far);
        h.assertTrue(y.mapTarget() != null && !y.autoAim() && y.stage() == 2, "map pick must not start the launch: stage " + y.stage());
        // ПУСК on the radar contact: target follows the contact
        y.radarLaunch(op, enemy.getId());
        h.assertTrue(y.armed() && y.autoAim(), "not armed");
        h.assertTrue(y.mapTarget().distanceTo(enemy.position()) < 3.0, "target is not the contact");
        // then back to the far map point so the test ICBM never comes down near the arena
        y.setMapTarget(op, far);
        y.radarLaunch(op, -1);
        h.assertTrue(y.armed(), "re-arm on map point");
        enemy.discard();
        post.discard();
        boolean[] sawJacks = new boolean[1];
        boolean[] sawErect = new boolean[1];
        h.onEachTick(() -> {
            if (y.isRemoved()) h.fail("TEL removed");
            int st = y.stage();
            if (st == 3) sawJacks[0] = true;
            if (st == 4) {
                sawErect[0] = true;
                h.assertTrue(y.jack() >= 0.97f, "TPK rising before the jacks are down");
            }
            if (y.loadedCount() == 0) {
                List<ru.aiforge.arsenal.entity.IcbmEntity> ms = level.getEntitiesOfClass(ru.aiforge.arsenal.entity.IcbmEntity.class, y.getBoundingBox().inflate(60.0));
                h.assertTrue(!ms.isEmpty(), "magazine empty but no ICBM");
                h.assertTrue(sawErect[0] && y.el() >= 89.0f, "launched without full erection: el " + y.el());
                ru.aiforge.arsenal.nuke.ChargeRegistry reg = ru.aiforge.arsenal.nuke.ChargeRegistry.get(level.getServer());
                for (ru.aiforge.arsenal.entity.IcbmEntity m : ms) {
                    ru.aiforge.arsenal.nuke.ChargeRegistry.Charge ch = reg.byEntity(m.getUUID());
                    if (ch != null) reg.charges.remove(ch);
                    m.discard();
                }
                reg.setDirty();
                h.assertTrue(!y.armed(), "still armed after launch");
                h.succeed();
            }
        });
    }

    /** 5x5 km radar map backend: one 512-block tile is computed fast, survives the packet round trip, villages scan is safe. */
    @GameTest(template="arena", batch="rw_blind", timeoutTicks=200)
    public static void terrain_overview_tile(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos c = h.absolutePos(new BlockPos(15, 0, 15));
        int tx = Math.floorDiv(c.getX(), 512), tz = Math.floorDiv(c.getZ(), 512);
        ru.aiforge.realwar.radar.TerrainOverview.compute(level, tx + 1, tz);          // warm-up (class init / JIT)
        long t0 = System.nanoTime();
        ru.aiforge.realwar.radar.TerrainOverview.Tile t = ru.aiforge.realwar.radar.TerrainOverview.compute(level, tx, tz);
        long ms = (System.nanoTime() - t0) / 1000000L;
        h.assertTrue(ms < 1500L, "tile took " + ms + " ms");
        int lo = level.getMinBuildHeight(), hi = level.getMaxBuildHeight();
        for (short v : t.h) h.assertTrue(v >= lo - 1 && v <= hi, "height out of range " + v);
        io.netty.buffer.ByteBuf raw = io.netty.buffer.Unpooled.buffer();
        net.minecraft.network.FriendlyByteBuf b = new net.minecraft.network.FriendlyByteBuf(raw);
        ru.aiforge.realwar.radar.OverviewTilePacket.encode(new ru.aiforge.realwar.radar.OverviewTilePacket(t), b);
        int bytes = b.readableBytes();
        ru.aiforge.realwar.radar.TerrainOverview.Tile r = ru.aiforge.realwar.radar.OverviewTilePacket.decode(b).tile();
        raw.release();
        h.assertTrue(r.tx == t.tx && r.tz == t.tz && java.util.Arrays.equals(r.h, t.h) && java.util.Arrays.equals(r.water, t.water) && java.util.Arrays.equals(r.biome, t.biome), "packet round trip");
        h.assertTrue(bytes < 64 * 1024, "tile packet " + bytes + " bytes");
        // the real overworld path: preliminary-surface density of a seeded overworld noise router, 33x33 columns = one tile
        net.minecraft.world.level.levelgen.RandomState ors = net.minecraft.world.level.levelgen.RandomState.create(level.registryAccess().asGetterLookup(),
                net.minecraft.world.level.levelgen.NoiseGeneratorSettings.OVERWORLD, 12345L);
        net.minecraft.world.level.levelgen.DensityFunction dens = ors.router().initialDensityWithoutJaggedness();
        ru.aiforge.realwar.radar.TerrainOverview.fastColumn(dens, 0, 0, -64, 320, 63);
        long n0 = System.nanoTime();
        int mn = 9999, mx = -9999, wet = 0;
        for (int j = 0; j < 33; ++j) {
            for (int i = 0; i < 33; ++i) {
                int[] sw = ru.aiforge.realwar.radar.TerrainOverview.fastColumn(dens, 4096 + i * 16, -2048 + j * 16, -64, 320, 63);
                mn = Math.min(mn, sw[1]);
                mx = Math.max(mx, sw[1]);
                if (sw[0] > sw[1]) ++wet;
            }
        }
        long nms = (System.nanoTime() - n0) / 1000000L;
        h.assertTrue(nms < 1500L, "noise tile took " + nms + " ms");
        h.assertTrue(mn >= -64 && mx <= 320 && mx > mn, "noise heights " + mn + ".." + mx);
        ru.aiforge.realwar.RealWar.LOGGER.info("TerrainOverview noise tile {} ms, floor {}..{}, wet {}/1089", nms, mn, mx, wet);
        List<int[]> vil = ru.aiforge.realwar.radar.TerrainOverview.villages(level, c.getX(), c.getZ(), 1500);
        h.assertTrue(vil != null, "villages null");
        ru.aiforge.realwar.RealWar.LOGGER.info("TerrainOverview tile {} ms, {} bytes, {} villages", ms, bytes, vil.size());
        h.succeed();
    }

    /** r7: walkable decks - a mob standing on a moving, turning T-90M stays on the same spot of the hull. */
    @GameTest(template="arena", batch="rw_deck", timeoutTicks=200)
    public static void deck_carries_mob(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        Vec3 o = h.absoluteVec(new Vec3(10.5, 2.0, 10.5));
        VehicleEntity tank = spawn(level, ru.aiforge.realwar.registry.ModEntities.T90M.get(), o.x, o.y, o.z, 0f);
        tank.setNoGravity(true);
        ru.aiforge.realwar.deck.DeckShapes.Deck dk = ru.aiforge.realwar.deck.Decks.shape(tank);
        h.assertTrue(dk != null && dk.count() > 20, "no deck shape for t90m");
        // highest hull box over the engine deck (~2.5 m behind the centre)
        float[] d = dk.b;
        float tp = -1;
        for (int i = 0; i < d.length; i += 7) if (d[i] == 0 && d[i + 1] <= 0 && d[i + 4] >= 0 && d[i + 3] <= -2.5f && d[i + 6] >= -2.5f) tp = Math.max(tp, d[i + 5]);
        h.assertTrue(tp > 0.5f, "no hull box at the engine deck");
        final float lx = 0, lz = -2.5f, top = tp;
        net.minecraft.world.entity.animal.Pig pig = EntityType.PIG.create(level);
        pig.moveTo(o.x + lx, o.y + top, o.z + lz, 0f, 0f);   // yaw 0: local == world offsets
        pig.setNoAi(true);
        level.addFreshEntity(pig);
        final long t0 = level.getGameTime();
        h.onEachTick(() -> {
            long k = level.getGameTime() - t0;
            if (k > 0 && k <= 80) {
                float yaw = k * 1.2f;
                tank.moveTo(o.x + k * 0.12, o.y, o.z + k * 0.05, yaw, 0f);
                tank.setYRot(yaw);
            }
        });
        h.runAfterDelay(100, () -> {
            double a = Math.toRadians(tank.getYRot());
            double wx = pig.getX() - tank.getX(), wz = pig.getZ() - tank.getZ();
            double plx = wx * Math.cos(a) + wz * Math.sin(a), plz = -wx * Math.sin(a) + wz * Math.cos(a);
            double err = Math.hypot(plx - lx, plz - lz), dy = pig.getY() - (tank.getY() + top);
            h.assertTrue(err < 1.0 && Math.abs(dy) < 0.4, String.format("pig slid off the deck: local err %.2f m, dy %.2f, tank moved to %.1f,%.1f yaw %.0f", err, dy, tank.getX() - o.x, tank.getZ() - o.z, tank.getYRot()));
            h.succeed();
        });
    }
}
