/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.resources.ResourceLocation
 */
package ru.aiforge.realwar.aircraft;

import net.minecraft.resources.ResourceLocation;

public enum AirBombType {
    FAB500("fab500m54", 500, 2.3f, 0.45f, 240.0, 0.55),
    FAB1500("fab1500m54", 1500, 3.3f, 0.63f, 760.0, 0.4),
    FAB5000("fab5000", 5000, 4.6f, 1.0f, 2400.0, 0.3),
    // reply 11: WW2 bombs of the He 111 / Pe-8
    SC250("sc250", 250, 1.64f, 0.37f, 130.0, 0.6),
    SC1000("sc1000", 1000, 2.58f, 0.66f, 530.0, 0.45),
    FAB250SV("fab250sv", 250, 1.5f, 0.29f, 100.0, 0.6),
    FAB1000("fab1000", 1000, 2.5f, 0.63f, 670.0, 0.45);

    public final String id;
    public final int massKg;
    public final float lengthM;
    public final float diameterM;
    public final double tntKg;
    public final double frag;

    private AirBombType(String id, int massKg, float lengthM, float diameterM, double tntKg, double frag) {
        this.id = id;
        this.massKg = massKg;
        this.lengthM = lengthM;
        this.diameterM = diameterM;
        this.tntKg = tntKg;
        this.frag = frag;
    }

    public ResourceLocation model() {
        return new ResourceLocation("aiarsenal", "models/glb/" + this.id + ".glb");
    }

    public static AirBombType byOrdinal(int i) {
        AirBombType[] v = AirBombType.values();
        return v[Math.floorMod(i, v.length)];
    }
}

