package ru.aiforge.realwar.client.render;

import java.util.Arrays;
import java.util.Locale;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.aiforge.arsenal.AiArsenal;

/** Opt-in same-scene A/B timing. Measures render event duration, NOT a hardware GPU certification. */
@Mod.EventBusSubscriber(modid="aiarsenal", value=Dist.CLIENT)
public final class FrameTimingQa {
    private static String mode;
    private static long start, previous;
    private static int count, warmup;
    private static final double[] renderMs = new double[40], intervalMs = new double[40];
    private FrameTimingQa() {}
    public static void start(boolean reference) {
        if (!Boolean.getBoolean("aiarsenal.visualQa")) throw new IllegalStateException("QA disabled");
        GlbRenderer.qaReference(reference);
        mode = reference ? "scan" : "index";
        count = 0; warmup = 12; previous = 0;
    }
    public static void stop() { mode = null; GlbRenderer.qaReference(false); }
    @SubscribeEvent
    public static void frame(TickEvent.RenderTickEvent e) {
        if (mode == null) return;
        long now = System.nanoTime();
        if (e.phase == TickEvent.Phase.START) { start = now; return; }
        if (warmup-- > 0) { previous = start; return; }
        renderMs[count] = (now - start) / 1e6;
        intervalMs[count] = (start - previous) / 1e6; previous = start;
        if (++count == renderMs.length) {
            Arrays.sort(renderMs); Arrays.sort(intervalMs);
            AiArsenal.LOGGER.info(String.format(Locale.ROOT,
                    "FRAME QA mode=%s frames=%d renderMedianMs=%.3f renderP95Ms=%.3f intervalMedianMs=%.3f",
                    mode, count, renderMs[20], renderMs[37], intervalMs[20]));
            mode = null;
        }
    }
}
