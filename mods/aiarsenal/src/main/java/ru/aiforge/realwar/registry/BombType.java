/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.resources.ResourceLocation
 */
package ru.aiforge.realwar.registry;

import net.minecraft.resources.ResourceLocation;

public enum BombType {
    LITTLE_BOY("little_boy", 15.0, 580.0, 4400, 3.0f, 0.71f),
    RDS_4("rds_4", 28.0, 600.0, 1200, 2.5f, 0.82f);

    public final String id;
    public final double yieldKt;
    public final double burstHeightM;
    public final int massKg;
    public final float lengthM;
    public final float diameterM;

    private BombType(String id, double yieldKt, double burstHeightM, int massKg, float lengthM, float diameterM) {
        this.id = id;
        this.yieldKt = yieldKt;
        this.burstHeightM = burstHeightM;
        this.massKg = massKg;
        this.lengthM = lengthM;
        this.diameterM = diameterM;
    }

    public ResourceLocation modelPath() {
        return new ResourceLocation("aiarsenal", "models/glb/" + this.id + ".glb");
    }

    public static BombType byOrdinal(int i) {
        BombType[] v = BombType.values();
        return v[Math.floorMod(i, v.length)];
    }
}

