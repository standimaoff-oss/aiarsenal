package ru.aiforge.realwar.deco;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Hurricane lantern: stands on a floor or hangs on a chain from a ceiling (click the underside of a block). */
public class HangingLampBlock extends LampBlock {
    public static final BooleanProperty HANGING = BlockStateProperties.HANGING;
    private static final VoxelShape STAND = Block.box(5.5, 0, 5.5, 10.5, 7.5, 10.5);
    private static final VoxelShape HANG = Block.box(5.5, 6.5, 5.5, 10.5, 16, 10.5);

    public HangingLampBlock(int fuel, Properties p) {
        super(fuel, p);
        this.registerDefaultState(this.defaultBlockState().setValue(HANGING, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        super.createBlockStateDefinition(b);
        b.add(HANGING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState s = powered(this.defaultBlockState(), ctx);
        for (Direction d : ctx.getNearestLookingDirections()) {
            if (d.getAxis() != Direction.Axis.Y) continue;
            BlockState t = s.setValue(HANGING, d == Direction.UP);
            if (t.canSurvive(ctx.getLevel(), ctx.getClickedPos())) return t;
        }
        return null;
    }

    @Override
    public boolean canSurvive(BlockState s, LevelReader lv, BlockPos p) {
        Direction d = s.getValue(HANGING) ? Direction.UP : Direction.DOWN;
        return Block.canSupportCenter(lv, p.relative(d), d.getOpposite());
    }

    @Override
    public BlockState updateShape(BlockState s, Direction d, BlockState ns, LevelAccessor lv, BlockPos p, BlockPos np) {
        return (d == (s.getValue(HANGING) ? Direction.UP : Direction.DOWN) && !s.canSurvive(lv, p)) ? Blocks.AIR.defaultBlockState() : s;
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter lv, BlockPos p, CollisionContext c) {
        return s.getValue(HANGING) ? HANG : STAND;
    }

    @Override
    protected double flameY(BlockState s) {
        return s.getValue(HANGING) ? 0.56 : 0.14;
    }
}
