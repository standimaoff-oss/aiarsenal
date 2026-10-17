package ru.aiforge.arsenal.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import ru.aiforge.arsenal.core.Kind;
import ru.aiforge.arsenal.entity.LauncherEntity;
import ru.aiforge.arsenal.item.TabletItem;
import ru.aiforge.arsenal.network.ArsenalNetwork;
import ru.aiforge.arsenal.network.LauncherPacket;

/** Launcher fire control: target X/Z (typed or from the tablet), launch, salvo, erect/stow, unload. */
public class LauncherScreen extends Screen {
    private final LauncherEntity l;
    private EditBox bx, bz;

    public LauncherScreen(LauncherEntity l) {
        super(Component.translatable(l.kind().translationKey()));
        this.l = l;
    }

    @Override
    protected void init() {
        int cx = width / 2, y = height / 2 - 40;
        bx = new EditBox(font, cx - 100, y, 95, 18, Component.literal("X"));
        bz = new EditBox(font, cx + 5, y, 95, 18, Component.literal("Z"));
        BlockPos t = l.target();
        if (t == null && minecraft.player != null) t = TabletItem.findTarget(minecraft.player);
        if (t != null) {
            bx.setValue(String.valueOf(t.getX()));
            bz.setValue(String.valueOf(t.getZ()));
        }
        bx.setFilter(s -> s.matches("-?\\d{0,8}"));
        bz.setFilter(s -> s.matches("-?\\d{0,8}"));
        addRenderableWidget(bx);
        addRenderableWidget(bz);
        y += 24;
        addRenderableWidget(Button.builder(Component.translatable("gui.aiarsenal.from_tablet"), b -> {
            BlockPos tt = minecraft.player == null ? null : TabletItem.findTarget(minecraft.player);
            if (tt != null) {
                bx.setValue(String.valueOf(tt.getX()));
                bz.setValue(String.valueOf(tt.getZ()));
            }
        }).bounds(cx - 100, y, 95, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.aiarsenal.set_target"), b -> send(LauncherEntity.A_TARGET))
                .bounds(cx + 5, y, 95, 20).build());
        y += 24;
        addRenderableWidget(Button.builder(Component.translatable("gui.aiarsenal.launch"), b -> {
            send(LauncherEntity.A_TARGET);
            send(LauncherEntity.A_LAUNCH);
        }).bounds(cx - 100, y, 95, 20).build());
        Button salvo = Button.builder(Component.translatable("gui.aiarsenal.salvo"), b -> {
            send(LauncherEntity.A_TARGET);
            send(LauncherEntity.A_SALVO);
        }).bounds(cx + 5, y, 95, 20).build();
        salvo.active = l.kind().slots > 1;
        addRenderableWidget(salvo);
        y += 24;
        Button erect = Button.builder(Component.translatable("gui.aiarsenal.erect"), b -> send(LauncherEntity.A_ERECT)).bounds(cx - 100, y, 95, 20).build();
        erect.active = l.needsErect();
        addRenderableWidget(erect);
        addRenderableWidget(Button.builder(Component.translatable("gui.aiarsenal.unload"), b -> send(LauncherEntity.A_UNLOAD)).bounds(cx + 5, y, 95, 20).build());
    }

    private void send(int action) {
        int x = parse(bx.getValue()), z = parse(bz.getValue());
        ArsenalNetwork.CHANNEL.sendToServer(new LauncherPacket(l.getId(), action, x, z));
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int cx = width / 2, y = height / 2 - 100;
        g.drawCenteredString(font, title, cx, y, 0xFFFFC040);
        Kind a = l.ammoKind();
        String ammo = a == null ? Component.translatable("gui.aiarsenal.empty").getString()
                : Component.translatable(a.translationKey()).getString() + " x" + l.loadedCount() + "/" + l.kind().slots;
        g.drawCenteredString(font, Component.translatable("gui.aiarsenal.ammo", ammo), cx, y += 14, 0xFFFFFFFF);
        if (l.needsErect())
            g.drawCenteredString(font, Component.translatable("gui.aiarsenal.erect_state", (int) (l.erect() * 100)), cx, y += 12, 0xFFB0B0B0);
        BlockPos t = l.target();
        g.drawCenteredString(font, t == null ? Component.translatable("gui.aiarsenal.no_target")
                : Component.translatable("gui.aiarsenal.target", t.getX(), t.getZ(), (int) Math.sqrt(l.blockPosition().distSqr(t))), cx, y += 12, 0xFFFF6060);
        g.drawString(font, "X", cx - 110, height / 2 - 35, 0xFFFFFFFF);
        g.drawString(font, "Z", cx + 101, height / 2 - 35, 0xFFFFFFFF);
        super.render(g, mx, my, pt);
        g.drawCenteredString(font, Component.translatable("gui.aiarsenal.launcher_hint"), cx, height - 20, 0xFF909090);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
