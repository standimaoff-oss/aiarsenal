package ru.aiforge.realwar.deco;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import ru.aiforge.realwar.registry.ModBlockEntities;

/** r16: vanilla door logic (two halves, hinge side, redstone, hand opening) with a static frame model and an animated leaf
 *  drawn by the lower half's block entity renderer (AnimDoorRenderer). */
public class AnimDoorBlock extends DoorBlock implements EntityBlock, HasDoorKind {
    public final DoorKind kind;

    public AnimDoorBlock(DoorKind kind, Properties props) {
        super(props, kind.setType());
        this.kind = kind;
    }

    @Override
    public DoorKind doorKind() {
        return kind;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? new AnimDoorBlockEntity(pos, state) : null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide && type == ModBlockEntities.ANIM_DOOR.get() ? (BlockEntityTicker<T>) (BlockEntityTicker<AnimDoorBlockEntity>) AnimDoorBlockEntity::clientTick : null;
    }
}
