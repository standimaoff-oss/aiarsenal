package ru.aiforge.realwar.field;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/** Field blocks that go off when a blast (shell, bomb, another mine, a drum) destroys them: chain reactions. */
public interface Detonatable {
    /** Called by BlastUtil.destroy / vanilla explosions after the block was removed. */
    void sympathetic(ServerLevel level, BlockPos pos, BlockState state);
}
