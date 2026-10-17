package ru.aiforge.realwar.deco;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import ru.aiforge.realwar.registry.ModBlockEntities;

/** No state, no ticking: the cloth animation is purely client-side (world time + position phase). */
public class FlagBlockEntity extends BlockEntity {
    public FlagBlockEntity(BlockPos p, BlockState s) {
        super(ModBlockEntities.FLAG.get(), p, s);
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(3.4, 0, 3.4).expandTowards(0, -2.3, 0).inflate(0, 0.4, 0);   // 3 x 2 block cloth
    }
}
