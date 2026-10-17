package ru.aiforge.arsenal.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import ru.aiforge.arsenal.core.Kind;
import ru.aiforge.arsenal.network.ArsenalNetwork;
import ru.aiforge.arsenal.network.ChargeListPacket;
import ru.aiforge.arsenal.network.DetonatorPacket;

import java.util.ArrayList;
import java.util.List;

/** Remote detonator: list of armed charges with fuze switch and individual / all detonation (two-step confirm). */
public class DetonatorScreen extends Screen {
    private static final int ROWS = 7;
    private List<ChargeListPacket.Entry> entries;
    private int page;
    private int armedId = Integer.MIN_VALUE;
    private long armedAt;

    public DetonatorScreen(List<ChargeListPacket.Entry> entries) {
        super(Component.translatable("gui.aiarsenal.detonator"));
        this.entries = new ArrayList<>(entries);
    }

    public void update(List<ChargeListPacket.Entry> e) {
        this.entries = new ArrayList<>(e);
        page = Math.min(page, Math.max(0, (entries.size() - 1) / ROWS));
        rebuildWidgets();
    }

    private boolean confirm(int id) {
        long now = System.currentTimeMillis();
        if (armedId == id && now - armedAt < 4000) {
            armedId = Integer.MIN_VALUE;
            return true;
        }
        armedId = id;
        armedAt = now;
        rebuildWidgets();
        return false;
    }

    @Override
    protected void init() {
        int x0 = width / 2 - 170, y = 46;
        for (int i = page * ROWS; i < Math.min(entries.size(), (page + 1) * ROWS); i++) {
            ChargeListPacket.Entry e = entries.get(i);
            addRenderableWidget(Button.builder(Component.translatable("fuze.aiarsenal." + e.fuze()), b ->
                    ArsenalNetwork.CHANNEL.sendToServer(new DetonatorPacket(DetonatorPacket.FUZE, e.id()))).bounds(x0 + 200, y, 64, 18).build());
            boolean armed = armedId == e.id();
            addRenderableWidget(Button.builder(Component.translatable(armed ? "gui.aiarsenal.confirm" : "gui.aiarsenal.detonate"), b -> {
                if (confirm(e.id())) ArsenalNetwork.CHANNEL.sendToServer(new DetonatorPacket(DetonatorPacket.DETONATE, e.id()));
            }).bounds(x0 + 270, y, 70, 18).build());
            y += 22;
        }
        int by = height - 52;
        boolean allArmed = armedId == -1;
        Button all = Button.builder(Component.translatable(allArmed ? "gui.aiarsenal.confirm_all" : "gui.aiarsenal.detonate_all"), b -> {
            if (confirm(-1)) ArsenalNetwork.CHANNEL.sendToServer(new DetonatorPacket(DetonatorPacket.ALL, 0));
        }).bounds(width / 2 - 170, by, 110, 20).build();
        all.active = !entries.isEmpty();
        addRenderableWidget(all);
        addRenderableWidget(Button.builder(Component.translatable("gui.aiarsenal.refresh"), b ->
                ArsenalNetwork.CHANNEL.sendToServer(new DetonatorPacket(DetonatorPacket.REFRESH, 0))).bounds(width / 2 - 55, by, 70, 20).build());
        Button prev = Button.builder(Component.literal("<"), b -> {
            page--;
            rebuildWidgets();
        }).bounds(width / 2 + 20, by, 30, 20).build();
        prev.active = page > 0;
        addRenderableWidget(prev);
        Button next = Button.builder(Component.literal(">"), b -> {
            page++;
            rebuildWidgets();
        }).bounds(width / 2 + 55, by, 30, 20).build();
        next.active = (page + 1) * ROWS < entries.size();
        addRenderableWidget(next);
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(width / 2 + 100, by, 70, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.fill(width / 2 - 178, 18, width / 2 + 178, height - 26, 0xC0101010);
        g.drawCenteredString(font, title, width / 2, 26, 0xFFFF4040);
        int x0 = width / 2 - 170, y = 46;
        if (entries.isEmpty()) g.drawCenteredString(font, Component.translatable("gui.aiarsenal.no_charges"), width / 2, 80, 0xFFB0B0B0);
        String[] st = {"gui.aiarsenal.st_ground", "gui.aiarsenal.st_falling", "gui.aiarsenal.st_flight", "gui.aiarsenal.st_space"};
        var p = minecraft.player;
        for (int i = page * ROWS; i < Math.min(entries.size(), (page + 1) * ROWS); i++) {
            ChargeListPacket.Entry e = entries.get(i);
            Kind k = Kind.byIndex(e.kind());
            int dist = p == null ? 0 : (int) Math.sqrt(p.distanceToSqr(e.x(), e.y(), e.z()));
            g.drawString(font, "#" + e.id() + " " + Component.translatable(k.translationKey()).getString(), x0, y + 1, 0xFFFFC040);
            g.drawString(font, e.x() + " " + e.y() + " " + e.z() + "  " + dist + " m", x0, y + 10, 0xFFA0A0A0);
            g.drawString(font, Component.translatable(st[Math.max(0, Math.min(3, e.status()))]), x0 + 130, y + 5, 0xFF80C0FF);
            y += 22;
        }
        super.render(g, mx, my, pt);
        g.drawCenteredString(font, Component.translatable("gui.aiarsenal.detonator_hint"), width / 2, height - 18, 0xFF909090);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
