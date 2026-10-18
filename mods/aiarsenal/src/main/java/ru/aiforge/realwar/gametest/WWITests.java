package ru.aiforge.realwar.gametest;

import com.mojang.authlib.GameProfile;
import java.io.InputStream;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3f;
import ru.aiforge.realwar.damage.Armour;
import ru.aiforge.realwar.damage.Crew;
import ru.aiforge.realwar.damage.VehicleDamage;
import ru.aiforge.realwar.glb.GlbModel;
import ru.aiforge.realwar.glb.Pose;
import ru.aiforge.realwar.ordnance.Ballistics;
import ru.aiforge.realwar.ordnance.ShellEntity;
import ru.aiforge.realwar.registry.ModEntities;
import ru.aiforge.realwar.registry.ModItems;
import ru.aiforge.realwar.registry.ModTabs;
import ru.aiforge.realwar.vehicle.GunVehicle;
import ru.aiforge.realwar.vehicle.Specs;
import ru.aiforge.realwar.vehicle.VehicleEntity;
import ru.aiforge.realwar.vehicle.VehicleKind;

/** WWI vertical slice: two working Mark I sponsons, 6-pdr ammunition, and the Gewehr 98. */
@GameTestHolder("aiarsenal")
@PrefixGameTestTemplate(false)
public final class WWITests {
    private WWITests() {}

