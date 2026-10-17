package ru.aiforge.realwar.deco;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import ru.aiforge.realwar.registry.ModBlockEntities;

/** r16: client-side opening animation state of an animated door (lower half) or hatch. No saved data: the block state is the truth. */
public class AnimDoorBlockEntity extends BlockEntity {
    public float prog, prevProg;
    private boolean init;

    public AnimDoorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ANIM_DOOR.get(), pos, state);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, AnimDoorBlockEntity be) {
        if (!(state.getBlock() instanceof HasDoorKind h) || !state.hasProperty(BlockStateProperties.OPEN)) return;
        float target = state.getValue(BlockStateProperties.OPEN) ? 1f : 0f;
        if (!be.init) {
            be.prog = be.prevProg = target;
            be.init = true;
            return;
        }
        be.prevProg = be.prog;
        float step = 1f / h.doorKind().duration;
        if (be.prog < target) be.prog = Math.min(target, be.prog + step);
        else if (be.prog > target) be.prog = Math.max(target, be.prog - step);
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(1.2).expandTowards(0, 1, 0);
    }
}
