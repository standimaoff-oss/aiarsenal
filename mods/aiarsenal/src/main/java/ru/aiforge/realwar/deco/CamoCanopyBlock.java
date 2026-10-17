package ru.aiforge.realwar.deco;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** r16: camouflage net canopy - horizontally connecting sheets (any colour joins any colour); open edges get a hanging fringe.
 *  No collision: you walk under and through it, it only hides what is below from above. */
public class CamoCanopyBlock extends Block {
    public static final BooleanProperty NORTH = PipeBlock.NORTH, EAST = PipeBlock.EAST, SOUTH = PipeBlock.SOUTH, WEST = PipeBlock.WEST;
    private static final VoxelShape SHAPE = Block.box(0, 12.5, 0, 16, 15.5, 16);

    public CamoCanopyBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(NORTH, false).setValue(EAST, false).setValue(SOUTH, false).setValue(WEST, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(NORTH, EAST, SOUTH, WEST);
    }

    private static BooleanProperty prop(Direction d) {
        return switch (d) {
            case NORTH -> NORTH;
            case EAST -> EAST;
            case SOUTH -> SOUTH;
            default -> WEST;
        };
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState s = defaultBlockState();
        for (Direction d : Direction.Plane.HORIZONTAL)
            s = s.setValue(prop(d), ctx.getLevel().getBlockState(ctx.getClickedPos().relative(d)).getBlock() instanceof CamoCanopyBlock);
        return s;
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(BlockState s, Direction d, BlockState n, LevelAccessor level, BlockPos pos, BlockPos np) {
        return d.getAxis().isHorizontal() ? s.setValue(prop(d), n.getBlock() instanceof CamoCanopyBlock) : s;
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public boolean propagatesSkylightDown(BlockState s, BlockGetter level, BlockPos pos) {
        return true;
    }
}
