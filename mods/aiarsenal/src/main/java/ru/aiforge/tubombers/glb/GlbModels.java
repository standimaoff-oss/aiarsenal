package ru.aiforge.tubombers.glb;

import ru.aiforge.tubombers.TuBombers;

import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Lazy, cached loader for GLB files shipped in the mod jar: {@code assets/<modid>/glb/<name>.glb}. */
public final class GlbModels {
    private static final Map<String, GlbModel> CACHE = new ConcurrentHashMap<>();

    public static final String TU95 = "tu95ms";
    public static final String TU160 = "tu160";
    public static final String BOMBS = "bombs";

    private GlbModels() {
    }

    public static GlbModel get(String name) {
        return CACHE.computeIfAbsent(name, GlbModels::loadNow);
    }

    private static GlbModel loadNow(String name) {
        String path = "/assets/" + TuBombers.MODID + "/glb/" + name + ".glb";
        long t0 = System.nanoTime();
        try (InputStream in = GlbModels.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("GLB not found in jar: " + path);
            GlbModel m = GlbModel.load(name, in);
            TuBombers.LOGGER.info("Loaded {} in {} ms", m, (System.nanoTime() - t0) / 1_000_000);
            return m;
        } catch (Exception e) {
            throw new IllegalStateException("Cannot load GLB " + path, e);
        }
    }
}
