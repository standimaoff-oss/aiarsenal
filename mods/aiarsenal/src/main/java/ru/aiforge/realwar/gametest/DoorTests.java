package ru.aiforge.realwar.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import ru.aiforge.realwar.deco.AnimDoorBlockEntity;
import ru.aiforge.realwar.deco.CamoCanopyBlock;
import ru.aiforge.realwar.deco.GateBlock;
import ru.aiforge.realwar.deco.GateBlockEntity;
import ru.aiforge.realwar.registry.ModBlocks;

/** r16 building blocks part 3: animated doors / hatches, multi-block gates, camouflage connections. */
@GameTestHolder("aiarsenal")
@PrefixGameTestTemplate(false)
public final class DoorTests {
    private DoorTests() {
    }

    private static BlockPos pad(GameTestHelper h, int rx, int rz, int half) {
        ServerLevel level = h.getLevel();
        BlockPos c = h.absolutePos(new BlockPos(rx, 0, rz));
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.getX(), c.getZ());
        for (int dx = -half; dx <= half; ++dx)
            for (int dz = -half; dz <= half; ++dz)
                for (int y = top - 2; y <= top + 6; ++y)
                    level.setBlock(new BlockPos(c.getX() + dx, y, c.getZ() + dz), y < top ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
        return c.atY(top);
    }