    private static ServerPlayer mock(GameTestHelper h) {
        return new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "rw-wwi"));
    }

    @SuppressWarnings("unchecked")
    private static <T extends VehicleEntity> T spawn(ServerLevel level, EntityType<?> type,
                                                      double x, double y, double z, float yaw) {
        VehicleEntity e = (VehicleEntity) type.create((Level) level);
        e.moveTo(x, y, z, yaw, 0.0f);
        level.addFreshEntity(e);
        return (T) e;
    }

    private static int clearRunway(ServerLevel level, BlockPos c, int distance) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.getX(), c.getZ());
        for (int x = c.getX() - 7; x <= c.getX() + 7; ++x) {
            for (int z = c.getZ() - 8; z <= c.getZ() + distance; ++z) {
                for (int yy = y - 1; yy <= y + 12; ++yy) {
                    level.setBlock(new BlockPos(x, yy, z), yy == y - 1 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
        return y;
    }

    private static void look(ServerPlayer p, Vec3 at) {
        Vec3 d = at.subtract(p.getEyePosition());
        p.setYRot((float) Math.toDegrees(Math.atan2(-d.x, d.z)));
        p.setXRot((float) -Math.toDegrees(Math.atan2(d.y, d.horizontalDistance())));
    }

    @GameTest(template = "arena", batch = "rw_wwi", timeoutTicks = 60)
    public static void wwi_models_profiles_tabs_and_recipes(GameTestHelper h) {
        h.assertTrue(!VehicleKind.MARK_I.ww2() && VehicleKind.MARK_I.isGun() && VehicleKind.MARK_I.tracked(), "Mark I era / tank flags");
        h.assertTrue(GunVehicle.cfg(VehicleKind.MARK_I).primary() == Ballistics.Profile.QF6_HE
                && GunVehicle.cfg(VehicleKind.MARK_I).secondary() == Ballistics.Profile.QF6_AP, "HE / AP selector");
        h.assertTrue(GunVehicle.cfg(VehicleKind.MARK_I).ammo().get() == ModItems.SHELLS_57.get(), "57 mm ammo item");
        h.assertTrue(VehicleKind.MARK_I.magazine == 2 && Specs.MARK_I.seats().length == 2, "two reloadable barrels / two player seats");
        GunVehicle.Cfg runningGear = GunVehicle.cfg(VehicleKind.MARK_I);
        h.assertTrue(Math.abs(runningGear.rRoad() - 0.305f) < 0.001f && Math.abs(runningGear.rSpk() - 0.385f) < 0.001f
                && Math.abs(runningGear.rIdl() - 0.385f) < 0.001f && Math.abs(runningGear.rRr() - 0.115f) < 0.001f,
                "road wheels, sprocket, idler and return rollers use the generated GLB radii");
        h.assertTrue(Math.abs(runningGear.pitch() - 0.29533399f) < 0.00001f && Math.abs(Specs.MARK_I.length() - 7.20f) < 0.01f,
                "the 56-link pitch and vehicle length match the rebuilt model/deck");
        h.assertTrue(Specs.MARK_I.seatRoles()[0].equals("driver") && Crew.roles(VehicleKind.MARK_I)[0] == Crew.DRIVER,
                "seat 0 remains the driver/operator");
        h.assertTrue(VehicleDamage.geo(VehicleKind.MARK_I, true, true).tracked()
                && !VehicleDamage.geo(VehicleKind.MARK_I, true, true).turret(), "sponsons are not a fictitious turret");
        h.assertTrue(ModTabs.content("wwi_vehicles").contains(ModItems.MARK_I_VEHICLE.get()), "Mark I is reachable in WWI vehicles");
        h.assertTrue(ModTabs.content("wwi_weapons").contains(ModItems.GEWEHR98.get()), "Gewehr 98 is reachable in WWI weapons");
        h.assertTrue(ModTabs.content("wwi_ammo").contains(ModItems.SHELLS_57.get()), "6-pdr ammunition is reachable");
        h.assertTrue(ModTabs.content("wwi_ammo").contains(ModItems.CLIP_K98.get()), "shared Mauser clip is reachable in WWI ammo");
        h.assertTrue(ModTabs.sectionOf("gewehr98").equals("wwi_weapons")
                && ModTabs.sectionOf("mark_i_vehicle").equals("wwi_vehicles"), "era sorting");
        h.assertTrue(GunVehicle.cfg(VehicleKind.MARK_I).modes().length == 2, "weapon selector exposes HE and AP");
        h.assertTrue(Armour.of(VehicleKind.MARK_I).mm(Armour.HULL_UF) == 12
                && Armour.of(VehicleKind.MARK_I).mm(Armour.HULL_S) == 8, "Mark I armour table");
        h.assertTrue(Armour.Ammo.of(Ballistics.Profile.QF6_AP).pen0() == 55
                && Armour.Ammo.of(Ballistics.Profile.QF6_HE).type() == Armour.Type.HE, "QF 6-pdr penetration profiles");
        h.assertTrue(ru.aiforge.realwar.handheld.GunKind.GEWEHR98.boltAction()
                && ru.aiforge.realwar.handheld.GunKind.GEWEHR98.clipFed()
                && ru.aiforge.realwar.handheld.GunKind.GEWEHR98.ammo.equals("clip_k98"), "Gewehr 98 Mauser clip-fed action");
        for (String name : new String[]{"gewehr98", "mark_i_vehicle", "shells_57"}) {
            var id = new net.minecraft.resources.ResourceLocation("aiarsenal", name);
            h.assertTrue(h.getLevel().getRecipeManager().byKey(id).isPresent(), "missing WWI recipe " + id);
        }
        try (InputStream in = WWITests.class.getResourceAsStream("/assets/aiarsenal/models/glb/mark_i.glb")) {
            GlbModel m = GlbModel.read(in);
            for (String n : new String[]{"launcher_yaw", "launcher_yaw_left", "launcher_pitch", "launcher_pitch_left",
                    "barrel", "barrel_left", "MarkI_link_L_00", "MarkI_link_R_00"})
                h.assertTrue(m.nodeIndex(n) >= 0, "Mark I GLB node " + n);
            h.assertTrue(Math.abs(m.nodes.get(m.nodeIndex("rw_L_0")).t.y - 0.483f) < 0.0001f
                            && Math.abs(m.nodes.get(m.nodeIndex("rr_L_0")).t.y - 2.117f) < 0.0001f,
                    "road wheels and return rollers touch the inside of their belt runs");
            Pose bind = new Pose(m);
            bind.compute();
            Vector3f bindRight = bind.world[m.nodeIndex("launcher_yaw")].transformDirection(new Vector3f(0, 0, 1)).normalize();
            Vector3f bindLeft = bind.world[m.nodeIndex("launcher_yaw_left")].transformDirection(new Vector3f(0, 0, 1)).normalize();
            h.assertTrue(bindRight.x < -0.99f && Math.abs(bindRight.z) < 0.01f
                            && bindLeft.x > 0.99f && Math.abs(bindLeft.z) < 0.01f,
                    "generated Mark I mounts start pointing outward from their sponsons");
            for (float az : new float[]{-90.0f, 0.0f, 45.0f, 135.0f}) {
                Pose aimed = new Pose(m);
                aimed.rotate("launcher_yaw", (float) Math.toRadians(az + 90.0f), 0, 1, 0);
                aimed.rotate("launcher_yaw_left", (float) Math.toRadians(az - 90.0f), 0, 1, 0);
                aimed.compute();
                float radians = (float) Math.toRadians(az);
                Vector3f expected = new Vector3f((float) Math.sin(radians), 0, (float) Math.cos(radians));
                Vector3f right = aimed.world[m.nodeIndex("launcher_yaw")].transformDirection(new Vector3f(0, 0, 1)).normalize();
                Vector3f left = aimed.world[m.nodeIndex("launcher_yaw_left")].transformDirection(new Vector3f(0, 0, 1)).normalize();
                h.assertTrue(right.distance(expected) < 0.001f && left.distance(expected) < 0.001f,
                        "left/right Mark I guns match projectile azimuth " + az + " degrees");
            }
            h.assertTrue(m.animation("track_L") != null && m.animation("track_R") != null, "both animated tracks");
        } catch (Exception e) {
            h.fail("Mark I model: " + e);
        }
        try (InputStream in = WWITests.class.getResourceAsStream("/assets/aiarsenal/models/glb/gewehr98.glb")) {
            GlbModel m = GlbModel.read(in);
            for (String n : new String[]{"gewehr98.body", "gewehr98.bolt", "gewehr98.clip", "gewehr98.sight"})
                h.assertTrue(m.nodeIndex(n) >= 0, "Gewehr 98 GLB node " + n);
            h.assertTrue(m.animation("gewehr98_reload") != null, "five-round stripper-clip reload animation");
        } catch (Exception e) {
            h.fail("Gewehr 98 model: " + e);
        }
        h.succeed();
    }

    @GameTest(template = "arena", batch = "rw_wwi", timeoutTicks = 600)
    public static void mark_i_sponsons_fire_as_one_salvo_in_both_modes(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos c = h.absolutePos(new BlockPos(8, 0, 8));
        int distance = 70;
        int y = clearRunway(level, c, distance + 8);
        TestChunks.path(level, Vec3.atCenterOf(c), Vec3.atCenterOf(c).add(0, 0, distance + 8));
        GunVehicle tank = spawn(level, ModEntities.MARK_I.get(), c.getX() + 0.5, y, c.getZ() + 0.5, 0.0f);
        VehicleEntity target = spawn(level, ModEntities.GRAD.get(), c.getX() + 0.5, y, c.getZ() + distance + 0.5, 90.0f);
        TestChunks.follow(tank, target);
        tank.fillMagazine();
        ServerPlayer operator = mock(h);
        h.assertTrue(tank.operatorSeat() == 0 && tank.enterSeat(operator, 0), "seat 0 is the Mark I driver/operator");
        h.assertTrue(tank.profile() == Ballistics.Profile.QF6_HE, "HE is the initial selected round");
        int[] seq = {tank.firedSeq()};
        int[] ticks = {0};
        boolean[] firstSalvo = {false};
        boolean[] secondSalvo = {false};
        boolean[] reloading = {false};
        h.onEachTick(() -> {
            ticks[0]++;
            look(operator, target.position().add(0, 1.4, 0));
            tank.handleControl(operator, 0, 0, !reloading[0] && ticks[0] > 30 && ticks[0] < 250, reloading[0], 0);
            if (tank.firedSeq() > seq[0]) {
                h.assertTrue(tank.firedSeq() == seq[0] + 1, "one paired salvo should produce one synchronized shot event");
                h.assertTrue(tank.firedTube() == -2, "paired-muzzle event marker");
                AABB near = new AABB(tank.getX() - 8, tank.getY() - 5, tank.getZ() - 8,
                        tank.getX() + 8, tank.getY() + 8, tank.getZ() + distance + 12);
                var shells = level.getEntitiesOfClass(ShellEntity.class, near);
                if (!firstSalvo[0]) {
                    h.assertTrue(tank.loadedCount() == 0, "the HE salvo consumed both independently loaded barrels");
                    h.assertTrue(shells.stream().filter(s -> s.profile() == Ballistics.Profile.QF6_HE).count() >= 2,
                            "both barrels must launch the selected QF 6-pdr HE profile");
                    firstSalvo[0] = true;
                    seq[0] = tank.firedSeq();
                    // r28: the ready rack now remembers the round type of every slot, so the AP salvo needs AP rounds
                    tank.fillMagazine(1);
                    h.assertTrue(tank.loadedOfType(1) == 2 && tank.loadedOfType(0) == 0, "the rack holds two AP rounds");
                    tank.selectSlot(operator, 1);
                    h.assertTrue(tank.profile() == Ballistics.Profile.QF6_AP, "weapon selector changes the live profile to AP");
                } else {
                    h.assertTrue(tank.loadedCount() == 0, "the AP salvo consumed both independently loaded barrels");
                    h.assertTrue(shells.stream().filter(s -> s.profile() == Ballistics.Profile.QF6_AP).count() >= 2,
                            "both barrels must use the selected QF 6-pdr AP profile");
                    secondSalvo[0] = true;
                    seq[0] = tank.firedSeq();
                    operator.getInventory().add(new net.minecraft.world.item.ItemStack(ModItems.SHELLS_57.get(), 2));
                    reloading[0] = true;
                }
            }
            if (secondSalvo[0] && tank.loadedCount() == 2) {
                int leftover = 0;
                for (net.minecraft.world.item.ItemStack stack : operator.getInventory().items)
                    if (stack.is(ModItems.SHELLS_57.get())) leftover += stack.getCount();
                h.assertTrue(leftover == 0, "two-round 6-pdr reload consumes exactly the rounds for both barrels");
                operator.stopRiding();
                tank.discard();
                target.discard();
                h.succeed();
            } else if (ticks[0] > 550) {
                h.fail("Mark I firing/reload failed: loaded=" + tank.loadedCount() + " reloadTube=" + tank.reloadTube()
                        + " az=" + tank.az() + " el=" + tank.el());
            }
        });
    }

    /** r28 (player report "one shot eats both round types"): every ready slot keeps the type the loader seated, so a
     *  selected AP round cannot be fired from an HE rack, the HE counter does not drop with it, and holding [R]
     *  exchanges the rounds in the rack without spending ammunition. */
    @GameTest(template = "arena", batch = "rw_wwi", timeoutTicks = 480)
    public static void ready_rack_keeps_the_round_type_of_every_slot(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos c = h.absolutePos(new BlockPos(2, 0, 2));
        int y = clearRunway(level, c, 8);
        GunVehicle tank = spawn(level, ModEntities.MARK_I.get(), c.getX() + 0.5, y, c.getZ() + 0.5, 0.0f);
        ServerPlayer operator = mock(h);
        h.assertTrue(tank.enterSeat(operator, 0), "seat 0 is the Mark I driver/operator");
        tank.fillMagazine(0);
        h.assertTrue(tank.loadedOfType(0) == 2 && tank.loadedOfType(1) == 0, "two HE rounds seated: HE="
                + tank.loadedOfType(0) + " AP=" + tank.loadedOfType(1));
        h.assertTrue(tank.profile() == Ballistics.Profile.QF6_HE, "HE is the initial selected round");
        tank.selectSlot(operator, 1);
        h.assertTrue(tank.profile() == Ballistics.Profile.QF6_AP, "the selector changes the live profile to AP");
        h.assertTrue(tank.firstLoadedOfType(1) < 0, "the HE rack must not offer an AP round");
        h.assertTrue(tank.loadedOfType(0) == 2, "selecting AP must not consume the HE rounds");
        int seq = tank.firedSeq();
        int[] tick = {0};
        int[] phase = {0};
        h.onEachTick(() -> {
            tick[0]++;
            if (phase[0] == 0) {
                // the rack holds HE only while AP is selected: 20 ticks of a held trigger must produce no shot
                tank.handleControl(operator, 0, 0, true, false, 0);
                h.assertTrue(tank.firedSeq() == seq, "the gun fired from a rack that holds the wrong round");
                h.assertTrue(tank.loadedOfType(0) == 2, "the HE rounds were touched");
                if (tick[0] >= 20) {
                    phase[0] = 1;
                    tick[0] = 0;
                }
                return;
            }
            // now hold [R]: every loading cycle exchanges one round of the rack for the selected AP round
            tank.handleControl(operator, 0, 0, false, true, 0);
            h.assertTrue(tank.firedSeq() == seq, "the gun fired while only the loader was working");
            h.assertTrue(tank.loadedCount() == 2, "exchanging rounds lost a slot: " + tank.loadedCount());
            int ap = tank.loadedOfType(1);
            if (ap == 2) {
                int items = 0;
                for (net.minecraft.world.item.ItemStack st : operator.getInventory().items)
                    if (st.is(ModItems.SHELLS_57.get())) items += st.getCount();
                h.assertTrue(items == 0, "exchanging rounds inside the rack must not spend ammunition: " + items);
                h.assertTrue(tank.firstLoadedOfType(1) >= 0, "both AP rounds are ready to fire");
                CompoundTag saved = tank.saveWithoutId(new CompoundTag());
                GunVehicle restored = ModEntities.MARK_I.get().create(level);
                restored.load(saved);
                h.assertTrue(restored.loadedOfType(1) == 2 && restored.loadedOfType(0) == 0,
                        "the round type of every slot survives save/load");
                operator.stopRiding();
                tank.discard();
                restored.discard();
                h.succeed();
            } else if (tick[0] > 320) {
                h.fail("round exchange stalled: HE=" + tank.loadedOfType(0) + " AP=" + ap
                        + " tube=" + tank.reloadTube() + " t=" + tank.reloadTicks());
            }
        });
    }

    @GameTest(template = "arena", batch = "rw_wwi", timeoutTicks = 60)
    public static void mark_i_mode_and_loaded_rounds_survive_save_load(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos c = h.absolutePos(new BlockPos(2, 0, 2));
        int y = clearRunway(level, c, 8);
        GunVehicle tank = spawn(level, ModEntities.MARK_I.get(), c.getX() + 0.5, y, c.getZ() + 0.5, 0.0f);
        tank.fillMagazine();
        ServerPlayer operator = mock(h);
        h.assertTrue(tank.enterSeat(operator, 0), "operator enters the specified seat 0");
        tank.selectSlot(operator, 1);
        h.assertTrue(tank.profile() == Ballistics.Profile.QF6_AP && tank.loadedCount() == 2,
                "AP selection and both loaded rounds before save");
        CompoundTag saved = tank.saveWithoutId(new CompoundTag());
        GunVehicle restored = ModEntities.MARK_I.get().create(level);
        h.assertTrue(restored != null, "registered Mark I entity type can be recreated");
        restored.load(saved);
        h.assertTrue(restored.loadedCount() == 2 && restored.profile() == Ballistics.Profile.QF6_AP,
                "world NBT preserves both ready rounds and the chosen AP mode");
        h.assertTrue(restored.operatorSeat() == 0 && restored.kind == VehicleKind.MARK_I,
                "restored operator seat still maps to the Mark I driver");
        operator.stopRiding();
        tank.discard();
        restored.discard();
        h.succeed();
    }

    @GameTest(template = "arena", batch = "rw_wwi", timeoutTicks = 40)
    public static void mark_i_tracks_sponsons_and_breeches_are_real_hit_zones(GameTestHelper h) {
        VehicleDamage.Geo g = VehicleDamage.geo(VehicleKind.MARK_I, true, true);
        float[] aim = {0.0f, 0.0f};
        h.assertTrue(VehicleDamage.zone(g, VehicleKind.MARK_I, false, 1.50, 0.13, -2.45, aim)
                == ru.aiforge.realwar.damage.DamageState.TRACK_L, "+X is the left external track");
        h.assertTrue(VehicleDamage.zone(g, VehicleKind.MARK_I, false, -1.50, 0.13, -2.45, aim)
                == ru.aiforge.realwar.damage.DamageState.TRACK_R, "-X is the right external track");
        // at aim 0 both guns rest at the forward end of their sector (35 deg off broadside), so the barrel
        // probes sit on the clamped axes: sin/cos 55 deg = 0.81915 / 0.57358
        h.assertTrue(VehicleDamage.zone(g, VehicleKind.MARK_I, false, -2.22915, 1.22, 0.57358, aim)
                == ru.aiforge.realwar.damage.DamageState.BARREL, "right QF 6-pdr breech/barrel zone");
        h.assertTrue(VehicleDamage.zone(g, VehicleKind.MARK_I, false, 2.22915, 1.22, 0.57358, aim)
                == ru.aiforge.realwar.damage.DamageState.BARREL, "left QF 6-pdr breech/barrel zone");
        h.assertTrue(VehicleDamage.zone(g, VehicleKind.MARK_I, false, -1.41, 1.22, 0.95, aim)
                != ru.aiforge.realwar.damage.DamageState.BARREL, "no barrel along the hull side at aim 0");
        h.assertTrue(VehicleDamage.zone(g, VehicleKind.MARK_I, false, 1.41, 1.5, -0.35, aim)
                == ru.aiforge.realwar.damage.DamageState.AMMO, "sponson ammunition rack zone");
        h.succeed();
    }

    /** r30: each QF 6-pdr traverses only its historical sector and fires along its own clamped line. */
    @GameTest(template = "arena", batch = "rw_wwi", timeoutTicks = 400)
    public static void mark_i_sponsons_traverse_only_their_historical_sector(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos c = h.absolutePos(new BlockPos(2, 0, 2));
        int y = clearRunway(level, c, 8);
        GunVehicle tank = spawn(level, ModEntities.MARK_I.get(), c.getX() + 0.5, y, c.getZ() + 0.5, 0.0f);
        ServerPlayer operator = mock(h);
        h.assertTrue(tank.enterSeat(operator, 0), "seat 0 is the Mark I driver/operator");
        tank.fillMagazine(1);
        tank.selectSlot(operator, 1);   // the selector must match the rack, else wrong_round blocks the shot
        // aim straight ahead: neither gun may lie along the hull side, both stop 35 deg off broadside
        h.assertTrue(Math.abs(tank.sponsonAz(0) + 55.0f) < 0.01f && Math.abs(tank.sponsonAz(1) - 55.0f) < 0.01f,
                "aim az 0 must clamp to the sector ends: " + tank.sponsonAz(0) + "/" + tank.sponsonAz(1));
        org.joml.Vector3d tr = tank.bodyFrame().transformPosition(new org.joml.Vector3d(-1.41, 1.22, 0.0));
        Vec3 tr0 = new Vec3(tr.x, tr.y, tr.z);
        int[] tick = {0};
        int[] phase = {0};
        int[] seq = {tank.firedSeq()};
        h.onEachTick(() -> {
            tick[0]++;
            if (phase[0] == 0) {
                // the spawn seed holds el at elMin until the first aim tick; wait for the laid aim first
                if (Math.abs(tank.az()) < 0.5f && Math.abs(tank.el()) < 0.5f) {
                    Vec3 d0 = tank.tubeWorld(0).subtract(tr0).normalize();
                    h.assertTrue(d0.z < 0.75, "the right gun still points along the hull: " + d0);
                    h.assertTrue(d0.dot(new Vec3(-0.81915, 0.0, 0.57358)) > 0.999,
                            "right gun off its clamped line: " + d0);
                    operator.setYRot(90.0f);   // hull az = -worldYaw: +90 looks down the right (-x) broadside
                    operator.setXRot(0.0f);
                    tick[0] = 0;
                    phase[0] = 1;
                } else if (tick[0] > 60) {   // elevation is hand-cranked at ~0.35 deg/tick from the elMin seed
                    h.fail("the laid aim never settled at az/el 0: " + tank.az() + "/" + tank.el());
                }
                return;
            }
            if (phase[0] == 1) {
                if (Math.abs(tank.az() + 90.0f) < 0.5f) {
                    // right broadside: the right gun is centred, the left one stays at its forward stop
                    h.assertTrue(Math.abs(tank.sponsonAz(0) + 90.0f) < 0.5f, "right gun off broadside: " + tank.sponsonAz(0));
                    h.assertTrue(Math.abs(tank.sponsonAz(1) - 55.0f) < 0.5f, "left gun left its sector: " + tank.sponsonAz(1));
                    Vec3 dr = tank.tubeWorld(0).subtract(tr0).normalize();
                    h.assertTrue(dr.dot(new Vec3(-1.0, 0.0, 0.0)) > 0.999, "right gun not on broadside: " + dr);
                    phase[0] = 2;
                    tick[0] = 0;
                } else if (tick[0] > 280) {   // the sponson yaw drive is rate limited too
                    h.fail("the aim never reached right broadside: az " + tank.az());
                }
                return;
            }
            tank.handleControl(operator, 0, 0, true, false, 0);
            if (tank.firedSeq() != seq[0]) {
                java.util.List<ShellEntity> shells = level.getEntitiesOfClass(ShellEntity.class,
                        tank.getBoundingBox().inflate(24.0));
                h.assertTrue(shells.size() == 2, "both sponsons fire one round each, got " + shells.size());
                StringBuilder dmlog = new StringBuilder();
                boolean broadside = false, clamped = false;
                for (ShellEntity sh : shells) {
                    ru.aiforge.realwar.ordnance.Ballistics.Flight fl = sh.flight();
                    Vec3 dm = new Vec3(fl.vx, fl.vy, fl.vz).normalize();
                    dmlog.append(String.format(" [%.3f %.3f %.3f]", dm.x, dm.y, dm.z));
                    if (dm.x < -0.9) broadside = true;
                    if (dm.x > 0.7 && dm.z > 0.4) clamped = true;
                }
                h.assertTrue(broadside, "the right sponson did not fire along its broadside line:" + dmlog);
                h.assertTrue(clamped, "the left sponson did not fire along its clamped 55 deg line:" + dmlog);
                h.succeed();
                return;
            }
            if (tick[0] > 80) h.fail("the sponsons never fired");
        });
    }
}
