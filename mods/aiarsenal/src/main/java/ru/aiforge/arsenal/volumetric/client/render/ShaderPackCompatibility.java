package ru.aiforge.arsenal.volumetric.client.render;

import java.lang.reflect.Method;
import net.minecraftforge.fml.ModList;
import ru.aiforge.arsenal.AiArsenal;

/**
 * Volumetric rendering is mandatory in this mod — with or without a shaderpack.
 * A pack (Photon/Iris/Oculus) never disables the raymarched smoke: the volume pass runs at AFTER_PARTICLES
 * against the current main render target and restores every piece of GL state it touches (GlStateSnapshot).
 * If a driver/pack combination ever makes a frame throw, VolumeGlRenderer backs off for a few seconds and
 * retries on its own — effects pause, they never degrade to vanilla particles.
 * This class now only reports whether a pack is active (diagnostics / QA status line).
 */
public final class ShaderPackCompatibility {
    private static boolean initialized;
    private static boolean unknown;
    private static Boolean previous;
    private static Object api;
    private static Method inUse;

    private ShaderPackCompatibility() {}

    /** Kept for call-site compatibility: volumetric effects are never blocked by a shaderpack. */
    public static boolean blocked() {
        return false;
    }

    /** Diagnostics only: is an Iris/Oculus shaderpack currently rendering? */
    public static boolean shaderPackActive() {
        if (!initialized) {
            initialized = true;
            boolean loader = ModList.get().isLoaded("oculus") || ModList.get().isLoaded("iris");
            try {
                Class<?> type = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                api = type.getMethod("getInstance").invoke(null);
                inUse = type.getMethod("isShaderPackInUse");
            } catch (ClassNotFoundException e) {
                unknown = loader; // no optional dependency on vanilla; fail closed with an unknown loader API
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                unknown = true;
                AiArsenal.LOGGER.warn("VolPyro: cannot query shaderpack API; using particle fallback", e);
            }
        }
        boolean blocked = unknown;
        if (!unknown && inUse != null) {
            try {
                // Do not cache the answer: enabling/disabling packs in-game changes it.
                blocked = !Boolean.FALSE.equals(inUse.invoke(api));
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                unknown = blocked = true;
                AiArsenal.LOGGER.warn("VolPyro: shaderpack query failed; using particle fallback", e);
            }
        }
        if (previous == null || previous != blocked) {
            previous = blocked;
            AiArsenal.LOGGER.info("VolPyro shaderpack guard: blocked={} reason={}", blocked,
                    unknown ? "unknown-api" : blocked ? "active-shaderpack" : "no-active-shaderpack");
        }
        return blocked;
    }
}
