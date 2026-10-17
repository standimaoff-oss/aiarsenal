package ru.aiforge.realwar.client;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.aiforge.realwar.deck.Decks;

/**
 * Local-player side of the walkable decks: runs at the end of the player's own tick (before its position packet is
 * sent), so the server receives the corrected, on-ground position in the same tick.
 */
@Mod.EventBusSubscriber(modid = "aiarsenal", value = Dist.CLIENT)
public final class DeckClient {
    private static final Map<Entity, double[]> PREV = new WeakHashMap<>();
    private static int standing = -1;
    private static int standingGroup = 0;
    private static int climbCd;

    private DeckClient() {
    }

    /** Id of the host the local player stands on, -1 if none (HUD / debugging). */
    public static int standingOn() {
        return standing;
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END || !ev.player.level().isClientSide) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(ev.player instanceof LocalPlayer p) || p != mc.player || mc.level == null) return;
        if (p.isPassenger() || p.isSpectator() || p.getAbilities().flying || p.isFallFlying()) {
            standing = -1;
            return;
        }
        int now = -1, nowGroup = 0;
        // the host stood on last tick first (it carries the player), then the others (walls / landing)
        java.util.List<Entity> hosts = Decks.hostsNear(mc.level, p.getBoundingBox().inflate(140, 64, 140));
        hosts.sort((a, b) -> Boolean.compare(b.getId() == standing, a.getId() == standing));
        for (Entity h : hosts) {
            ru.aiforge.realwar.deck.DeckShapes.Deck d = Decks.shape(h);
            double[] cur = Decks.pose(h);
            double[] prev = PREV.put(h, cur);
            int g = Decks.step(p, h, d, prev, cur, h.getId() == standing && now < 0 ? standingGroup : -1);
            if (g >= 0 && now < 0) { now = h.getId(); nowGroup = g; }
        }
        if (now < 0 && p.input != null && p.input.jumping && climbCd <= 0) {
            for (Entity h : hosts) {
                if (Decks.climb(Decks.shape(h), Decks.pose(h), p)) { now = h.getId(); climbCd = 8; break; }
            }
        }
        if (climbCd > 0) climbCd--;
        standing = now;
        standingGroup = nowGroup;
    }
}
