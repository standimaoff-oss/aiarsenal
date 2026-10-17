package ru.aiforge.realwar.gametest;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import ru.aiforge.realwar.field.BearTrapBlock;
import ru.aiforge.realwar.field.Detonations;
import ru.aiforge.realwar.field.MineBlock;
import ru.aiforge.realwar.field.TrenchBlock;
import ru.aiforge.realwar.registry.ModBlocks;
import ru.aiforge.realwar.registry.ModItems;

/** r12 field blocks: entrenching tool trench, mines (arming, pressure, anti-tank selectivity), bear trap, drum chain reaction. */
@GameTestHolder("aiarsenal")
@PrefixGameTestTemplate(false)
public final class FieldTests {
    private FieldTests() {
    }

    private static BlockPos pad(GameTestHelper h, int rx, int rz, int half, net.minecraft.world.level.block.Block ground) {
        ServerLevel level = h.getLevel();
        BlockPos c = h.absolutePos(new BlockPos(rx, 0, rz));
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.getX(), c.getZ());
        for (int dx = -half; dx <= half; ++dx)
            for (int dz = -half; dz <= half; ++dz)
                for (int y = top - 4; y <= top + 8; ++y)
                    level.setBlock(new BlockPos(c.getX() + dx, y, c.getZ() + dz), y < top ? ground.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
        return c.atY(top);
    }

