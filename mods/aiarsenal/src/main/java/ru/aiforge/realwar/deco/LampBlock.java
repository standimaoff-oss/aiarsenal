package ru.aiforge.realwar.deco;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/** r14 lamps: LIT toggled by right-click; a redstone edge (power on / power off) switches it like a lamp switch.
 *  Light level is set per block through Properties.lightLevel(s -> LIT ? n : 0). Subclasses add the mounting. */
public class LampBlock extends Block {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    /** 0 = electric (click sound), 1 = kerosene (flame particles + smoke) */
    protected final int fuel;

    public LampBlock(int fuel, Properties p) {
        super(p);
        this.fuel = fuel;
        this.registerDefaultState(this.stateDefinition.any().setValue(LIT, true).setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(LIT, POWERED);
    }

    protected BlockState powered(BlockState s, BlockPlaceContext ctx) {
        boolean pw = ctx.getLevel().hasNeighborSignal(ctx.getClickedPos());
        return s.setValue(POWERED, pw).setValue(LIT, true);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return powered(this.defaultBlockState(), ctx);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState s, Level lv, BlockPos p, Player pl, InteractionHand h, BlockHitResult hit) {
        if (!pl.getItemInHand(h).isEmpty() && pl.isShiftKeyDown()) return InteractionResult.PASS;
        if (!lv.isClientSide) {
            lv.setBlock(p, s.cycle(LIT), 3);
            lv.playSound(null, p, fuel == 1 ? SoundEvents.FLINTANDSTEEL_USE : SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.5f, s.getValue(LIT) ? 0.7f : 1.2f);
        }
        return InteractionResult.sidedSuccess(lv.isClientSide);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void neighborChanged(BlockState s, Level lv, BlockPos p, Block b, BlockPos from, boolean moving) {
        if (lv.isClientSide) return;
        boolean pw = lv.hasNeighborSignal(p);
        if (pw != s.getValue(POWERED)) lv.setBlock(p, s.setValue(POWERED, pw).setValue(LIT, pw), 3);
    }

    @Override
    public void animateTick(BlockState s, Level lv, BlockPos p, RandomSource r) {
        if (fuel != 1 || !s.getValue(LIT)) return;
        double y = flameY(s);
        if (r.nextInt(3) == 0) lv.addParticle(ru.aiforge.arsenal.volumetric.VolumetricParticles.SMOKE.get(), p.getX() + 0.5, p.getY() + y + 0.22, p.getZ() + 0.5, 0, 0.01, 0);
        if (r.nextInt(6) == 0) lv.addParticle(ParticleTypes.SMALL_FLAME, p.getX() + 0.5, p.getY() + y + 0.03, p.getZ() + 0.5, 0, 0.002, 0);
    }

    protected double flameY(BlockState s) {
        return 0.14;
    }
}
