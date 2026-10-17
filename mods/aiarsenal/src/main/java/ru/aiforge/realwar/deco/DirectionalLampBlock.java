package ru.aiforge.realwar.deco;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Caged bulkhead lamp: on a floor, wall or ceiling; FACING points away from the surface it is screwed to. */
public class DirectionalLampBlock extends LampBlock {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    private static final VoxelShape[] SHAPES = new VoxelShape[6];

    static {
        double r = 2.7, h = 2.6;   // 165 mm radius, 160 mm tall (in px)
        SHAPES[Direction.UP.ordinal()] = Block.box(8 - r, 0, 8 - r, 8 + r, h, 8 + r);
        SHAPES[Direction.DOWN.ordinal()] = Block.box(8 - r, 16 - h, 8 - r, 8 + r, 16, 8 + r);
        SHAPES[Direction.NORTH.ordinal()] = Block.box(8 - r, 8 - r, 16 - h, 8 + r, 8 + r, 16);
        SHAPES[Direction.SOUTH.ordinal()] = Block.box(8 - r, 8 - r, 0, 8 + r, 8 + r, h);
        SHAPES[Direction.WEST.ordinal()] = Block.box(16 - h, 8 - r, 8 - r, 16, 8 + r, 8 + r);
        SHAPES[Direction.EAST.ordinal()] = Block.box(0, 8 - r, 8 - r, h, 8 + r, 8 + r);
    }

    public DirectionalLampBlock(int fuel, Properties p) {
        super(fuel, p);
        this.registerDefaultState(this.defaultBlockState().setValue(FACING, Direction.UP));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        super.createBlockStateDefinition(b);
        b.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return powered(this.defaultBlockState().setValue(FACING, ctx.getClickedFace()), ctx);
    }

    @Override
    public boolean canSurvive(BlockState s, LevelReader lv, BlockPos p) {
        Direction f = s.getValue(FACING);
        BlockPos q = p.relative(f.getOpposite());
        return lv.getBlockState(q).isFaceSturdy(lv, q, f);
    }

    @Override
    public BlockState updateShape(BlockState s, Direction d, BlockState ns, LevelAccessor lv, BlockPos p, BlockPos np) {
        return d == s.getValue(FACING).getOpposite() && !s.canSurvive(lv, p) ? Blocks.AIR.defaultBlockState() : s;
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter lv, BlockPos p, CollisionContext c) {
        return SHAPES[s.getValue(FACING).ordinal()];
    }

    @Override
    public VoxelShape getCollisionShape(BlockState s, BlockGetter lv, BlockPos p, CollisionContext c) {
        return s.getValue(FACING) == Direction.UP ? SHAPES[1] : Shapes.empty();
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
