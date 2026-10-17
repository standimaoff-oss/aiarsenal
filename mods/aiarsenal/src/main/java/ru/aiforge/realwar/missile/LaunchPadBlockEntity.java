/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.core.BlockPos
 *  net.minecraft.nbt.CompoundTag
 *  net.minecraft.network.chat.Component
 *  net.minecraft.network.chat.MutableComponent
 *  net.minecraft.network.protocol.Packet
 *  net.minecraft.network.protocol.game.ClientGamePacketListener
 *  net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.sounds.SoundEvent
 *  net.minecraft.sounds.SoundSource
 *  net.minecraft.world.InteractionHand
 *  net.minecraft.world.entity.player.Player
 *  net.minecraft.world.item.Item
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.level.ItemLike
 *  net.minecraft.world.level.Level
 *  net.minecraft.world.level.block.entity.BlockEntity
 *  net.minecraft.world.level.block.entity.BlockEntityType
 *  net.minecraft.world.level.block.state.BlockState
 *  net.minecraft.world.phys.AABB
 *  net.minecraft.world.phys.Vec3
 *  org.jetbrains.annotations.Nullable
 */
package ru.aiforge.realwar.missile;

import ru.aiforge.realwar.missile.LaunchPadBlock;
import ru.aiforge.realwar.missile.MissileEntity;
import ru.aiforge.realwar.missile.MissileItem;
import ru.aiforge.realwar.missile.MissileType;
import ru.aiforge.realwar.missile.PadRegistry;
import ru.aiforge.realwar.missile.Warheads;
import ru.aiforge.realwar.registry.ModS3;
import ru.aiforge.realwar.registry.ModSounds;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class LaunchPadBlockEntity
extends BlockEntity {
    public static final int LOAD_TICKS = 150;
    public static final int PREP_TICKS = 60;
    public static final int FLAME_TICKS = 60;
    public static final double TABLE_H = 0.22;
    public static Consumer<LaunchPadBlockEntity> clientHook = be -> {};
    private MissileType kind;
    private State state = State.EMPTY;
    private long stateStart;
    private long launchAt;
    private Vec3 target = Vec3.ZERO;

    public LaunchPadBlockEntity(BlockPos pos, BlockState st) {
        super((BlockEntityType)ModS3.PAD_BE.get(), pos, st);
    }

    public int padLevel() {
        return ((LaunchPadBlock)this.getBlockState().getBlock()).level;
    }

    public MissileType kind() {
        return this.kind;
    }

    public State state() {
        return this.state;
    }

    public long stateStart() {
        return this.stateStart;
    }

    public long launchAt() {
        return this.launchAt;
    }

    public Vec3 target() {
        return this.target;
    }

    public boolean ready() {
        return this.state == State.READY && this.kind != null;
    }

    public void onLoad() {
        super.onLoad();
        if (this.level != null && !this.level.isClientSide) {
            PadRegistry.addPad(this.level, this.worldPosition);
        }
    }

    public void setRemoved() {
        super.setRemoved();
        if (this.level != null) {
            PadRegistry.removePad(this.level, this.worldPosition);
        }
    }

    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (this.level != null) {
            PadRegistry.removePad(this.level, this.worldPosition);
        }
    }

    private void sync() {
        this.setChanged();
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    private void set(State s) {
        this.state = s;
        this.stateStart = this.level.getGameTime();
        this.sync();
    }

    public static void tick(Level l, BlockPos pos, BlockState st, LaunchPadBlockEntity be) {
        if (l.isClientSide) {
            clientHook.accept(be);
            return;
        }
        long now = l.getGameTime();
        switch (be.state) {
            case LOADING: {
                if (now - be.stateStart < 150L) break;
                be.set(State.READY);
                break;
            }
            case PREP: {
                if (now < be.launchAt) break;
                be.fire((ServerLevel)l);
                break;
            }
            case LAUNCH: {
                if (now - be.stateStart < 60L) break;
                be.set(State.EMPTY);
                break;
            }
        }
    }

    public boolean canAccept(MissileType t) {
        return this.state == State.EMPTY && this.kind == null && t.padLevel() <= this.padLevel();
    }

    public void interact(Player pl, InteractionHand hand) {
        ItemStack held = pl.getItemInHand(hand);
        Item item = held.getItem();
        if (item instanceof MissileItem) {
            MissileItem mi = (MissileItem)item;
            if (this.state != State.EMPTY || this.kind != null) {
                pl.displayClientMessage((Component)Component.translatable((String)"message.aiarsenal.rw.pad.busy"), true);
            } else if (mi.type.padLevel() > this.padLevel()) {
                pl.displayClientMessage((Component)Component.translatable((String)"message.aiarsenal.rw.pad.toobig", (Object[])new Object[]{mi.type.padLevel() + "\u00d7" + mi.type.padLevel()}), true);
            } else {
                if (!pl.getAbilities().instabuild) {
                    held.shrink(1);
                }
                this.kind = mi.type;
                this.set(State.LOADING);
                this.level.playSound(null, this.worldPosition, (SoundEvent)ModSounds.HYDRAULIC.get(), SoundSource.BLOCKS, 1.6f, 0.8f);
                pl.displayClientMessage((Component)Component.translatable((String)"message.aiarsenal.rw.pad.loading"), true);
            }
            return;
        }
        if (pl.isShiftKeyDown() && held.isEmpty()) {
            if (this.state == State.READY && this.kind != null) {
                ItemStack back = new ItemStack((ItemLike)ModS3.missileItem(this.kind).get());
                if (!pl.getInventory().add(back)) {
                    pl.drop(back, false);
                }
                this.kind = null;
                this.set(State.EMPTY);
                this.level.playSound(null, this.worldPosition, (SoundEvent)ModSounds.HYDRAULIC.get(), SoundSource.BLOCKS, 1.4f, 0.7f);
            } else {
                pl.displayClientMessage((Component)Component.translatable((String)"message.aiarsenal.rw.pad.cannot_unload"), true);
            }
            return;
        }
        this.status(pl);
    }

    public void status(Player pl) {
        String key = switch (this.state) {
            default -> throw new IncompatibleClassChangeError();
            case EMPTY -> "message.aiarsenal.rw.pad.status.empty";
            case LOADING -> "message.aiarsenal.rw.pad.status.loading";
            case READY -> "message.aiarsenal.rw.pad.status.ready";
            case PREP -> "message.aiarsenal.rw.pad.status.prep";
            case LAUNCH -> "message.aiarsenal.rw.pad.status.launch";
        };
        MutableComponent m = Component.translatable((String)"message.aiarsenal.rw.pad.status", (Object[])new Object[]{this.padLevel() + "\u00d7" + this.padLevel(), Component.translatable((String)key)});
        if (this.kind != null) {
            m = m.copy().append(" \u2014 ").append((Component)Component.translatable((String)("item.aiarsenal." + this.kind.itemName())));
        }
        pl.displayClientMessage((Component)m, true);
    }

    public boolean scheduleLaunch(Vec3 tgt, int ticksToLaunch) {
        if (!this.ready() || this.level == null) {
            return false;
        }
        this.target = tgt;
        this.launchAt = this.level.getGameTime() + (long)Math.max(60, ticksToLaunch);
        this.set(State.PREP);
        this.level.playSound(null, this.worldPosition, (SoundEvent)ModSounds.HYDRAULIC.get(), SoundSource.BLOCKS, 2.0f, 0.6f);
        return true;
    }

    public void abort() {
        if (this.state == State.PREP && this.level != null && this.level.getGameTime() < this.launchAt) {
            this.set(State.READY);
        }
    }

    private void fire(ServerLevel sl) {
        MissileType t = this.kind;
        if (t == null) {
            this.set(State.EMPTY);
            return;
        }
        double h = LaunchPadBlock.HEIGHT[this.padLevel()];
        double c = (double)this.padLevel() / 2.0;
        Vec3 pos = new Vec3((double)this.worldPosition.getX() + c, (double)this.worldPosition.getY() + h + 0.22 + t.cls.length / 2.0 + 0.05, (double)this.worldPosition.getZ() + c);
        Vec3 pad = new Vec3(pos.x, (double)this.worldPosition.getY() + h, pos.z);
        MissileEntity.launch(sl, t, pos, this.target);
        sl.playSound(null, pos.x, pos.y, pos.z, (SoundEvent)ModSounds.ROCKET_LAUNCH_LARGE.get(), SoundSource.HOSTILE, 16.0f, 0.7f);
        Warheads.backblast(sl, pad, this.padLevel());
        this.kind = null;
        this.set(State.LAUNCH);
    }

    protected void saveAdditional(CompoundTag t) {
        super.saveAdditional(t);
        t.putInt("Kind", this.kind == null ? -1 : this.kind.ordinal());
        t.putInt("State", this.state.ordinal());
        t.putLong("Start", this.stateStart);
        t.putLong("LaunchAt", this.launchAt);
        t.putDouble("TX", this.target.x);
        t.putDouble("TY", this.target.y);
        t.putDouble("TZ", this.target.z);
    }

    public void load(CompoundTag t) {
        super.load(t);
        this.kind = MissileType.byOrdinal(t.getInt("Kind"));
        this.state = State.values()[Math.max(0, Math.min(State.values().length - 1, t.getInt("State")))];
        this.stateStart = t.getLong("Start");
        this.launchAt = t.getLong("LaunchAt");
        this.target = new Vec3(t.getDouble("TX"), t.getDouble("TY"), t.getDouble("TZ"));
    }

    public CompoundTag getUpdateTag() {
        CompoundTag t = new CompoundTag();
        this.saveAdditional(t);
        return t;
    }

    @Nullable
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create((BlockEntity)this);
    }

    public AABB getRenderBoundingBox() {
        int n = this.padLevel();
        return new AABB((double)(this.worldPosition.getX() - 6), (double)this.worldPosition.getY(), (double)(this.worldPosition.getZ() - 6), (double)(this.worldPosition.getX() + n + 6), (double)this.worldPosition.getY() + LaunchPadBlock.HEIGHT[n] + 16.0, (double)(this.worldPosition.getZ() + n + 6));
    }

    public static enum State {
        EMPTY,
        LOADING,
        READY,
        PREP,
        LAUNCH;

    }
}

