/*
 * Decompiled with CFR 0.152.
 */
package ru.aiforge.realwar.blast;

import java.util.LinkedHashMap;
import java.util.Map;

final class ColumnOrder {
    final int maxD;
    final int[] dx;
    final int[] dz;
    final int[] d2;
    private static final Map<Integer, ColumnOrder> CACHE = new LinkedHashMap<Integer, ColumnOrder>(4, 0.75f, true){

        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, ColumnOrder> eldest) {
            return this.size() > 3;
        }
    };

    static synchronized ColumnOrder get(int maxD) {
        return CACHE.computeIfAbsent(maxD, ColumnOrder::new);
    }

    private ColumnOrder(int maxD) {
        this.maxD = maxD;
        int m2 = maxD * maxD;
        int[] start = new int[m2 + 2];
        for (int x = -maxD; x <= maxD; ++x) {
            for (int z = -maxD; z <= maxD; ++z) {
                int q = x * x + z * z;
                if (q > m2) continue;
                int n = q + 1;
                start[n] = start[n] + 1;
            }
        }
        for (int i = 1; i < start.length; ++i) {
            int n = i;
            start[n] = start[n] + start[i - 1];
        }
        int total = start[m2 + 1];
        this.dx = new int[total];
        this.dz = new int[total];
        this.d2 = new int[total];
        int[] pos = new int[m2 + 1];
        System.arraycopy(start, 0, pos, 0, m2 + 1);
        for (int x = -maxD; x <= maxD; ++x) {
            for (int z = -maxD; z <= maxD; ++z) {
                int q = x * x + z * z;
                if (q > m2) continue;
                int p = pos[q]++;
                this.dx[p] = x;
                this.dz[p] = z;
                this.d2[p] = q;
            }
        }
    }

    int size() {
        return this.d2.length;
    }

    int maxD2() {
        return this.maxD * this.maxD;
    }

    int countWithin(long r2) {
        if (r2 < 0L) {
            return 0;
        }
        if (r2 >= (long)this.maxD2()) {
            return this.d2.length;
        }
        int lo = 0;
        int hi = this.d2.length;
        while (lo < hi) {
            int mid = lo + hi >>> 1;
            if ((long)this.d2[mid] <= r2) {
                lo = mid + 1;
                continue;
            }
            hi = mid;
        }
        return lo;
    }
}

