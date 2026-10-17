package ru.aiforge.realwar.deco;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import ru.aiforge.realwar.registry.ModBlockEntities;

/** r16: client animation state of a gate (only on the master block). */
public class GateBlockEntity extends BlockEntity {
    public float prog, prevProg;
    private boolean init;

    public GateBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GATE.get(), pos, state);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, GateBlockEntity be) {
        if (!(state.getBlock() instanceof GateBlock g)) return;
        float target = state.getValue(GateBlock.OPEN) ? 1f : 0f;
        if (!be.init) {
            be.prog = be.prevProg = target;
            be.init = true;
            return;
        }
        be.prevProg = be.prog;
        float step = 1f / g.kind.duration;
        if (be.prog < target) be.prog = Math.min(target, be.prog + step);
        else if (be.prog > target) be.prog = Math.max(target, be.prog - step);
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(6.5);
    }
}
