package ru.aiforge.arsenal.volumetric;

import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Non-destructive QA bridge. Only a client explicitly launched with
 * -Daiarsenal.visualQa=true accepts it. Normal player clients ignore it,
 * including attempts to change their local renderer settings. */
public record VisualQaPacket(String effect) {
    public static final Set<String> EFFECTS = Set.of("smoke", "blast", "nuke", "trail",
            "clear", "status", "budget", "unpack", "off", "on", "view_fov90", "view_fov70", "view_fov45",
            "view_shaderfail", "view_scale3", "view_scale2", "view_reload", "view_tabs", "view_modern", "view_ww2wear", "view_napwear", "view_naparms", "view_napguns", "view_napammo", "view_units", "view_first", "view_front", "view_back", "view_left", "view_right", "view_ready", "view_index", "view_perf_old", "view_perf_new", "view_perf_end", "view_motion",
            "view_move_idle", "view_move_walk", "view_move_slow", "view_move_run", "view_move_crouch");

    public void encode(FriendlyByteBuf buf) { buf.writeUtf(effect, 16); }
    public static VisualQaPacket decode(FriendlyByteBuf buf) { return new VisualQaPacket(buf.readUtf(16)); }

    public void handle(Supplier<NetworkEvent.Context> context) {
        if (Boolean.getBoolean("aiarsenal.visualQa") && EFFECTS.contains(effect)) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    ru.aiforge.arsenal.volumetric.client.VolumetricEffects.runVisualQa(effect));
        }
        context.get().setPacketHandled(true);
    }
}
