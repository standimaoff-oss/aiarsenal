package ru.aiforge.realwar.deco;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Invisible, intangible light-15 cell placed where a searchlight beam lands. Lives only while a searchlight keeps
 *  refreshing it (server-side registry); otherwise it removes itself within ~2 s (also after a restart). */
public class SpotLightBlock extends Block {
    private static final Map<Long, Long> FRESH = new HashMap<>();   // key = dimension hash ^ pos, value = game time

    public SpotLightBlock(Properties p) {
        super(p);
    }

    private static long key(Level lv, BlockPos p) {
        return p.asLong() ^ ((long) lv.dimension().location().hashCode() << 52);
    }

    public static void refresh(Level lv, BlockPos p) {
        FRESH.put(key(lv, p), lv.getGameTime());
    }

    public static void forget(Level lv, BlockPos p) {
        FRESH.remove(key(lv, p));
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onPlace(BlockState s, Level lv, BlockPos p, BlockState old, boolean moving) {
        if (!lv.isClientSide) lv.scheduleTick(p, this, 20);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void tick(BlockState s, ServerLevel lv, BlockPos p, RandomSource r) {
        Long t = FRESH.get(key(lv, p));
        if (t == null || lv.getGameTime() - t > 30) {
            FRESH.remove(key(lv, p));
            lv.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
        } else {
            lv.scheduleTick(p, this, 20);
        }
    }

    @Override
    public RenderShape getRenderShape(BlockState s) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter lv, BlockPos p, CollisionContext c) {
        return Shapes.empty();
    }

    @Override
    public boolean propagatesSkylightDown(BlockState s, BlockGetter lv, BlockPos p) {
        return true;
    }
}
