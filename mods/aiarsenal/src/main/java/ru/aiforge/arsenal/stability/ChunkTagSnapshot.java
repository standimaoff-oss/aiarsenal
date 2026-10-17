package ru.aiforge.arsenal.stability;

import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.nbt.CompoundTag;

/** IOWorker may hand a pending-write tag back to ChunkStorage's read path.
 * upgradeChunkTag inserts/removes __context even for current-version chunks.
 * Mutating that shared root while the IO thread serializes it can corrupt a save
 * or throw ConcurrentModificationException. Upgrade a deep, caller-owned snapshot.
 * Invoked at the upgrade boundary by the narrowly targeted Forge coremod.
 */
public final class ChunkTagSnapshot {
    private static final AtomicLong COPIES = new AtomicLong();
    private ChunkTagSnapshot() {}

    public static CompoundTag forUpgrade(CompoundTag pendingWrite) {
        CompoundTag copy = pendingWrite.copy();
        COPIES.incrementAndGet();
        return copy;
    }

    public static long copies() { return COPIES.get(); }
}
