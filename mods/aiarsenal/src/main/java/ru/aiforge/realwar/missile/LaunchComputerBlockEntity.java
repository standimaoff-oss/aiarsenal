/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.core.BlockPos
 *  net.minecraft.nbt.CompoundTag
 *  net.minecraft.nbt.ListTag
 *  net.minecraft.nbt.Tag
 *  net.minecraft.network.chat.Component
 *  net.minecraft.network.protocol.Packet
 *  net.minecraft.network.protocol.game.ClientGamePacketListener
 *  net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.server.level.ServerPlayer
 *  net.minecraft.sounds.SoundEvent
 *  net.minecraft.sounds.SoundSource
 *  net.minecraft.util.Mth
 *  net.minecraft.util.RandomSource
 *  net.minecraft.world.level.Level
 *  net.minecraft.world.level.block.entity.BlockEntity
 *  net.minecraft.world.level.block.entity.BlockEntityType
 *  net.minecraft.world.level.block.state.BlockState
 *  net.minecraft.world.level.levelgen.Heightmap$Types
 *  net.minecraft.world.phys.Vec3
 *  org.jetbrains.annotations.Nullable
 */
package ru.aiforge.realwar.missile;

import ru.aiforge.realwar.missile.LaunchPadBlockEntity;
import ru.aiforge.realwar.missile.MissileType;
import ru.aiforge.realwar.missile.PadRegistry;
import ru.aiforge.realwar.missile.SirenBlockEntity;
import ru.aiforge.realwar.registry.ModS3;
import ru.aiforge.realwar.registry.ModSounds;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class LaunchComputerBlockEntity
extends BlockEntity {
    public static final int RANGE = 48;
    public static final int COUNTDOWN = 240;
    public static final int MAX_SPREAD = 200;
    public static final int A_SELECT = 0;
    public static final int A_TARGET = 1;
    public static final int A_SPREAD = 2;
    public static final int A_ARM = 3;
    public static final int A_LAUNCH = 4;
    public static final int A_ABORT = 5;
    public static final int A_CLEAR = 6;
    public static final int A_ALL = 7;
    public static final int A_SIREN = 8;
    public boolean hasTarget;
    public int tx;
    public int tz;
    public int spread = 10;
    public boolean armed;
    public int countdown = -1;
    public final Set<Long> selected = new HashSet<Long>();
    public final List<PadInfo> pads = new ArrayList<PadInfo>();
    public String msg = "";
    private String lastSig = "";

    public LaunchComputerBlockEntity(BlockPos pos, BlockState st) {
        super((BlockEntityType)ModS3.COMPUTER_BE.get(), pos, st);
    }

    private void sync() {
        this.setChanged();
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    public static void tick(Level l, BlockPos pos, BlockState st, LaunchComputerBlockEntity be) {
        if (l.isClientSide || !(l instanceof ServerLevel)) {
            return;
        }
        ServerLevel sl = (ServerLevel)l;
        long now = l.getGameTime();
        if (now % 10L == (pos.asLong() & 7L)) {
            be.refresh(sl);
        }
        if (be.countdown >= 0) {
            be.countdownTick(sl);
        }
    }

    private void refresh(ServerLevel sl) {
        ArrayList<PadInfo> now = new ArrayList<PadInfo>();
        for (BlockPos blockPos : PadRegistry.padsNear((Level)sl, this.worldPosition, 48.0)) {
            BlockEntity blockEntity = sl.getBlockEntity(blockPos);
            if (!(blockEntity instanceof LaunchPadBlockEntity)) continue;
            LaunchPadBlockEntity pad = (LaunchPadBlockEntity)blockEntity;
            now.add(new PadInfo(blockPos, pad.padLevel(), pad.kind() == null ? -1 : pad.kind().ordinal(), pad.state().ordinal()));
        }
        StringBuilder sb = new StringBuilder();
        for (PadInfo i : now) {
            sb.append(i.pos.asLong()).append(',').append(i.kind).append(',').append(i.state).append(';');
        }
        String string = sb.toString();
        if (!string.equals(this.lastSig)) {
            this.lastSig = string;
            this.pads.clear();
            this.pads.addAll(now);
            HashSet<Long> keep = new HashSet<Long>();
            for (PadInfo i : now) {
                keep.add(i.pos.asLong());
            }
            this.selected.retainAll(keep);
            this.sync();
        }
    }

    private PadInfo find(int idx) {
        return idx >= 0 && idx < this.pads.size() ? this.pads.get(idx) : null;
    }

    private void say(ServerPlayer p, String key, Object ... args) {
        this.msg = key;
        if (p != null) {
            p.displayClientMessage((Component)Component.translatable((String)key, (Object[])args), true);
        }
        this.sync();
    }

    public void handle(ServerPlayer p, int action, int a, int b) {
        Level level = this.level;
        if (!(level instanceof ServerLevel)) {
            return;
        }
        ServerLevel sl = (ServerLevel)level;
        if (p.distanceToSqr((double)this.worldPosition.getX() + 0.5, (double)this.worldPosition.getY() + 0.5, (double)this.worldPosition.getZ() + 0.5) > 100.0) {
            return;
        }
        switch (action) {
            case 0: {
                PadInfo i = this.find(a);
                if (i == null || this.countdown >= 0) break;
                if (!this.selected.remove(i.pos.asLong())) {
                    this.selected.add(i.pos.asLong());
                }
                this.sync();
                break;
            }
            case 7: {
                if (this.countdown >= 0) {
                    return;
                }
                this.selected.clear();
                if (a == 1) {
                    for (PadInfo i : this.pads) {
                        if (i.state != LaunchPadBlockEntity.State.READY.ordinal() || i.kind < 0) continue;
                        this.selected.add(i.pos.asLong());
                    }
                }
                this.sync();
                break;
            }
            case 1: {
                if (Math.abs(a) > 29999000 || Math.abs(b) > 29999000 || this.countdown >= 0) {
                    return;
                }
                this.hasTarget = true;
                this.tx = a;
                this.tz = b;
                this.say(p, "message.aiarsenal.rw.computer.target_set", a, b);
                break;
            }
            case 6: {
                if (this.countdown >= 0) {
                    return;
                }
                this.hasTarget = false;
                this.armed = false;
                this.sync();
                break;
            }
            case 2: {
                this.spread = Mth.clamp((int)a, (int)0, (int)200);
                this.sync();
                break;
            }
            case 3: {
                if (this.countdown >= 0) {
                    return;
                }
                this.armed = !this.armed;
                this.level.playSound(null, this.worldPosition, (SoundEvent)ModSounds.ARM_BEEP.get(), SoundSource.BLOCKS, 1.0f, this.armed ? 1.4f : 0.9f);
                this.sync();
                break;
            }
            case 4: {
                this.startCountdown(sl, p);
                break;
            }
            case 5: {
                this.abort(sl, p);
                break;
            }
            case 8: {
                SirenBlockEntity.alarmAround(sl, this.worldPosition, 200.0, 160);
                break;
            }
        }
    }

    private boolean inRange(LaunchPadBlockEntity pad, MissileType t) {
        double d = Math.hypot((double)this.tx + 0.5 - ((double)pad.getBlockPos().getX() + (double)pad.padLevel() / 2.0), (double)this.tz + 0.5 - ((double)pad.getBlockPos().getZ() + (double)pad.padLevel() / 2.0));
        return d >= t.cls.minRange && d <= t.cls.maxRange;
    }

    private List<LaunchPadBlockEntity> launchable(ServerLevel sl, ServerPlayer p) {
        ArrayList<LaunchPadBlockEntity> out = new ArrayList<LaunchPadBlockEntity>();
        for (PadInfo i : this.pads) {
            LaunchPadBlockEntity pad;
            BlockEntity blockEntity;
            if (!this.selected.contains(i.pos.asLong()) || !((blockEntity = sl.getBlockEntity(i.pos)) instanceof LaunchPadBlockEntity) || !(pad = (LaunchPadBlockEntity)blockEntity).ready()) continue;
            if (!this.inRange(pad, pad.kind())) {
                this.say(p, "message.aiarsenal.rw.computer.range", Component.translatable((String)("item.aiarsenal." + pad.kind().itemName())), (int)pad.kind().cls.minRange, (int)pad.kind().cls.maxRange);
                continue;
            }
            out.add(pad);
        }
        return out;
    }

    private void startCountdown(ServerLevel sl, ServerPlayer p) {
        if (this.countdown >= 0) {
            return;
        }
        if (!this.armed) {
            this.say(p, "message.aiarsenal.rw.computer.not_armed", new Object[0]);
            return;
        }
        if (!this.hasTarget) {
            this.say(p, "message.aiarsenal.rw.computer.no_target", new Object[0]);
            return;
        }
        if (this.launchable(sl, p).isEmpty()) {
            this.say(p, "message.aiarsenal.rw.computer.nothing", new Object[0]);
            return;
        }
        this.countdown = 240;
        SirenBlockEntity.alarmAround(sl, this.worldPosition, 120.0, 740);
        this.say(p, "message.aiarsenal.rw.computer.countdown", new Object[0]);
    }

    private void abort(ServerLevel sl, ServerPlayer p) {
        if (this.countdown < 0) {
            return;
        }
        this.countdown = -1;
        for (PadInfo i : this.pads) {
            BlockEntity blockEntity = sl.getBlockEntity(i.pos);
            if (!(blockEntity instanceof LaunchPadBlockEntity)) continue;
            LaunchPadBlockEntity pad = (LaunchPadBlockEntity)blockEntity;
            pad.abort();
        }
        SirenBlockEntity.alarmAround(sl, this.worldPosition, 120.0, 0);
        this.say(p, "message.aiarsenal.rw.computer.aborted", new Object[0]);
    }

    private void countdownTick(ServerLevel sl) {
        int pre = 70;
        if (this.countdown == pre) {
            List<LaunchPadBlockEntity> list = this.launchable(sl, null);
            int idx = 0;
            RandomSourceAdapter rnd = new RandomSourceAdapter(sl);
            for (LaunchPadBlockEntity pad : list) {
                double a = rnd.next() * Math.PI * 2.0;
                double r = Math.sqrt(rnd.next()) * (double)this.spread;
                double x = (double)this.tx + 0.5 + Math.cos(a) * r;
                double z = (double)this.tz + 0.5 + Math.sin(a) * r;
                double y = sl.hasChunk(Mth.floor((double)x) >> 4, Mth.floor((double)z) >> 4) ? (double)sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor((double)x), Mth.floor((double)z)) : (double)pad.getBlockPos().getY();
                pad.scheduleLaunch(new Vec3(x, y, z), pre + idx * 20);
                ++idx;
            }
            if (list.isEmpty()) {
                this.countdown = -1;
                this.sync();
                return;
            }
        }
        --this.countdown;
        if (this.countdown == 0 || this.countdown % 20 == 0) {
            if (this.countdown % 20 == 0) {
                this.level.playSound(null, this.worldPosition, (SoundEvent)ModSounds.ARM_BEEP.get(), SoundSource.BLOCKS, 0.8f, 1.0f);
            }
            this.sync();
        }
        if (this.countdown < 0) {
            this.countdown = -1;
            this.armed = false;
            this.msg = "message.aiarsenal.rw.computer.launched";
            this.sync();
        }
    }

    protected void saveAdditional(CompoundTag t) {
        super.saveAdditional(t);
        t.putBoolean("HasTarget", this.hasTarget);
        t.putInt("TX", this.tx);
        t.putInt("TZ", this.tz);
        t.putInt("Spread", this.spread);
        t.putBoolean("Armed", this.armed);
        t.putInt("Count", this.countdown);
        t.putString("Msg", this.msg);
        t.putLongArray("Sel", this.selected.stream().mapToLong(Long::longValue).toArray());
        ListTag l = new ListTag();
        for (PadInfo i : this.pads) {
            CompoundTag c = new CompoundTag();
            c.putLong("P", i.pos.asLong());
            c.putInt("L", i.level);
            c.putInt("K", i.kind);
            c.putInt("S", i.state);
            l.add(c);
        }
        t.put("Pads", (Tag)l);
    }

    public void load(CompoundTag t) {
        super.load(t);
        this.hasTarget = t.getBoolean("HasTarget");
        this.tx = t.getInt("TX");
        this.tz = t.getInt("TZ");
        this.spread = t.getInt("Spread");
        this.armed = t.getBoolean("Armed");
        this.countdown = t.contains("Count") ? t.getInt("Count") : -1;
        this.msg = t.getString("Msg");
        this.selected.clear();
        for (long v : t.getLongArray("Sel")) {
            this.selected.add(v);
        }
        this.pads.clear();
        java.util.Iterator<Tag> object = t.getList("Pads", 10).iterator();
        while (object.hasNext()) {
            Tag g = (Tag)object.next();
            CompoundTag c = (CompoundTag)g;
            this.pads.add(new PadInfo(BlockPos.of((long)c.getLong("P")), c.getInt("L"), c.getInt("K"), c.getInt("S")));
        }
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

    public record PadInfo(BlockPos pos, int level, int kind, int state) {
        public MissileType type() {
            return MissileType.byOrdinal(this.kind);
        }
    }

    private static final class RandomSourceAdapter {
        final RandomSource r;

        RandomSourceAdapter(ServerLevel l) {
            this.r = l.random;
        }

        double next() {
            return this.r.nextDouble();
        }
    }
}

