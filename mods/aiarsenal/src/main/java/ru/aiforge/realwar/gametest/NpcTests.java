package ru.aiforge.realwar.gametest;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import ru.aiforge.realwar.craft.CraftEntity;
import ru.aiforge.realwar.craft.CraftKind;
import ru.aiforge.realwar.craft.CraftRegistry;
import ru.aiforge.realwar.handheld.FirearmItem;
import ru.aiforge.realwar.npc.Era;
import ru.aiforge.realwar.npc.Faction;
import ru.aiforge.realwar.npc.NpcGear;
import ru.aiforge.realwar.npc.Rank;
import ru.aiforge.realwar.npc.SoldierEntity;
import ru.aiforge.realwar.npc.SoldierSpawner;
import ru.aiforge.realwar.registry.ModEntities;
import ru.aiforge.realwar.vehicle.VehicleEntity;
import ru.aiforge.realwar.vehicle.VehicleKind;

/** r22: AI soldiers - nations, ranks, faction-locked crews, imperfect gunnery and searchable loot. */
@GameTestHolder("aiarsenal")
@PrefixGameTestTemplate(false)
public final class NpcTests {
    private NpcTests() {
    }

    private static int pad(GameTestHelper h, int rx, int rz, int half) {
        ServerLevel level = h.getLevel();
        BlockPos c = h.absolutePos(new BlockPos(rx, 0, rz));
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.getX(), c.getZ());
        for (int dx = -half; dx <= half; dx++) {
            for (int dz = -half; dz <= half; dz++) {
                for (int y = top - 1; y <= top + 40; y++) {
                    level.setBlock(new BlockPos(c.getX() + dx, y, c.getZ() + dz),
                            y == top - 1 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
        return top;
    }

    @GameTest(template = "arena", batch = "rw_npc", timeoutTicks = 200)
    public static void squad_has_ranks_nations_and_gear(GameTestHelper h) {
        int top = pad(h, 6, 6, 12);
        Vec3 at = new Vec3(h.absolutePos(new BlockPos(6, 0, 6)).getX() + 0.5, top, h.absolutePos(new BlockPos(6, 0, 6)).getZ() + 0.5);
        List<SoldierEntity> squad = SoldierSpawner.spawnSquad(h.getLevel(), at, Faction.RUSSIA, Era.WW2, 5, false, false);
        h.assertTrue(squad.size() == 5, "squad size " + squad.size());
        int sergeants = 0;
        for (SoldierEntity s : squad) {
            h.assertTrue(s.faction() == Faction.RUSSIA, "wrong faction " + s.faction());
            h.assertTrue(s.era() == Era.WW2, "wrong era " + s.era());
            if (s.rank() == Rank.COMMANDER) sergeants++;
            h.assertTrue(s.getMainHandItem().getItem() instanceof FirearmItem, "no firearm for " + s.rank());
            h.assertTrue(!s.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).isEmpty(), "no uniform");
            h.assertTrue(s.leaderId() == squad.get(0).getId(), "squad leader not wired");
            h.assertTrue(s.squadId() == squad.get(0).squadId(), "squad id mismatch");
        }
        h.assertTrue(sergeants == 1, "exactly one commander expected, got " + sergeants);
        h.succeed();
    }

    @GameTest(template = "arena", batch = "rw_npc", timeoutTicks = 120)
    public static void soldier_refuses_foreign_vehicle(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        int top = pad(h, 15, 15, 12);
        BlockPos c = h.absolutePos(new BlockPos(15, 0, 15));
        VehicleEntity tiger = (VehicleEntity) ModEntities.TIGER.get().create(level);
        tiger.moveTo(c.getX() + 3.5, top, c.getZ() + 0.5, 0f, 0f);
        level.addFreshEntity(tiger);
        SoldierEntity nato = SoldierSpawner.spawnOne(level, new Vec3(c.getX() + 0.5, top, c.getZ() + 0.5),
                Faction.NATO, Era.MODERN, Rank.CREW);
        h.assertTrue(nato != null, "soldier did not spawn");
        nato.setAssignedVehicle(tiger.getId());
        h.runAfterDelay(40, () -> {
            h.assertTrue(nato.getVehicle() == null, "a NATO soldier must never crew a German Tiger");
            tiger.discard();
            nato.discard();
            h.succeed();
        });
    }

    @GameTest(template = "arena", batch = "rw_npc", timeoutTicks = 260)
    public static void crew_drives_its_own_tank(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        int top = pad(h, 15, 15, 16);
        BlockPos c = h.absolutePos(new BlockPos(15, 0, 15));
        VehicleEntity tank = (VehicleEntity) ModEntities.T90M.get().create(level);
        tank.moveTo(c.getX() + 6.5, top, c.getZ() + 0.5, 0f, 0f);
        level.addFreshEntity(tank);
        Vec3 start = tank.position();
        SoldierEntity crew = SoldierSpawner.spawnOne(level, new Vec3(c.getX() + 0.5, top, c.getZ() + 0.5),
                Faction.RUSSIA, Era.MODERN, Rank.CREW);
        h.assertTrue(crew != null, "crew did not spawn");
        Vec3 anchor = crew.position().add(0, 0, 14);      // the squad is 14 blocks up the pad: the crew must drive to it
        crew.setSquad(1, -1, anchor);
        crew.setAssignedVehicle(tank.getId());
        h.onEachTick(() -> {
            if (tank.tickCount > 200) {
                h.assertTrue(tank.getPassengers().contains(crew), "the crew never boarded its own tank");
                h.assertTrue(tank.engineOn(), "the AI crew must start the engine");
                h.assertTrue(tank.position().distanceTo(start) > 1.5,
                        "the AI crew did not move the tank (moved " + String.format("%.2f", tank.position().distanceTo(start)) + " blocks)");
                h.succeed();
            }
        });
    }

    @GameTest(template = "arena", batch = "rw_npc", timeoutTicks = 120)
    public static void killed_soldier_loots_poor_kit(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        int top = pad(h, 8, 8, 8);
        BlockPos c = h.absolutePos(new BlockPos(8, 0, 8));
        Vec3 at = new Vec3(c.getX() + 0.5, top, c.getZ() + 0.5);
        SoldierEntity s = SoldierSpawner.spawnOne(level, at, Faction.GERMANY, Era.WW2, Rank.PRIVATE);
        h.assertTrue(s != null, "soldier did not spawn");
        s.hurt(level.damageSources().magic(), 1000f);
        h.assertTrue(!s.isAlive(), "soldier survived");
        h.runAfterDelay(5, () -> {
            List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(at, at).inflate(6.0));
            boolean gun = false;
            boolean uniform = false;
            for (ItemEntity it : drops) {
                ItemStack st = it.getItem();
                if (st.getItem() instanceof FirearmItem fi) {
                    gun = true;
                    int ammo = FirearmItem.ammo(st);
                    h.assertTrue(ammo >= 1 && ammo <= 4, "looted " + fi.kind.id + " carried " + ammo + " rounds, expected 1-4");
                } else if (st.isDamageableItem() && st.getDamageValue() > 0) {
                    uniform = true;
                }
            }
            h.assertTrue(gun, "no weapon in the loot");
            h.assertTrue(uniform, "no damaged uniform in the loot");
            drops.forEach(Entity::discard);
            h.succeed();
        });
    }

    @GameTest(template = "arena", batch = "rw_npc", timeoutTicks = 60)
    public static void gunnery_is_deliberately_imperfect(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        int top = pad(h, 8, 8, 8);
        BlockPos c = h.absolutePos(new BlockPos(8, 0, 8));
        Husk dummy = EntityType.HUSK.create(level);
        h.assertTrue(dummy != null, "target did not spawn");
        dummy.moveTo(c.getX() + 0.5, top, c.getZ() + 40.5, 180f, 0f);
        dummy.setNoAi(true);
        level.addFreshEntity(dummy);
        Vec3 from = new Vec3(c.getX() + 0.5, top + 1.6, c.getZ() + 0.5);
        Vec3 target = dummy.position().add(0, dummy.getBbHeight() * 0.55, 0);
        RandomSource rnd = RandomSource.create(4242L);

        double maxDeg = 0;
        double privateErr = 0;
        double veteranErr = 0;
        for (int i = 0; i < 120; i++) {
            Vec3 p = NpcGear.aimPoint(from, dummy, 0.58, rnd);
            privateErr += p.distanceTo(target);
            maxDeg = Math.max(maxDeg, Math.toDegrees(Math.atan2(p.subtract(from).normalize()
                    .subtract(target.subtract(from).normalize()).length(), 1.0)));
            veteranErr += NpcGear.aimPoint(from, dummy, 0.93, rnd).distanceTo(target);
        }
        h.assertTrue(maxDeg > 0.2, "a private must miss now and then: max deviation " + String.format("%.3f", maxDeg) + " deg");
        h.assertTrue(maxDeg < 20.0, "dispersion must stay usable: " + String.format("%.3f", maxDeg) + " deg");
        h.assertTrue(veteranErr < privateErr, "a veteran must shoot straighter than a private");
        dummy.discard();
        h.succeed();
    }

    @GameTest(template = "arena", batch = "rw_npc", timeoutTicks = 260)
    public static void helicopter_pilot_boards_and_takes_off(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        int top = pad(h, 15, 15, 20);
        BlockPos c = h.absolutePos(new BlockPos(15, 0, 15));
        CraftEntity heli = CraftRegistry.type(CraftKind.KA52).create(level);
        h.assertTrue(heli != null, "KA-52 did not spawn");
        heli.moveTo(c.getX() + 0.5, top + 0.2, c.getZ() + 0.5, 0f, 0f);
        heli.setFuel(1.0f);
        level.addFreshEntity(heli);
        double y0 = heli.getY();
        SoldierEntity pilot = SoldierSpawner.spawnOne(level, new Vec3(c.getX() + 2.5, top, c.getZ() + 0.5),
                Faction.RUSSIA, Era.MODERN, Rank.PILOT);
        h.assertTrue(pilot != null, "pilot did not spawn");
        pilot.setAssignedCraft(heli.getId());
        h.onEachTick(() -> {
            if (heli.tickCount > 220) {
                h.assertTrue(heli.getPassengers().contains(pilot), "the pilot never boarded the helicopter");
                h.assertTrue(heli.getY() > y0 + 4.0,
                        "the AI pilot did not lift off (gained " + String.format("%.2f", heli.getY() - y0)
                                + " blocks) engine=" + heli.engineOn() + " fuel=" + heli.fuel()
                                + " seat=" + heli.seatOf(pilot) + " stab=" + heli.flag(CraftEntity.FLAG_STAB)
                                + " forced=" + java.util.Arrays.toString(heli.forcedInput()));
                h.succeed();
            }
        });
    }

    @GameTest(template = "arena", batch = "rw_npc", timeoutTicks = 40)
    public static void nation_and_era_locks_are_consistent(GameTestHelper h) {
        h.assertTrue(Faction.NATO.allows(Era.MODERN) && !Faction.NATO.allows(Era.WW2), "NATO is a modern army only");
        h.assertTrue(Faction.GERMANY.allows(Era.WW2) && !Faction.GERMANY.allows(Era.NAPOLEONIC), "Wehrmacht is WWII only");
        h.assertTrue(Faction.FRANCE.allows(Era.NAPOLEONIC) && !Faction.FRANCE.allows(Era.MODERN), "France fields the 1812 army");
        h.assertTrue(Faction.RUSSIA.allows(Era.NAPOLEONIC) && Faction.RUSSIA.allows(Era.WW2) && Faction.RUSSIA.allows(Era.MODERN),
                "Russia has all three periods");
        h.assertTrue(!NpcGear.canUseVehicle(Faction.NATO, Era.MODERN, VehicleKind.TIGER), "NATO must not use a Tiger");
        h.assertTrue(NpcGear.canUseVehicle(Faction.RUSSIA, Era.WW2, VehicleKind.T34), "the Red Army crews the T-34");
        h.assertTrue(!NpcGear.canUseVehicle(Faction.RUSSIA, Era.WW2, VehicleKind.T90M), "no T-90 in 1943");
        h.assertTrue(!NpcGear.canUseCraft(Faction.GERMANY, Era.WW2, CraftKind.KA52), "no Ka-52 in the Wehrmacht");
        h.assertTrue(NpcGear.canUseCraft(Faction.GERMANY, Era.WW2, CraftKind.BF109), "Bf 109 belongs to the Wehrmacht");
        h.succeed();
    }
}