    private static void use(ServerLevel l, BlockPos p, Player pl) {
        l.getBlockState(p).use(l, pl, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(p), Direction.UP, p, false));
    }

    @GameTest(template = "arena", batch = "rw_doors", timeoutTicks = 60)
    public static void door_and_hatch_open(GameTestHelper h) {
        ServerLevel l = h.getLevel();
        BlockPos c = pad(h, 4, 4, 3);
        Player pl = h.makeMockPlayer();
        BlockState d = ModBlocks.DOOR_BUNKER.get().defaultBlockState().setValue(DoorBlock.FACING, Direction.EAST);
        l.setBlock(c, d, 3);
        d.getBlock().setPlacedBy(l, c, d, pl, ItemStack.EMPTY);
        BlockPos hp = c.relative(Direction.SOUTH, 2);
        l.setBlock(hp, ModBlocks.HATCH_BUNKER.get().defaultBlockState().setValue(TrapDoorBlock.FACING, Direction.NORTH), 3);
        h.assertTrue(l.getBlockState(c.above()).is(ModBlocks.DOOR_BUNKER.get()) && l.getBlockState(c.above()).getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER, "upper half placed");
        h.assertTrue(l.getBlockEntity(c) instanceof AnimDoorBlockEntity && l.getBlockEntity(c.above()) == null, "animation BE only on the lower half");
        h.assertTrue(l.getBlockEntity(hp) instanceof AnimDoorBlockEntity, "hatch BE");
        use(l, c.above(), pl);
        use(l, hp, pl);
        h.succeedWhen(() -> {
            h.assertTrue(l.getBlockState(c).getValue(DoorBlock.OPEN) && l.getBlockState(c.above()).getValue(DoorBlock.OPEN), "door opened by hand (both halves)");
            h.assertTrue(l.getBlockState(hp).getValue(TrapDoorBlock.OPEN), "hatch opened by hand");
        });
    }

    @GameTest(template = "arena", batch = "rw_doors", timeoutTicks = 80)
    public static void sliding_gate_multiblock(GameTestHelper h) {
        ServerLevel l = h.getLevel();
        BlockPos c = pad(h, 4, 4, 4).relative(Direction.WEST, 2);
        Player pl = h.makeMockPlayer();
        GateBlock g = (GateBlock) ModBlocks.GATE_SLIDING.get();
        BlockState m = g.defaultBlockState().setValue(GateBlock.FACING, Direction.NORTH);
        l.setBlock(c, m, 3);
        g.setPlacedBy(l, c, m, pl, ItemStack.EMPTY);
        int n = 0;
        for (int i = 0; i < 5; i++)
            for (int j = 0; j < 2; j++) {
                BlockState s = l.getBlockState(c.east(i).above(j));
                if (s.is(g) && s.getValue(GateBlock.OX) == i && s.getValue(GateBlock.OY) == j) n++;
            }
        h.assertTrue(n == 10, "5x2 footprint filled, got " + n);
        h.assertTrue(l.getBlockEntity(c) instanceof GateBlockEntity && l.getBlockEntity(c.east(2)) == null, "BE only on the master");
        BlockPos mid = c.east(2);
        h.assertFalse(l.getBlockState(mid).getCollisionShape(l, mid, CollisionContext.empty()).isEmpty(), "closed gate blocks the way");
        use(l, c.east(3).above(), pl);
        h.assertTrue(l.getBlockState(c).getValue(GateBlock.OPEN) && l.getBlockState(c.east(4).above()).getValue(GateBlock.OPEN), "use on a filler opens the whole gate");
        h.assertTrue(l.getBlockState(mid).getCollisionShape(l, mid, CollisionContext.empty()).isEmpty(), "open gate lets you through");
        h.assertFalse(l.getBlockState(c).getCollisionShape(l, c, CollisionContext.empty()).isEmpty(), "posts stay solid");
        l.destroyBlock(c.east(3), false);
        h.succeedWhen(() -> {
            for (int i = 0; i < 5; i++)
                for (int j = 0; j < 2; j++) h.assertTrue(l.getBlockState(c.east(i).above(j)).isAir(), "whole gate removed at " + i + "," + j);
        });
    }

    @GameTest(template = "arena", batch = "rw_doors", timeoutTicks = 80)
    public static void boom_gate_redstone(GameTestHelper h) {
        ServerLevel l = h.getLevel();
        BlockPos c = pad(h, 4, 4, 4).relative(Direction.NORTH, 2);
        GateBlock g = (GateBlock) ModBlocks.GATE_BOOM.get();
        BlockState m = g.defaultBlockState().setValue(GateBlock.FACING, Direction.EAST);
        l.setBlock(c, m, 3);
        g.setPlacedBy(l, c, m, null, ItemStack.EMPTY);
        BlockPos tip = c.south(4);                                // facing east -> extends to the south
        h.assertTrue(l.getBlockState(tip).is(g) && l.getBlockState(tip).getValue(GateBlock.OX) == 4, "boom extends to the right of the facing");
        l.setBlock(tip.east(), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
        h.succeedWhen(() -> {
            BlockState s = l.getBlockState(c);
            h.assertTrue(s.getValue(GateBlock.POWERED) && s.getValue(GateBlock.OPEN), "redstone at the far end raises the arm: " + s);
        });
    }

    @GameTest(template = "arena", batch = "rw_doors", timeoutTicks = 60)
    public static void camo_connections(GameTestHelper h) {
        ServerLevel l = h.getLevel();
        BlockPos c = pad(h, 4, 4, 3);
        BlockPos a = c.above(2);
        for (int i = 0; i < 2; i++) l.setBlock(a.east(i), Block.updateFromNeighbourShapes(ModBlocks.CAMO_CANOPY_FOREST.get().defaultBlockState(), l, a.east(i)), 3);
        for (int i = 0; i < 3; i++) l.setBlock(c.north(1).south(i), ModBlocks.CAMO_SCREEN_DESERT.get().defaultBlockState(), 3);
        for (int i = 0; i < 3; i++) {
            BlockPos p = c.north(1).south(i);
            l.setBlock(p, Block.updateFromNeighbourShapes(l.getBlockState(p), l, p), 3);
        }
        l.setBlock(c.west(2), ModBlocks.CAMO_BUSH_SNOW.get().defaultBlockState(), 3);
        h.succeedWhen(() -> {
            BlockState w = l.getBlockState(a), e = l.getBlockState(a.east());
            h.assertTrue(w.getValue(CamoCanopyBlock.EAST) && !w.getValue(CamoCanopyBlock.WEST) && e.getValue(CamoCanopyBlock.WEST), "canopies join: " + w + " / " + e);
            BlockState mid = l.getBlockState(c);
            h.assertTrue(mid.getValue(IronBarsBlock.NORTH) && mid.getValue(IronBarsBlock.SOUTH) && !mid.getValue(IronBarsBlock.EAST), "screen links like a fence: " + mid);
            h.assertTrue(l.getBlockState(c.west(2)).getCollisionShape(l, c.west(2), CollisionContext.empty()).isEmpty(), "bush has no collision");
        });
    }
}