    /** r13: two clicks -> an L-shaped trench (x leg, turn, z leg), dug progressively, revetted on every soil face. */
    @GameTest(template = "arena", batch = "rw_field", timeoutTicks = 160)
    public static void entrenching_tool_digs_trench(GameTestHelper h) {
        ServerLevel l = h.getLevel();
        BlockPos c = pad(h, 8, 8, 6, Blocks.DIRT);
        BlockPos t0 = c.below();
        BlockPos a = t0.offset(-3, 0, -3), b = t0.offset(3, 0, 1), corner = new BlockPos(b.getX(), a.getY(), a.getZ());
        FakePlayer p = new FakePlayer(l, new GameProfile(UUID.randomUUID(), "rw-field"));
        ItemStack tool = new ItemStack(ModItems.ENTRENCHING_TOOL.get());
        p.setItemInHand(InteractionHand.MAIN_HAND, tool);
        tool.useOn(new UseOnContext(p, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(a).add(0, 0.5, 0), Direction.UP, a, false)));
        h.assertTrue(ru.aiforge.realwar.field.EntrenchingToolItem.start(tool, l) != null, "start point stored");
        h.assertTrue(l.getBlockState(a).is(Blocks.DIRT), "first click digs nothing");
        tool.useOn(new UseOnContext(p, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(b).add(0, 0.5, 0), Direction.UP, b, false)));
        h.assertTrue(ru.aiforge.realwar.field.EntrenchingToolItem.start(tool, l) == null, "start point consumed");
        h.succeedWhen(() -> {
            h.assertTrue(ru.aiforge.realwar.field.TrenchJobs.pending() == 0, "digging");
            for (BlockPos q : new BlockPos[]{a, a.east(2), corner, corner.south(2), b}) {
                h.assertTrue(l.getBlockState(q).isAir(), "dug " + q.subtract(t0) + ": " + l.getBlockState(q));
                h.assertTrue(l.getBlockState(q.below()).is(ModBlocks.DUCKBOARD.get()), "duckboard " + q.subtract(t0));
            }
            h.assertTrue(l.getBlockState(new BlockPos(a.getX(), a.getY(), b.getZ())).is(Blocks.DIRT), "the other L corner stays");
            BlockState w = l.getBlockState(a.below().north());
            h.assertTrue(w.is(ModBlocks.TRENCH_WALL.get()) && w.getValue(TrenchBlock.FACING) == Direction.SOUTH, "x leg revetted: " + w);
            BlockState ce = l.getBlockState(corner.east());
            h.assertTrue(ce.is(ModBlocks.TRENCH_WALL.get()) && ce.getValue(TrenchBlock.FACING) == Direction.WEST, "turn outer wall: " + ce);
            BlockState cn = l.getBlockState(corner.below().north());
            h.assertTrue(cn.is(ModBlocks.TRENCH_WALL.get()) && cn.getValue(TrenchBlock.FACING) == Direction.SOUTH, "turn north wall: " + cn);
            BlockState zw = l.getBlockState(corner.south(2).west());
            h.assertTrue(zw.is(ModBlocks.TRENCH_WALL.get()) && zw.getValue(TrenchBlock.FACING) == Direction.EAST, "z leg revetted: " + zw);
            BlockState end = l.getBlockState(b.below().south());
            h.assertTrue(end.is(ModBlocks.TRENCH_WALL.get()) && end.getValue(TrenchBlock.FACING) == Direction.NORTH, "closed end: " + end);
            h.assertTrue(!l.getBlockState(corner.west()).is(ModBlocks.TRENCH_WALL.get()), "no wall inside the trench");
        });
    }

    @GameTest(template = "arena", batch = "rw_field", timeoutTicks = 200)
    public static void mines_arm_and_trigger(GameTestHelper h) {
        ServerLevel l = h.getLevel();
        BlockPos c = pad(h, 15, 15, 6, Blocks.STONE);
        BlockPos pmn = c.west(3), tm = c.east(3);
        l.setBlock(pmn, ModBlocks.MINE_PMN2.get().defaultBlockState(), 3);
        l.setBlock(tm, ModBlocks.MINE_TM62.get().defaultBlockState(), 3);
        h.assertTrue(!l.getBlockState(pmn).getValue(MineBlock.ARMED), "placed unarmed");
        int[] t = {0};
        Zombie[] z = {null, null};
        h.onEachTick(() -> {
            t[0]++;
            if (t[0] == MineBlock.ARM_TICKS + 10) {
                h.assertTrue(l.getBlockState(pmn).getValue(MineBlock.ARMED) && l.getBlockState(tm).getValue(MineBlock.ARMED), "armed after 3 s");
                for (int i = 0; i < 2; i++) {
                    Zombie zz = EntityType.ZOMBIE.create(l);
                    BlockPos at = i == 0 ? pmn : tm;
                    // a live mob dropped onto the mine: NoAI mobs never call move(), so they never "step" on anything
                    zz.moveTo(at.getX() + 0.5, at.getY() + 0.6, at.getZ() + 0.5, 0, 0);
                    l.addFreshEntity(zz);
                    z[i] = zz;
                }
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(t[0] > MineBlock.ARM_TICKS + 40, "wait");
            h.assertTrue(l.getBlockState(pmn).isAir(), "PMN-2 went off under the zombie");
            h.assertTrue(l.getBlockState(tm).is(ModBlocks.MINE_TM62.get()), "TM-62 ignores infantry");
            h.assertTrue(z[0] == null || !z[0].isAlive() || z[0].getHealth() < 10.0f, "PMN-2 victim hurt");
        });
    }

    @GameTest(template = "arena", batch = "rw_field", timeoutTicks = 100)
    public static void bear_trap_snaps_and_holds(GameTestHelper h) {
        ServerLevel l = h.getLevel();
        BlockPos c = pad(h, 24, 6, 3, Blocks.STONE);
        l.setBlock(c, ModBlocks.BEAR_TRAP.get().defaultBlockState(), 3);
        Pig pig = EntityType.PIG.create(l);
        pig.moveTo(c.getX() + 0.5, c.getY(), c.getZ() + 0.5, 0, 0);
        l.addFreshEntity(pig);
        h.succeedWhen(() -> {
            h.assertTrue(!l.getBlockState(c).getValue(BearTrapBlock.OPEN), "trap closed");
            h.assertTrue(pig.getHealth() < pig.getMaxHealth(), "pig hurt");
        });
    }

    @GameTest(template = "arena", batch = "rw_field_drums", timeoutTicks = 200)
    public static void fuel_drums_chain_react(GameTestHelper h) {
        ServerLevel l = h.getLevel();
        BlockPos c = pad(h, 6, 24, 5, Blocks.STONE);
        BlockPos d1 = c, d2 = c.east(2);
        l.setBlock(d1, ModBlocks.FUEL_DRUM_RED.get().defaultBlockState(), 3);
        l.setBlock(d2, ModBlocks.FUEL_DRUM_OLIVE.get().defaultBlockState(), 3);
        Detonations.queue(l, Vec3.atCenterOf(c.west(2)), 4.0, 0.0, 0, 2);       // a shell lands next to the stack
        h.succeedWhen(() -> {
            h.assertTrue(l.getBlockState(d1).getBlock() != ModBlocks.FUEL_DRUM_RED.get(), "near drum burst");
            h.assertTrue(l.getBlockState(d2).getBlock() != ModBlocks.FUEL_DRUM_OLIVE.get(), "second drum caught in the chain");
            h.assertTrue(Detonations.pending() == 0, "queue drained");
        });
    }
}
