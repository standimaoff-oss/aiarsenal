package ru.aiforge.realwar.deco;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import ru.aiforge.realwar.registry.ModBlockEntities;

public class BeaconLampBlockEntity extends BlockEntity {
    public BeaconLampBlockEntity(BlockPos p, BlockState s) {
        super(ModBlockEntities.BEACON_LAMP.get(), p, s);
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(3.2, 0.5, 3.2);
    }
}
