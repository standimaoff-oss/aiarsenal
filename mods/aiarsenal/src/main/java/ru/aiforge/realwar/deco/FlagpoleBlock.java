package ru.aiforge.realwar.deco;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** r14: stackable galvanised flagpole. TOP = gilded finial (nothing pole-like above), BOTTOM = concrete footing. */
public class FlagpoleBlock extends Block {
    public static final BooleanProperty TOP = BooleanProperty.create("top");
    public static final BooleanProperty BOTTOM = BooleanProperty.create("bottom");
    protected static final VoxelShape POLE = Block.box(6.5, 0, 6.5, 9.5, 16, 9.5);
    protected static final VoxelShape FOOT = Block.box(4.5, 0, 4.5, 11.5, 2, 11.5);

    public FlagpoleBlock(Properties p) {
        super(p);
        this.registerDefaultState(this.stateDefinition.any().setValue(TOP, true).setValue(BOTTOM, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(TOP, BOTTOM);
    }

    public static boolean isPole(BlockState s) {
        return s.getBlock() instanceof FlagpoleBlock;
    }

    protected BlockState withEnds(BlockState s, BlockGetter lv, BlockPos p) {
        return s.setValue(TOP, !isPole(lv.getBlockState(p.above()))).setValue(BOTTOM, !isPole(lv.getBlockState(p.below())));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return withEnds(this.defaultBlockState(), ctx.getLevel(), ctx.getClickedPos());
    }

    @Override
    public BlockState updateShape(BlockState s, Direction d, BlockState ns, LevelAccessor lv, BlockPos p, BlockPos np) {
        if (d == Direction.UP) return s.setValue(TOP, !isPole(ns));
        if (d == Direction.DOWN) return s.setValue(BOTTOM, !isPole(ns));
        return s;
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter lv, BlockPos p, CollisionContext c) {
        return s.getValue(BOTTOM) ? net.minecraft.world.phys.shapes.Shapes.or(POLE, FOOT) : POLE;
    }

    @Override
    public boolean propagatesSkylightDown(BlockState s, BlockGetter lv, BlockPos p) {
        return true;
    }
}
