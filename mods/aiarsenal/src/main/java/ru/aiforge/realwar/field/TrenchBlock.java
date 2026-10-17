package ru.aiforge.realwar.field;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Trench furniture. WALL / WALL_TIN are full earth blocks whose revetment (boards / corrugated sheet) faces the trench (FACING);
 * DUCKBOARD is the 2 px floor grating, STEP the half-height firing step with a plank riser on the FACING side.
 */
public class TrenchBlock extends HorizontalDirectionalBlock {
    public enum Kind { WALL, WALL_TIN, DUCKBOARD, STEP }

    private static final VoxelShape DUCK = Block.box(0, 0, 0, 16, 2, 16);
    private static final VoxelShape STEP = Block.box(0, 0, 0, 16, 8, 16);
    public final Kind kind;

    public TrenchBlock(Kind kind, Properties props) {
        super(props);
        this.kind = kind;
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    public boolean isWall() {
        return this.kind == Kind.WALL || this.kind == Kind.WALL_TIN;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING);
    }

    /** Clicking the side of the trench puts the revetment toward you; otherwise it faces the player. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction f = ctx.getClickedFace();
        if (!f.getAxis().isHorizontal() || !this.isWall()) f = ctx.getHorizontalDirection().getOpposite();
        return this.defaultBlockState().setValue(FACING, f);
    }

    @Override
    public VoxelShape getShape(BlockState st, BlockGetter lv, BlockPos p, CollisionContext ctx) {
        return switch (this.kind) {
            case DUCKBOARD -> DUCK;
            case STEP -> STEP;
            default -> Shapes.block();
        };
    }
}
