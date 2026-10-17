package ru.aiforge.realwar.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import ru.aiforge.realwar.deco.DirectionalLampBlock;
import ru.aiforge.realwar.deco.FlagBlock;
import ru.aiforge.realwar.deco.FlagBlockEntity;
import ru.aiforge.realwar.deco.FlagpoleBlock;
import ru.aiforge.realwar.deco.LampBlock;
import ru.aiforge.realwar.deco.SearchlightBlock;
import ru.aiforge.realwar.deco.SearchlightBlockEntity;
import ru.aiforge.realwar.registry.ModBlocks;

/** r14 building blocks part 2: flagpole / flag, lamps, searchlight, armour plates. */
@GameTestHolder("aiarsenal")
@PrefixGameTestTemplate(false)
public final class DecoTests {
    private DecoTests() {
    }

    private static BlockPos pad(GameTestHelper h, int rx, int rz, int half) {
        ServerLevel level = h.getLevel();
        BlockPos c = h.absolutePos(new BlockPos(rx, 0, rz));
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.getX(), c.getZ());
        for (int dx = -half; dx <= half; ++dx)
            for (int dz = -half; dz <= half; ++dz)
                for (int y = top - 2; y <= top + 8; ++y)
                    level.setBlock(new BlockPos(c.getX() + dx, y, c.getZ() + dz), y < top ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
        return c.atY(top);
    }

    private static void put(ServerLevel l, BlockPos p, BlockState s) {
        l.setBlock(p, Block.updateFromNeighbourShapes(s, l, p), 3);
    }

    @GameTest(template = "arena", batch = "rw_deco", timeoutTicks = 60)
    public static void flag_on_flagpole(GameTestHelper h) {
        ServerLevel l = h.getLevel();
        BlockPos c = pad(h, 4, 4, 2);
        put(l, c, ModBlocks.FLAGPOLE.get().defaultBlockState());
        put(l, c.above(), ModBlocks.FLAGPOLE.get().defaultBlockState());
        put(l, c.above(2), ModBlocks.FLAG_RUSSIA.get().defaultBlockState().setValue(FlagBlock.FACING, Direction.EAST));
        h.succeedWhen(() -> {
            BlockState a = l.getBlockState(c), b = l.getBlockState(c.above()), f = l.getBlockState(c.above(2));
            h.assertTrue(a.getValue(FlagpoleBlock.BOTTOM) && !a.getValue(FlagpoleBlock.TOP), "footing only at the bottom: " + a);
            h.assertTrue(!b.getValue(FlagpoleBlock.BOTTOM) && !b.getValue(FlagpoleBlock.TOP), "middle section: " + b);
            h.assertTrue(f.getValue(FlagpoleBlock.TOP) && !f.getValue(FlagpoleBlock.BOTTOM), "flag on top carries the finial: " + f);
            h.assertTrue(l.getBlockEntity(c.above(2)) instanceof FlagBlockEntity, "flag block entity");
        });
    }

