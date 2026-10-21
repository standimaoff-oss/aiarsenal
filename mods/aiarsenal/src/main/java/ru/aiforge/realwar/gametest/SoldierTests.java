package ru.aiforge.realwar.gametest;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import ru.aiforge.realwar.registry.ModEntities;
import ru.aiforge.realwar.soldier.SoldierClass;
import ru.aiforge.realwar.soldier.SoldierEntity;
import ru.aiforge.realwar.vehicle.VehicleEntity;

/**
 * r27: the AI soldiers must board real equipment that is actually nearby (tank driver
 * seat, ZU-23 gunner seat) and must NOT board anything when there is no equipment
 * within range.  Summon eggs live in the single "ai_units" creative section.
 */
@GameTestHolder(value = "aiarsenal")
@PrefixGameTestTemplate(value = false)
public final class SoldierTests {

    private SoldierTests() {
    }

    private static int pad(GameTestHelper h, int rx, int rz, int half) {
        ServerLevel level = h.getLevel();
        BlockPos c = h.absolutePos(new BlockPos(rx, 0, rz));
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.getX(), c.getZ());
        for (int dx = -half; dx <= half; ++dx) {
            for (int dz = -half; dz <= half; ++dz) {
                for (int y = top - 1; y <= top + 40; ++y) {
                    level.setBlock(new BlockPos(c.getX() + dx, y, c.getZ() + dz),
                            y == top - 1 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
        return top;
    }

    private static VehicleEntity spawnVehicle(ServerLevel level, EntityType<? extends VehicleEntity> type, double x, double y, double z, float yaw) {
        VehicleEntity e = (VehicleEntity) type.create(level);
        e.moveTo(x, y, z, yaw, 0.0f);
        level.addFreshEntity((Entity) e);
        return e;
    }

    private static SoldierEntity spawnSoldier(ServerLevel level, SoldierClass c, double x, double y, double z) {
        return SoldierEntity.create(level, c, new Vec3(x, y, z), 0f, null);
    }

    @GameTest(template = "arena", batch = "rw_soldier", timeoutTicks = 600)
    public static void soldier_tanker_boards_nearby_tank(GameTestHelper h) {
        int top = pad(h, 0, 0, 12);
        ServerLevel l = h.getLevel();
        VehicleEntity tank = spawnVehicle(l, ModEntities.T34.get(), 4.0, top, 0.0, 90f);
        SoldierEntity s = spawnSoldier(l, SoldierClass.TANKER, 9.0, top, 0.0);
        h.assertFalse(s.isPassenger(), "танкист сидел до старта");
        h.succeedWhen(() -> {
            h.assertTrue(s.isPassenger(), "танкист не сел в стоящий рядом Т-34");
            h.assertTrue(s.getVehicle() == tank, "танкист сел не в свой танк");
        });
    }

    @GameTest(template = "arena", batch = "rw_soldier", timeoutTicks = 600)
    public static void soldier_tanker_boards_zu23_gunner_seat(GameTestHelper h) {
        int top = pad(h, 0, 0, 12);
        ServerLevel l = h.getLevel();
        VehicleEntity zu = spawnVehicle(l, ModEntities.ZU23.get(), 0.0, top, 0.0, 0f);
        SoldierEntity s = spawnSoldier(l, SoldierClass.TANKER, 3.0, top, 0.0);
        h.succeedWhen(() -> {
            h.assertTrue(s.isPassenger(), "танкист не сел за ЗУ-23-2");
            h.assertTrue(s.getVehicle() == zu, "танкист сел не в ЗУ-23");
            int seat = zu.seatOf(s);
            h.assertTrue(seat >= 0, "место танкиста в ЗУ-23 не записано");
            h.assertTrue(zu.seatRole(seat).equals("gunner"), "танкист сел не за наводчика: " + zu.seatRole(seat));
        });
    }

    @GameTest(template = "arena", batch = "rw_soldier", timeoutTicks = 400)
    public static void soldier_without_vehicle_stays_on_ground(GameTestHelper h) {
        int top = pad(h, 0, 0, 12);
        ServerLevel l = h.getLevel();
        SoldierEntity s = spawnSoldier(l, SoldierClass.TANKER, 0.0, top, 0.0);
        h.runAtTickTime(160, () -> {
            h.assertFalse(s.isRemoved(), "танкист без техники исчез");
            h.assertFalse(s.isPassenger(), "танкист без техники рядом сел в никуда");
            h.assertTrue(s.isAlive(), "боец погиб без причины");
            h.succeed();
        });
    }

    @GameTest(template = "arena", batch = "rw_soldier", timeoutTicks = 400)
    public static void soldier_assault_does_not_board(GameTestHelper h) {
        int top = pad(h, 0, 0, 12);
        ServerLevel l = h.getLevel();
        spawnVehicle(l, ModEntities.ZU23.get(), 0.0, top, 0.0, 0f);
        SoldierEntity s = spawnSoldier(l, SoldierClass.ASSAULT, 3.0, top, 0.0);
        h.runAtTickTime(160, () -> {
            h.assertFalse(s.isPassenger(), "штурмовик (пехота) не должен садиться в технику");
            h.succeed();
        });
    }

    @GameTest(template = "arena", batch = "rw_soldier", timeoutTicks = 400)
    public static void soldier_medic_heals_wounded(GameTestHelper h) {
        int top = pad(h, 0, 0, 12);
        ServerLevel l = h.getLevel();
        SoldierEntity wounded = spawnSoldier(l, SoldierClass.ASSAULT, 0.0, top, 0.0);
        SoldierEntity medic = spawnSoldier(l, SoldierClass.MEDIC, 2.0, top, 0.0);
        wounded.setHealth(4.0f);
        float before = wounded.getHealth();
        h.succeedWhen(() -> h.assertTrue(wounded.getHealth() > before + 0.5f, "медик не вылечил раненого бойца"));
    }

    @GameTest(template = "arena", batch = "rw_soldier")
    public static void soldier_eggs_live_in_single_tab(GameTestHelper h) {
        CreativeModeTab tab = BuiltInRegistries.CREATIVE_MODE_TAB.get(new ResourceLocation("aiarsenal", "ai_units"));
        h.assertTrue(tab != null, "нет креативной вкладки ai_units");
        if (tab == null) return;
        tab.buildContents(new CreativeModeTab.ItemDisplayParameters(
                net.minecraft.world.flag.FeatureFlagSet.of(), true, h.getLevel().registryAccess()));
        List<Item> items = new ArrayList<>();
        for (ItemStack st : tab.getDisplayItems()) items.add(st.getItem());
        for (String id : List.of("soldier_egg_commander", "soldier_egg_tanker", "soldier_egg_assault", "soldier_egg_medic")) {
            Item egg = BuiltInRegistries.ITEM.get(new ResourceLocation("aiarsenal", id));
            h.assertTrue(items.contains(egg), "вкладка ai_units не содержит " + id);
        }
        for (String sec : ru.aiforge.realwar.registry.ModTabs.SECTIONS) {
            if (sec.equals("ai_units")) continue;
            CreativeModeTab other = BuiltInRegistries.CREATIVE_MODE_TAB.get(new ResourceLocation("aiarsenal", sec));
            if (other == null) continue;
            other.buildContents(new CreativeModeTab.ItemDisplayParameters(
                    net.minecraft.world.flag.FeatureFlagSet.of(), true, h.getLevel().registryAccess()));
            List<Item> rest = new ArrayList<>();
            for (ItemStack st : other.getDisplayItems()) rest.add(st.getItem());
            h.assertTrue(rest.stream().noneMatch(i -> i.getDescriptionId().startsWith("item.aiarsenal.soldier_egg")),
                    "яйца призыва дублируются в разделе " + sec);
        }
    }
}
