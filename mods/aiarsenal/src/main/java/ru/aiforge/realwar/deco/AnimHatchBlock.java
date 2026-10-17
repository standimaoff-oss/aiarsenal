package ru.aiforge.realwar.deco;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import ru.aiforge.realwar.registry.ModBlockEntities;

/** r16: trapdoor logic (top / bottom half, redstone, waterlogging) with a static coaming frame and an animated lid. */
public class AnimHatchBlock extends TrapDoorBlock implements EntityBlock, HasDoorKind {
    public final DoorKind kind;

    public AnimHatchBlock(DoorKind kind, Properties props) {
        super(props, kind.setType());
        this.kind = kind;
    }

    @Override
    public DoorKind doorKind() {
        return kind;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AnimDoorBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide && type == ModBlockEntities.ANIM_DOOR.get() ? (BlockEntityTicker<T>) (BlockEntityTicker<AnimDoorBlockEntity>) AnimDoorBlockEntity::clientTick : null;
    }
}