    @GameTest(template = "arena", batch = "rw_deco", timeoutTicks = 80)
    public static void lamp_switches_by_redstone(GameTestHelper h) {
        ServerLevel l = h.getLevel();
        BlockPos c = pad(h, 4, 10, 2);
        BlockPos lamp = c;
        put(l, lamp, ModBlocks.LAMP_BULKHEAD.get().defaultBlockState().setValue(DirectionalLampBlock.FACING, Direction.UP));
        h.assertTrue(l.getBlockState(lamp).getValue(LampBlock.LIT), "placed lit");
        l.setBlock(lamp.east(), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
        h.assertTrue(l.getBlockState(lamp).getValue(LampBlock.POWERED), "sees power");
        h.runAfterDelay(5, () -> l.setBlock(lamp.east(), Blocks.AIR.defaultBlockState(), 3));
        h.runAfterDelay(8, () -> h.succeedWhen(() -> {
            BlockState s = l.getBlockState(lamp);
            h.assertTrue(!s.getValue(LampBlock.LIT) && !s.getValue(LampBlock.POWERED), "power off -> lamp off: " + s);
            h.assertTrue(l.getBrightness(LightLayer.BLOCK, lamp) < 15, "light removed: " + l.getBrightness(LightLayer.BLOCK, lamp));
        }));
    }

    @GameTest(template = "arena", batch = "rw_deco", timeoutTicks = 120)
    public static void searchlight_lights_and_detects(GameTestHelper h) {
        ServerLevel l = h.getLevel();
        BlockPos c = pad(h, 8, 8, 7);
        BlockPos sl = c.offset(0, 0, 6);
        for (int dx = -3; dx <= 3; ++dx)
            for (int dy = 0; dy <= 4; ++dy) l.setBlock(c.offset(dx, dy, -7), Blocks.STONE.defaultBlockState(), 2);
        put(l, sl, ModBlocks.SEARCHLIGHT.get().defaultBlockState().setValue(SearchlightBlock.FACING, Direction.NORTH));
        SearchlightBlockEntity be = (SearchlightBlockEntity) l.getBlockEntity(sl);
        h.assertTrue(be != null, "block entity");
        be.basePitch = 0f;
        be.cycleMode();                                   // OFF -> FIXED
        h.assertTrue(be.mode == 1 && l.getBlockState(sl).getValue(SearchlightBlock.LIT), "on, lit block");
        Husk husk = EntityType.HUSK.create(l);
        husk.setNoAi(true);
        husk.moveTo(c.getX() + 0.5, c.getY(), c.getZ() + 0.5, 0, 0);
        l.addFreshEntity(husk);
        BlockPos spot = c.offset(0, 1, -6);
        h.succeedWhen(() -> {
            h.assertTrue(l.getBlockState(spot).is(ModBlocks.SPOTLIGHT.get()), "light spot on the wall: " + l.getBlockState(spot) + " be.spot=" + be.spot());
            h.assertTrue(l.getBrightness(LightLayer.BLOCK, spot) == 15, "spot is lit");
            h.assertTrue(husk.hasEffect(MobEffects.GLOWING), "target in the beam glows");
            h.assertTrue(l.getBlockState(sl).getSignal(l, sl, Direction.NORTH) == 15, "redstone output while it sees the target");
        });
    }

    @GameTest(template = "arena", batch = "rw_deco", timeoutTicks = 40)
    public static void searchlight_off_removes_spot(GameTestHelper h) {
        ServerLevel l = h.getLevel();
        BlockPos c = pad(h, 8, 8, 4);
        BlockPos sl = c.offset(0, 0, 3);
        for (int dx = -1; dx <= 1; ++dx)
            for (int dy = 0; dy <= 3; ++dy) l.setBlock(c.offset(dx, dy, -3), Blocks.STONE.defaultBlockState(), 2);
        put(l, sl, ModBlocks.SEARCHLIGHT.get().defaultBlockState().setValue(SearchlightBlock.FACING, Direction.NORTH));
        SearchlightBlockEntity be = (SearchlightBlockEntity) l.getBlockEntity(sl);
        be.basePitch = 0f;
        be.cycleMode();
        h.runAfterDelay(10, () -> {
            h.assertTrue(be.spot() != null, "spot placed");
            BlockPos s = be.spot();
            l.setBlock(sl, Blocks.AIR.defaultBlockState(), 3);     // breaking the searchlight clears its spot
            h.succeedWhen(() -> h.assertTrue(!l.getBlockState(s).is(ModBlocks.SPOTLIGHT.get()), "spot removed with the searchlight"));
        });
    }

    @GameTest(template = "arena", batch = "rw_deco", timeoutTicks = 20)
    public static void armor_plates_blast_resistant(GameTestHelper h) {
        for (Block b : new Block[]{ModBlocks.ARMOR_PLATE.get(), ModBlocks.ARMOR_PLATE_STAR_RED.get(), ModBlocks.ARMOR_PLATE_VMF.get()})
            h.assertTrue(b.getExplosionResistance() >= 50f, "armour resistance " + b.getExplosionResistance());
        h.assertTrue(ModBlocks.ARMOR_PLATE.get().getExplosionResistance() > ModBlocks.STEEL_BLOCK.get().getExplosionResistance(), "armour beats steel block");
        h.succeed();
    }

    /** r15/r19: all 64 uniform pieces registered, in the right slots, with field/dress armour, craftable, and a zombie wears them. */
    @GameTest(template = "arena", batch = "rw_deco", timeoutTicks = 40)
    public static void uniforms_registered_and_wearable(GameTestHelper h) {
        ServerLevel l = h.getLevel();
        int n = 0;
        for (var e : ru.aiforge.realwar.registry.ModItems.UNIFORMS.entrySet()) {
            if (!(e.getValue().get() instanceof ru.aiforge.realwar.item.UniformItem u)) { h.fail("not uniform " + e.getKey()); return; }
            String slot = ru.aiforge.realwar.item.UniformItem.slotName(u.getType());
            if (!e.getKey().endsWith("_" + slot)) { h.fail("slot mismatch " + e.getKey()); return; }
            boolean field = ru.aiforge.realwar.item.UniformItem.field(u.set);
            if (u.getMaterial() != (field ? ru.aiforge.realwar.item.ModArmorMaterial.UNIFORM_FIELD : ru.aiforge.realwar.item.ModArmorMaterial.UNIFORM_DRESS)) { h.fail("material " + e.getKey()); return; }
            if (l.getRecipeManager().byKey(new net.minecraft.resources.ResourceLocation("aiarsenal", e.getKey())).isEmpty()) { h.fail("no recipe " + e.getKey()); return; }
            n++;
        }
        if (n != ru.aiforge.realwar.item.UniformItem.SETS.length * 4 || n != 128) { h.fail("uniform count " + n); return; }
        var z = h.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 2));
        z.setNoAi(true);
        final int base = z.getArmorValue();
        for (String sl : new String[]{"head", "chest", "legs", "feet"}) {
            var it = (ru.aiforge.realwar.item.UniformItem) ru.aiforge.realwar.registry.ModItems.UNIFORMS.get("uniform_ru_sergeant_" + sl).get();
            z.setItemSlot(it.getEquipmentSlot(), new net.minecraft.world.item.ItemStack(it));
        }
        h.runAfterDelay(2, () -> {
            int armor = z.getArmorValue() - base;
            if (armor != 8) h.fail("field armour " + armor);
            else h.succeed();
        });
    }
}
