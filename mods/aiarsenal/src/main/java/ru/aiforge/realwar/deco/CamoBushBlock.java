package ru.aiforge.realwar.deco;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** r16: artificial camouflage bush - leaf cards, random rotation (blockstate) and random XZ offset, no collision. */
public class CamoBushBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 15, 15);

    public CamoBushBlock(Properties props) {
        super(props);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        Vec3 o = s.getOffset(level, pos);
        return SHAPE.move(o.x, o.y, o.z);
    }

    @Override
    public boolean propagatesSkylightDown(BlockState s, BlockGetter level, BlockPos pos) {
        return true;
    }
}
