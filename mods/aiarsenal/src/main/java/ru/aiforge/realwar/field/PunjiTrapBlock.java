package ru.aiforge.realwar.field;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** r17: punji pit. Place it in a one-block hole: sharpened bamboo stakes under a leaf mat (COVERED). It has no collision,
 *  so whoever walks over it drops in, tearing the mat; the stakes wound (cactus damage) and slow. Re-cover with leaves. */
public class PunjiTrapBlock extends Block {
    public static final BooleanProperty COVERED = BooleanProperty.create("covered");
    private static final VoxelShape COVER = Block.box(0, 15, 0, 16, 16, 16);
    private static final VoxelShape OPEN = Block.box(1, 0, 1, 15, 11, 15);

    public PunjiTrapBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(COVERED, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(COVERED);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return s.getValue(COVERED) ? COVER : OPEN;
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getCollisionShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return Shapes.empty();
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean isPathfindable(BlockState s, BlockGetter l, BlockPos p, PathComputationType t) {
        return s.getValue(COVERED);      // mobs blunder into a covered pit, but avoid an open one
    }

    @Override
    @SuppressWarnings("deprecation")
    public void entityInside(BlockState st, Level level, BlockPos pos, Entity e) {
        if (level.isClientSide || !(e instanceof LivingEntity le)) return;
        if (e instanceof Player p && (p.isCreative() || p.isSpectator())) return;
        if (st.getValue(COVERED)) {
            level.setBlock(pos, st.setValue(COVERED, false), 3);
            level.playSound(null, pos, SoundEvents.AZALEA_LEAVES_BREAK, SoundSource.BLOCKS, 1.0f, 0.8f);
            st = st.setValue(COVERED, false);
        }
        e.makeStuckInBlock(st, new Vec3(0.55, 0.7, 0.55));
        if (e.getY() < pos.getY() + 0.75) {
            float dmg = e.fallDistance > 0.8f ? 6.0f : 3.0f;
            if (le.hurt(level.damageSources().cactus(), dmg))
                le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 120, 1));
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState st, Level level, BlockPos pos, Player p, InteractionHand hand, BlockHitResult hit) {
        ItemStack held = p.getItemInHand(hand);
        if (st.getValue(COVERED) || !held.is(ItemTags.LEAVES)) return InteractionResult.PASS;
        if (!level.isClientSide) {
            level.setBlock(pos, st.setValue(COVERED, true), 3);
            level.playSound(null, pos, SoundEvents.AZALEA_LEAVES_PLACE, SoundSource.BLOCKS, 1.0f, 1.0f);
            if (!p.isCreative()) held.shrink(1);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
