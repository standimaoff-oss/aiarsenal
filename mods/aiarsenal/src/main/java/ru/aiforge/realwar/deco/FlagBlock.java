package ru.aiforge.realwar.deco;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

/** r14: top section of a flagpole carrying an animated 1.5 x 1 m flag. FACING = direction the cloth flies.
 *  Placed so the obverse faces the player (hoist on the viewer's left). */
public class FlagBlock extends FlagpoleBlock implements EntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public final String flag;
    public final ResourceLocation texture;

    public FlagBlock(String flag, Properties p) {
        super(p);
        this.flag = flag;
        this.texture = new ResourceLocation("aiarsenal", "textures/entity/flag/" + flag + ".png");
        this.registerDefaultState(this.stateDefinition.any().setValue(TOP, true).setValue(BOTTOM, true).setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        super.createBlockStateDefinition(b);
        b.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return withEnds(this.defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getClockWise()), ctx.getLevel(), ctx.getClickedPos());
    }

    @Override
    public BlockState rotate(BlockState s, Rotation r) {
        return s.setValue(FACING, r.rotate(s.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState s, Mirror m) {
        return s.rotate(m.getRotation(s.getValue(FACING)));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos p, BlockState s) {
        return new FlagBlockEntity(p, s);
    }
}
