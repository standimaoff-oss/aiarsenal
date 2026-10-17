/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.resources.ResourceLocation
 */
package ru.aiforge.realwar.vehicle;

import net.minecraft.resources.ResourceLocation;

public record VehicleSpec(String id, float aabbW, float aabbH, float length, float height, float[][] wheels, float[][] seats, String[] seatRoles, float[][] parts, String[] partKinds, float[][] tubes, float[][] jacks, float[] yawPivot, float[] pitchPivot, float[][] canisters) {
    public ResourceLocation model() {
        if ("yars".equals(this.id)) {
            return new ResourceLocation("aiarsenal", "glb/yarstel.glb");   // shared with the arsenal TEL renderer (one 5 MB asset)
        }
        return new ResourceLocation("aiarsenal", "models/glb/" + this.id + ".glb");
    }

    public float wheelbase() {
        double f = 0.0;
        double r = 0.0;
        int nf = 0;
        int nr = 0;
        for (float[] w : this.wheels) {
            if (!(w[4] > 0.5f)) continue;
            f += (double)w[2];
            ++nf;
        }
        for (float[] w : this.wheels) {
            if (!(w[4] <= 0.5f)) continue;
            r += (double)w[2];
            ++nr;
        }
        if (nf == 0 || nr == 0) {
            return 3.0f;
        }
        return (float)(f / (double)nf - r / (double)nr);
    }

    public int seatCount() {
        return this.seats.length;
    }

    public int tubeCount() {
        return this.tubes.length > 0 ? this.tubes.length : this.canisters.length;
    }
}

