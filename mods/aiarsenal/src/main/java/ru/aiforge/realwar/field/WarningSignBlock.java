package ru.aiforge.realwar.field;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Enamelled steel warning plate (600 x 600 mm): on a pipe post with a concrete footing, or screwed to a wall. */
public class WarningSignBlock extends HorizontalDirectionalBlock {
    public static final BooleanProperty WALL = BooleanProperty.create("wall");
    private static final VoxelShape POST = Shapes.or(Block.box(7, 0, 7, 9, 16, 9), Block.box(3, 11, 6.5, 13, 16, 8.5));
    private static final VoxelShape[] WALLS = new VoxelShape[4];

    static {
        // wall plate on the face opposite to FACING (horizontal index: S, W, N, E)
        WALLS[Direction.NORTH.get2DDataValue()] = Block.box(3, 3.8, 15.4, 13, 13.8, 16);
        WALLS[Direction.SOUTH.get2DDataValue()] = Block.box(3, 3.8, 0, 13, 13.8, 0.6);
        WALLS[Direction.WEST.get2DDataValue()] = Block.box(15.4, 3.8, 3, 16, 13.8, 13);
        WALLS[Direction.EAST.get2DDataValue()] = Block.box(0, 3.8, 3, 0.6, 13.8, 13);
    }

    public WarningSignBlock(Properties props) {
        super(props);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(WALL, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, WALL);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction f = ctx.getClickedFace();
        if (f.getAxis().isHorizontal()) return this.defaultBlockState().setValue(FACING, f).setValue(WALL, true);
        return this.defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite()).setValue(WALL, false);
    }

    @Override
    public boolean canSurvive(BlockState st, LevelReader lv, BlockPos p) {
        if (st.getValue(WALL)) {
            Direction back = st.getValue(FACING).getOpposite();
            return lv.getBlockState(p.relative(back)).isFaceSturdy(lv, p.relative(back), st.getValue(FACING));
        }
        return Block.canSupportCenter(lv, p.below(), Direction.UP);
    }

    @Override
    public BlockState updateShape(BlockState st, Direction d, BlockState ns, LevelAccessor lv, BlockPos p, BlockPos np) {
        return !st.canSurvive(lv, p) ? Blocks.AIR.defaultBlockState() : super.updateShape(st, d, ns, lv, p, np);
    }

    @Override
    public VoxelShape getShape(BlockState st, BlockGetter lv, BlockPos p, CollisionContext ctx) {
        return st.getValue(WALL) ? WALLS[st.getValue(FACING).get2DDataValue()] : POST;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState st, BlockGetter lv, BlockPos p, CollisionContext ctx) {
        return st.getValue(WALL) ? Shapes.empty() : Block.box(7, 0, 7, 9, 16, 9);
    }
}
