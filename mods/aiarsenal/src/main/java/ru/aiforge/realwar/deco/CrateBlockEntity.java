package ru.aiforge.realwar.deco;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import ru.aiforge.realwar.registry.ModBlockEntities;

/** r17: loot crate inventory (3 rows) with chest-style opener counting; the lid angle is animated client side. */
public class CrateBlockEntity extends RandomizableContainerBlockEntity {
    private NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
    public float open, prevOpen;
    private boolean shouldOpen;
    private final ContainerOpenersCounter openers = new ContainerOpenersCounter() {
        @Override
        protected void onOpen(Level level, BlockPos pos, BlockState state) {
            level.playSound(null, pos, metal(state) ? SoundEvents.IRON_TRAPDOOR_OPEN : SoundEvents.BARREL_OPEN, SoundSource.BLOCKS, 0.6f,
                    0.9f + level.random.nextFloat() * 0.1f);
        }

        @Override
        protected void onClose(Level level, BlockPos pos, BlockState state) {
            level.playSound(null, pos, metal(state) ? SoundEvents.IRON_TRAPDOOR_CLOSE : SoundEvents.BARREL_CLOSE, SoundSource.BLOCKS, 0.6f,
                    0.9f + level.random.nextFloat() * 0.1f);
        }

        @Override
        protected void openerCountChanged(Level level, BlockPos pos, BlockState state, int old, int now) {
            level.blockEvent(pos, state.getBlock(), 1, now);
        }

        @Override
        protected boolean isOwnContainer(Player p) {
            return p.containerMenu instanceof ChestMenu m && m.getContainer() == CrateBlockEntity.this;
        }
    };

    public CrateBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CRATE.get(), pos, state);
    }

    private static boolean metal(BlockState s) {
        return s.getBlock() instanceof CrateBlock c && c.kind != CrateKind.AMMO;
    }

    public CrateKind kind() {
        return getBlockState().getBlock() instanceof CrateBlock c ? c.kind : CrateKind.AMMO;
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> l) {
        items = l;
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.aiarsenal.crate_" + kind().id);
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inv) {
        return ChestMenu.threeRows(id, inv, this);
    }

    @Override
    public int getContainerSize() {
        return 27;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
        if (!tryLoadLootTable(tag)) ContainerHelper.loadAllItems(tag, items);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!trySaveLootTable(tag)) ContainerHelper.saveAllItems(tag, items);
    }

    /** Loot table id still waiting to be rolled (null once opened / filled). */
    public net.minecraft.resources.ResourceLocation pendingLoot() {
        return lootTable;
    }

    @Override
    public void startOpen(Player p) {
        if (!remove && !p.isSpectator()) openers.incrementOpeners(p, getLevel(), getBlockPos(), getBlockState());
    }

    @Override
    public void stopOpen(Player p) {
        if (!remove && !p.isSpectator()) openers.decrementOpeners(p, getLevel(), getBlockPos(), getBlockState());
    }

    public void recheckOpen() {
        if (!remove) openers.recheckOpeners(getLevel(), getBlockPos(), getBlockState());
    }

    @Override
    public boolean triggerEvent(int id, int param) {
        if (id == 1) {
            shouldOpen = param > 0;
            return true;
        }
        return super.triggerEvent(id, param);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, CrateBlockEntity be) {
        be.prevOpen = be.open;
        float t = be.shouldOpen ? 1f : 0f;
        be.open += Math.signum(t - be.open) * Math.min(Math.abs(t - be.open), 0.12f);
    }
}
