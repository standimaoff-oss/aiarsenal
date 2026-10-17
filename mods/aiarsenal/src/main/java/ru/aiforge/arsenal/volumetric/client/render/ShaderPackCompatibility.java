package ru.aiforge.arsenal.volumetric.client.render;

import java.lang.reflect.Method;
import net.minecraftforge.fml.ModList;
import ru.aiforge.arsenal.AiArsenal;

/** No GL calls: consulted BEFORE state capture, framebuffer creation or depth blits.
 * Oculus owns the render targets while a shaderpack is active. The vanilla depth
 * texture contract is not sufficient in that pipeline. Until an explicit integration
 * is tested, use the existing particle fallback instead of touching its framebuffer.
 * This mitigates a compatibility risk; it does not diagnose a native driver crash. */
public final class ShaderPackCompatibility {
    private static boolean initialized;
    private static boolean unknown;
    private static Boolean previous;
    private static Object api;
    private static Method inUse;

    private ShaderPackCompatibility() {}

    public static boolean blocked() {
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
