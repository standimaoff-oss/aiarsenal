package ru.aiforge.realwar.fort;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Anti-vehicle obstacles with a facing: Czech hedgehog, dragon's teeth, Jersey (F-shape) concrete barrier. */
public class FacingFortBlock extends HorizontalDirectionalBlock {
    public enum Kind { HEDGEHOG, TEETH, BARRIER }

    private static final VoxelShape HEDGEHOG = Shapes.or(Block.box(1, 0, 1, 15, 4, 15), Block.box(3, 4, 3, 13, 15, 13));
    private static final VoxelShape TEETH = Shapes.or(Block.box(0.5, 0, 0.5, 15.5, 1, 15.5), Block.box(1.5, 1, 1.5, 14.5, 6, 14.5),
            Block.box(3, 6, 3, 13, 11, 13), Block.box(4.5, 11, 4.5, 11.5, 15.5, 11.5));
    private static final VoxelShape BARRIER_X = Shapes.or(Block.box(0, 0, 3, 16, 3, 13), Block.box(0, 3, 4.5, 16, 8, 11.5), Block.box(0, 8, 6.5, 16, 13, 9.5));
    private static final VoxelShape BARRIER_Z = Shapes.or(Block.box(3, 0, 0, 13, 3, 16), Block.box(4.5, 3, 0, 11.5, 8, 16), Block.box(6.5, 8, 0, 9.5, 13, 16));
    /** Taller collision so infantry cannot simply hop over a hedgehog or a barrier line. */
    private static final VoxelShape TALL = Block.box(2, 0, 2, 14, 20, 14);

    public final Kind kind;

    public FacingFortBlock(Kind kind, Properties props) {
        super(props);
        this.kind = kind;
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return this.defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState st, BlockGetter lv, BlockPos p, CollisionContext ctx) {
        return switch (this.kind) {
            case HEDGEHOG -> HEDGEHOG;
            case TEETH -> TEETH;
            case BARRIER -> st.getValue(FACING).getAxis() == Direction.Axis.Z ? BARRIER_X : BARRIER_Z;
        };
    }

    @Override
    public VoxelShape getCollisionShape(BlockState st, BlockGetter lv, BlockPos p, CollisionContext ctx) {
        VoxelShape s = this.getShape(st, lv, p, ctx);
        if (this.kind == Kind.HEDGEHOG) return Shapes.join(s, TALL, BooleanOp.OR);
        if (this.kind == Kind.BARRIER) {
            return st.getValue(FACING).getAxis() == Direction.Axis.Z ? Shapes.or(s, Block.box(0, 0, 6.5, 16, 18, 9.5)) : Shapes.or(s, Block.box(6.5, 0, 0, 9.5, 18, 16));
        }
        return s;
    }

    @Override
    public boolean isPathfindable(BlockState st, BlockGetter lv, BlockPos p, PathComputationType t) {
        return false;
    }

    @Override
    public void appendHoverText(net.minecraft.world.item.ItemStack stack, @javax.annotation.Nullable BlockGetter lv, java.util.List<net.minecraft.network.chat.Component> tip, net.minecraft.world.item.TooltipFlag flag) {
        tip.add(net.minecraft.network.chat.Component.translatable("tooltip.aiarsenal." + net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(this).getPath()).withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
