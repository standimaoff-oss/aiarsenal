package ru.aiforge.realwar.deco;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import ru.aiforge.realwar.registry.ModBlockEntities;

/** r17: anchor block entity of a wreck - only remembers whether it smoulders and makes the smoke columns client side. */
public class WreckBlockEntity extends BlockEntity {
    private boolean smoking;

    public WreckBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WRECK.get(), pos, state);
    }

    public boolean smoking() {
        return smoking;
    }

    public void setSmoking(boolean s) {
        smoking = s;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        smoking = tag.getBoolean("smoking");
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putBoolean("smoking", smoking);
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** Smoke sources are in canonical block space relative to the origin cell; rotate them like the blockstate. */
    public static void clientTick(Level level, BlockPos pos, BlockState state, WreckBlockEntity be) {
        if (!be.smoking || !(state.getBlock() instanceof WreckBlock wb)) return;
        RandomSource r = level.random;
        float[] sp = wb.kind.smoke;
        if (sp.length == 0) return;
        Direction f = state.getValue(WreckBlock.FACING);
        BlockPos o = WreckBlock.origin(pos, state);
        int q = (f.get2DDataValue() + 2) % 4;
        for (int k = 0; k < sp.length / 3; k++) {
            if (r.nextFloat() > 0.3f) continue;
            double cx = sp[k * 3] - 0.5, y = sp[k * 3 + 1], cz = sp[k * 3 + 2] - 0.5;
            for (int i = 0; i < q; i++) {
                double t = cx;
                cx = -cz;
                cz = t;
            }
            double x = o.getX() + 0.5 + cx + (r.nextDouble() - 0.5) * 0.6, z = o.getZ() + 0.5 + cz + (r.nextDouble() - 0.5) * 0.6;
            double yy = o.getY() + y - 0.1;
            level.addAlwaysVisibleParticle(ru.aiforge.arsenal.volumetric.VolumetricParticles.SMOKE.get(), true, x, yy, z, (r.nextDouble() - 0.5) * 0.01, 0.06 + r.nextDouble() * 0.03,
                    (r.nextDouble() - 0.5) * 0.01);
            if (r.nextFloat() < 0.15f) level.addParticle(ru.aiforge.arsenal.volumetric.VolumetricParticles.SMOKE.get(), x, yy, z, 0, 0.04, 0);
            if (r.nextFloat() < 0.06f) level.addParticle(ParticleTypes.FLAME, x, yy, z, 0, 0.01, 0);
        }
    }
}
