package ru.aiforge.realwar.field;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Steel leg-hold trap: snaps shut on whoever steps on the pan, holds them until someone right-clicks it open (re-arms). */
public class BearTrapBlock extends HorizontalDirectionalBlock {
    public static final BooleanProperty OPEN = BooleanProperty.create("open");
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 2, 14);

    public BearTrapBlock(Properties props) {
        super(props);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OPEN, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, OPEN);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return this.defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState st, BlockGetter lv, BlockPos p, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState st, BlockGetter lv, BlockPos p, CollisionContext ctx) {
        return Shapes.empty();
    }

    @Override
    public void entityInside(BlockState st, Level level, BlockPos pos, Entity e) {
        if (!(e instanceof LivingEntity le) || e.getY() > pos.getY() + 0.4) return;
        if (e instanceof Player p && (p.isCreative() || p.isSpectator())) return;
        if (st.getValue(OPEN)) {
            if (!level.isClientSide) {
                level.setBlock(pos, st.setValue(OPEN, false), 3);
                level.playSound(null, pos, SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 1.2f, 0.7f);
                level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 1.2f, 1.5f);
                le.hurt(level.damageSources().cactus(), 8.0f);
            }
            return;
        }
        // caught: held in place
        e.makeStuckInBlock(st, new Vec3(0.01, 0.4, 0.01));
        if (!level.isClientSide && e.tickCount % 20 == 0) {
            le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 4, false, false));
            if ((e.xOld != e.getX() || e.zOld != e.getZ())) le.hurt(level.damageSources().cactus(), 1.0f);
        }
    }

    @Override
    public InteractionResult use(BlockState st, Level level, BlockPos pos, Player p, InteractionHand hand, BlockHitResult hit) {
        if (st.getValue(OPEN)) return InteractionResult.PASS;
        if (!level.isClientSide) {
            level.setBlock(pos, st.setValue(OPEN, true), 3);
            level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 1.0f, 0.8f);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
