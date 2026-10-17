/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.server.level.ServerLevel
 */
package ru.aiforge.realwar.blast;

import net.minecraft.server.level.ServerLevel;

public interface BlastTask {
    public boolean tick(ServerLevel var1, long var2);

    public String describe();
}

