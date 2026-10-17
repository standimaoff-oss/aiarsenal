/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.core.BlockPos
 *  net.minecraft.core.Vec3i
 *  net.minecraft.resources.ResourceKey
 *  net.minecraft.world.level.Level
 */
package ru.aiforge.realwar.missile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public final class PadRegistry {
    private static final Map<ResourceKey<Level>, Set<BlockPos>> PADS = new ConcurrentHashMap<ResourceKey<Level>, Set<BlockPos>>();
    private static final Map<ResourceKey<Level>, Set<BlockPos>> SIRENS = new ConcurrentHashMap<ResourceKey<Level>, Set<BlockPos>>();

    private PadRegistry() {
    }

    private static Set<BlockPos> set(Map<ResourceKey<Level>, Set<BlockPos>> m, Level l) {
        return m.computeIfAbsent((ResourceKey<Level>)l.dimension(), k -> ConcurrentHashMap.newKeySet());
    }

    public static void addPad(Level l, BlockPos p) {
        PadRegistry.set(PADS, l).add(p.immutable());
    }

    public static void removePad(Level l, BlockPos p) {
        PadRegistry.set(PADS, l).remove(p);
    }

    public static void addSiren(Level l, BlockPos p) {
        PadRegistry.set(SIRENS, l).add(p.immutable());
    }

    public static void removeSiren(Level l, BlockPos p) {
        PadRegistry.set(SIRENS, l).remove(p);
    }

    public static List<BlockPos> padsNear(Level l, BlockPos c, double r) {
        return PadRegistry.near(PadRegistry.set(PADS, l), c, r);
    }

    public static List<BlockPos> sirensNear(Level l, BlockPos c, double r) {
        return PadRegistry.near(PadRegistry.set(SIRENS, l), c, r);
    }

    private static List<BlockPos> near(Set<BlockPos> s, BlockPos c, double r) {
        ArrayList<BlockPos> out = new ArrayList<BlockPos>();
        double r2 = r * r;
        for (BlockPos p : s) {
            if (!(p.distSqr((Vec3i)c) <= r2)) continue;
            out.add(p);
        }
        out.sort((a, b) -> Long.compare(a.asLong(), b.asLong()));
        return out;
    }

    public static void clear() {
        PADS.clear();
        SIRENS.clear();
    }
}

