/*
 * Decompiled with CFR 0.152.
 */
package ru.aiforge.realwar.aircraft;

import java.util.ArrayList;
import java.util.List;

public final class BayLayout {
    public static final int RDS = 0;
    public static final int F5000 = 1;
    public static final int F1500 = 2;
    public static final int F500 = 3;
    public static final String[] NAMES = new String[]{"rds_4", "fab5000", "fab1500m54", "fab500m54"};
    public static final int[] MAX = new int[]{1, 2, 12, 40};
    public static final int[] MASS = new int[]{1200, 5000, 1500, 500};
    public static final float[] LEN = new float[]{2.5f, 4.6f, 3.3f, 2.3f};
    public static final int MAX_MASS = 20000;
    public static final float SHELF_Y = 3.05f;
    public static final float Z_START = -2.85f;
    public static final float Z_END = 8.35f;

    private BayLayout() {
    }

    public static int mass(int[] c) {
        int m = 0;
        for (int i = 0; i < 4; ++i) {
            m += c[i] * MASS[i];
        }
        return m;
    }

    public static float centerLength(int[] c) {
        float l = 0.0f;
        int n = 0;
        for (int k : new int[]{0, 1}) {
            l += (float)c[k] * (LEN[k] + 0.1f);
            n += c[k];
        }
        return l;
    }

    public static int sideCapacity(int[] c) {
        return c[0] + c[1] > 0 ? 20 : 40;
    }

    public static int sideUsed(int[] c) {
        return c[3] + 3 * c[2];
    }

    public static String why(int[] c, int kind) {
        if (c[kind] >= MAX[kind]) {
            return "max";
        }
        if (BayLayout.mass(c) + MASS[kind] > 20000) {
            return "mass";
        }
        int[] t = (int[])c.clone();
        int n = kind;
        t[n] = t[n] + 1;
        if (BayLayout.centerLength(t) > 11.400001f) {
            return "length";
        }
        if (BayLayout.sideUsed(t) > BayLayout.sideCapacity(t)) {
            return "racks";
        }
        if (t[2] > (t[0] + t[1] > 0 ? 6 : 12)) {
            return "racks";
        }
        return null;
    }

    public static List<Slot> slots(int[] c) {
        float[] fArray;
        float[] fArray2;
        boolean center;
        ArrayList<Slot> out = new ArrayList<Slot>();
        float z = -2.85f;
        for (int k : new int[]{1, 0}) {
            for (int i = 0; i < c[k]; ++i) {
                out.add(new Slot(k, 0.0f, 3.05f, z + LEN[k] / 2.0f));
                z += LEN[k] + 0.1f;
            }
        }
        boolean bl = center = c[0] + c[1] > 0;
        if (center) {
            float[] fArray3 = new float[1];
            fArray2 = fArray3;
            fArray3[0] = 4.3f;
        } else {
            float[] fArray4 = new float[2];
            fArray4[0] = 4.3f;
            fArray2 = fArray4;
            fArray4[1] = 3.55f;
        }
        float[] t15 = fArray2;
        int per = 2 * t15.length;
        for (int i = 0; i < c[2]; ++i) {
            int slot = i / per;
            int r = i % per;
            out.add(new Slot(2, (float)(r % 2 == 0 ? 1 : -1) * 0.42f, t15[r / 2], -2.85f + LEN[2] * ((float)slot + 0.5f)));
        }
        if (center) {
            float[] fArray5 = new float[2];
            fArray5[0] = 4.45f;
            fArray = fArray5;
            fArray5[1] = 3.95f;
        } else {
            float[] fArray6 = new float[4];
            fArray6[0] = 4.45f;
            fArray6[1] = 3.95f;
            fArray6[2] = 3.45f;
            fArray = fArray6;
            fArray6[3] = 2.95f;
        }
        float[] t5 = fArray;
        int used15 = c[2] * 3;
        for (int i = 0; i < c[3]; ++i) {
            int cell = i + used15;
            int per5 = 2 * t5.length;
            int slot = cell / per5;
            int r = cell % per5;
            float zz = -2.85f + LEN[3] * ((float)slot + 0.5f);
            out.add(new Slot(3, (float)(r % 2 == 0 ? 1 : -1) * 0.42f, t5[r / 2], Math.min(zz, 7.2500005f)));
        }
        return out;
    }

    public record Slot(int kind, float x, float y, float z) {
    }
}

