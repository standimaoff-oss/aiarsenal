package ru.aiforge.realwar.deco;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Horizontal lamps. wall = true: street light on a wall bracket (click a wall side, FACING = away from the wall);
 *  wall = false: tripod floodlight standing on the ground, shining where the player looked. */
public class FacingLampBlock extends LampBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    private final boolean wall;
    private static final VoxelShape TRIPOD = Block.box(2, 0, 2, 14, 16, 14);
    private static final VoxelShape[] WALL = new VoxelShape[4];

    static {
        WALL[Direction.NORTH.get2DDataValue()] = Block.box(5, 5, 0, 11, 13, 16);
        WALL[Direction.SOUTH.get2DDataValue()] = Block.box(5, 5, 0, 11, 13, 16);
        WALL[Direction.EAST.get2DDataValue()] = Block.box(0, 5, 5, 16, 13, 11);
        WALL[Direction.WEST.get2DDataValue()] = Block.box(0, 5, 5, 16, 13, 11);
    }

    public FacingLampBlock(boolean wall, Properties p) {
        super(0, p);
        this.wall = wall;
        this.registerDefaultState(this.defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        super.createBlockStateDefinition(b);
        b.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        if (wall) {
            Direction f = ctx.getClickedFace();
            if (!f.getAxis().isHorizontal()) return null;
            BlockState s = powered(this.defaultBlockState().setValue(FACING, f), ctx);
            return s.canSurvive(ctx.getLevel(), ctx.getClickedPos()) ? s : null;
        }
        return powered(this.defaultBlockState().setValue(FACING, ctx.getHorizontalDirection()), ctx);
    }

    @Override
    public boolean canSurvive(BlockState s, LevelReader lv, BlockPos p) {
        if (!wall) return Block.canSupportCenter(lv, p.below(), Direction.UP);
        Direction f = s.getValue(FACING);
        BlockPos q = p.relative(f.getOpposite());
        return lv.getBlockState(q).isFaceSturdy(lv, q, f);
    }

    @Override
    public BlockState updateShape(BlockState s, Direction d, BlockState ns, LevelAccessor lv, BlockPos p, BlockPos np) {
        return !s.canSurvive(lv, p) ? Blocks.AIR.defaultBlockState() : s;
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter lv, BlockPos p, CollisionContext c) {
        return wall ? WALL[s.getValue(FACING).get2DDataValue()] : TRIPOD;
    }

    @Override
    public BlockState rotate(BlockState s, Rotation r) {
        return s.setValue(FACING, r.rotate(s.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState s, Mirror m) {
        return s.rotate(m.getRotation(s.getValue(FACING)));
    }
}
