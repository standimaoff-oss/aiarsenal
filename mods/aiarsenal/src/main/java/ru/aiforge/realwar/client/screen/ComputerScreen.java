/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.gui.GuiGraphics
 *  net.minecraft.client.gui.components.AbstractSliderButton
 *  net.minecraft.client.gui.components.Button
 *  net.minecraft.client.gui.components.EditBox
 *  net.minecraft.client.gui.components.events.GuiEventListener
 *  net.minecraft.client.gui.screens.Screen
 *  net.minecraft.core.BlockPos
 *  net.minecraft.core.Vec3i
 *  net.minecraft.network.chat.Component
 *  net.minecraft.network.chat.FormattedText
 *  net.minecraft.network.chat.MutableComponent
 *  net.minecraft.util.Mth
 *  net.minecraft.world.level.block.entity.BlockEntity
 *  net.minecraft.world.phys.Vec3
 */
package ru.aiforge.realwar.client.screen;

import ru.aiforge.realwar.client.sight.MapScreen;
import ru.aiforge.realwar.missile.ComputerActionPacket;
import ru.aiforge.realwar.missile.LaunchComputerBlockEntity;
import ru.aiforge.realwar.missile.LaunchPadBlockEntity;
import ru.aiforge.realwar.missile.MissileType;
import ru.aiforge.realwar.network.ModNetwork;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

public class ComputerScreen
extends Screen {
    private static final int W = 400;
    private static final int H = 236;
    private static final int ROWS = 7;
    private static final int ROW_H = 22;
    private final BlockPos pos;
    private int x0;
    private int y0;
    private int scroll;
    private EditBox bx;
    private EditBox bz;
    private Button armBtn;
    private Button launchBtn;
    private Button abortBtn;
    private SpreadSlider slider;

    public ComputerScreen(BlockPos pos) {
        super((Component)Component.translatable((String)"gui.aiarsenal.rw.computer.title"));
        this.pos = pos;
    }

    private LaunchComputerBlockEntity be() {
        LaunchComputerBlockEntity c;
        BlockEntity blockEntity;
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && (blockEntity = mc.level.getBlockEntity(this.pos)) instanceof LaunchComputerBlockEntity ? (c = (LaunchComputerBlockEntity)blockEntity) : null;
    }

    private void send(int action, int a, int b) {
        ModNetwork.CHANNEL.sendToServer(new ComputerActionPacket(this.pos, action, a, b));
    }

    protected void init() {
        this.x0 = (this.width - 400) / 2;
        this.y0 = (this.height - 236) / 2;
        int rx = this.x0 + 226;
        this.bx = new EditBox(this.font, rx + 12, this.y0 + 42, 58, 16, (Component)Component.literal((String)"X"));
        this.bz = new EditBox(this.font, rx + 86, this.y0 + 42, 58, 16, (Component)Component.literal((String)"Z"));
        for (EditBox b2 : new EditBox[]{this.bx, this.bz}) {
            b2.setFilter(s -> s.matches("-?\\d{0,8}"));
            b2.setMaxLength(9);
            this.addRenderableWidget(b2);
        }
        LaunchComputerBlockEntity c = this.be();
        if (c != null && c.hasTarget) {
            this.bx.setValue(String.valueOf(c.tx));
            this.bz.setValue(String.valueOf(c.tz));
        }
        this.addRenderableWidget(Button.builder((Component)Component.translatable((String)"gui.aiarsenal.rw.computer.set"), b -> this.setFromBoxes()).bounds(rx + 6, this.y0 + 62, 50, 16).build());
        this.addRenderableWidget(Button.builder((Component)Component.translatable((String)"gui.aiarsenal.rw.computer.map"), b -> this.openMap()).bounds(rx + 58, this.y0 + 62, 50, 16).build());
        this.addRenderableWidget(Button.builder((Component)Component.translatable((String)"gui.aiarsenal.rw.computer.clear"), b -> this.send(6, 0, 0)).bounds(rx + 110, this.y0 + 62, 40, 16).build());
        this.slider = new SpreadSlider(rx + 6, this.y0 + 96, 144, 16, c == null ? 10 : c.spread);
        this.addRenderableWidget(this.slider);
        this.armBtn = (Button)this.addRenderableWidget(Button.builder((Component)Component.empty(), b -> this.send(3, 0, 0)).bounds(rx + 6, this.y0 + 122, 144, 18).build());
        this.launchBtn = (Button)this.addRenderableWidget(Button.builder((Component)Component.translatable((String)"gui.aiarsenal.rw.computer.launch"), b -> this.send(4, 0, 0)).bounds(rx + 6, this.y0 + 146, 144, 22).build());
        this.abortBtn = (Button)this.addRenderableWidget(Button.builder((Component)Component.translatable((String)"gui.aiarsenal.rw.computer.abort"), b -> this.send(5, 0, 0)).bounds(rx + 6, this.y0 + 146, 144, 22).build());
        this.addRenderableWidget(Button.builder((Component)Component.translatable((String)"gui.aiarsenal.rw.computer.siren"), b -> this.send(8, 0, 0)).bounds(rx + 6, this.y0 + 172, 70, 16).build());
        this.addRenderableWidget(Button.builder((Component)Component.translatable((String)"gui.aiarsenal.rw.computer.close"), b -> this.onClose()).bounds(rx + 80, this.y0 + 172, 70, 16).build());
        this.addRenderableWidget(Button.builder((Component)Component.translatable((String)"gui.aiarsenal.rw.computer.all"), b -> this.send(7, 1, 0)).bounds(this.x0 + 8, this.y0 + 20 + 154 + 4, 100, 16).build());
        this.addRenderableWidget(Button.builder((Component)Component.translatable((String)"gui.aiarsenal.rw.computer.none"), b -> this.send(7, 0, 0)).bounds(this.x0 + 112, this.y0 + 20 + 154 + 4, 100, 16).build());
    }

    private void setFromBoxes() {
        try {
            this.send(1, Integer.parseInt(this.bx.getValue()), Integer.parseInt(this.bz.getValue()));
        }
        catch (NumberFormatException numberFormatException) {
            // empty catch block
        }
    }

    private void openMap() {
        LaunchComputerBlockEntity c = this.be();
        if (c == null) {
            return;
        }
        double mn = Double.MAX_VALUE;
        double mx = 0.0;
        double km = 0.5;
        for (LaunchComputerBlockEntity.PadInfo i : c.pads) {
            boolean use = c.selected.contains(i.pos().asLong()) || c.selected.isEmpty() && i.kind() >= 0;
            MissileType t = i.type();
            if (!use || t == null) continue;
            mn = Math.min(mn, t.cls.minRange);
            mx = Math.max(mx, t.cls.maxRange);
            km = t.cls.kmPerBlock;
        }
        if (mx == 0.0) {
            mn = 0.0;
            mx = 2500.0;
        }
        BlockPos cur = c.hasTarget ? new BlockPos(c.tx, 64, c.tz) : null;
        MapScreen ms = new MapScreen(new MapScreen.Params((Component)Component.translatable((String)"gui.aiarsenal.rw.computer.map_title"), Vec3.atCenterOf((Vec3i)this.pos), mn, mx, false, cur, km, p -> this.send(1, p.getX(), p.getZ()), () -> this.send(6, 0, 0)));
        ms.parent = this;
        Minecraft.getInstance().setScreen((Screen)ms);
    }

    public boolean mouseScrolled(double mx, double my, double d) {
        LaunchComputerBlockEntity c = this.be();
        if (c != null && mx < (double)(this.x0 + 220)) {
            this.scroll = Mth.clamp((int)(this.scroll - (int)Math.signum(d)), (int)0, (int)Math.max(0, c.pads.size() - 7));
        }
        return true;
    }

    public boolean mouseClicked(double mx, double my, int btn) {
        int idx;
        LaunchComputerBlockEntity c = this.be();
        if (c != null && btn == 0 && mx >= (double)(this.x0 + 8) && mx < (double)(this.x0 + 214) && my >= (double)(this.y0 + 20) && my < (double)(this.y0 + 20 + 154) && (idx = this.scroll + (int)((my - (double)this.y0 - 20.0) / 22.0)) < c.pads.size()) {
            this.send(0, idx, 0);
            return true;
        }
        return super.mouseClicked(mx, my, btn);
    }

    public boolean isPauseScreen() {
        return false;
    }

    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        this.renderBackground(g);
        LaunchComputerBlockEntity c = this.be();
        g.fill(this.x0 - 2, this.y0 - 2, this.x0 + 400 + 2, this.y0 + 236 + 2, -14996962);
        g.fill(this.x0, this.y0, this.x0 + 400, this.y0 + 236, -301329396);
        g.fill(this.x0 + 4, this.y0 + 16, this.x0 + 218, this.y0 + 20 + 154 + 2, -15785452);
        g.drawString(this.font, this.title, this.x0 + 8, this.y0 + 5, -7536740, false);
        if (c == null) {
            g.drawString(this.font, "\u2026", this.x0 + 10, this.y0 + 30, -1, false);
            super.render(g, mouseX, mouseY, pt);
            return;
        }
        this.scroll = Mth.clamp((int)this.scroll, (int)0, (int)Math.max(0, c.pads.size() - 7));
        List<LaunchComputerBlockEntity.PadInfo> pads = c.pads;
        if (pads.isEmpty()) {
            g.drawWordWrap(this.font, (FormattedText)Component.translatable((String)"gui.aiarsenal.rw.computer.nopads"), this.x0 + 10, this.y0 + 30, 200, -5191504);
        }
        for (int r = 0; r < 7 && this.scroll + r < pads.size(); ++r) {
            boolean hov;
            LaunchComputerBlockEntity.PadInfo i = pads.get(this.scroll + r);
            int ry = this.y0 + 20 + r * 22;
            boolean sel = c.selected.contains(i.pos().asLong());
            boolean bl = hov = mouseX >= this.x0 + 8 && mouseX < this.x0 + 214 && mouseY >= ry && mouseY < ry + 22;
            g.fill(this.x0 + 6, ry, this.x0 + 216, ry + 22 - 2, sel ? -14923222 : (hov ? -15255008 : -15586792));
            g.fill(this.x0 + 10, ry + 5, this.x0 + 22, ry + 17, -12817850);
            if (sel) {
                g.fill(this.x0 + 12, ry + 7, this.x0 + 20, ry + 15, -7536740);
            }
            MissileType t = i.type();
            LaunchPadBlockEntity.State st = LaunchPadBlockEntity.State.values()[Mth.clamp((int)i.state(), (int)0, (int)4)];
            g.drawString(this.font, i.level() + "\u00d7" + i.level(), this.x0 + 28, ry + 2, -12192, false);
            MutableComponent name = t == null ? Component.translatable((String)"gui.aiarsenal.rw.computer.empty") : Component.translatable((String)("item.aiarsenal." + t.itemName()));
            g.drawString(this.font, this.font.plainSubstrByWidth(name.getString(), 150), this.x0 + 52, ry + 2, t == null ? -9404304 : -2035488, false);
            int col = st == LaunchPadBlockEntity.State.READY ? -10420352 : (st == LaunchPadBlockEntity.State.LAUNCH ? -40896 : -4145056);
            String dist = "";
            if (t != null && c.hasTarget) {
                double d = Math.hypot((double)c.tx + 0.5 - ((double)i.pos().getX() + (double)i.level() / 2.0), (double)c.tz + 0.5 - ((double)i.pos().getZ() + (double)i.level() / 2.0));
                boolean ok = d >= t.cls.minRange && d <= t.cls.maxRange;
                dist = String.format("  %.0f %s", d * t.cls.kmPerBlock, ok ? "\u043a\u043c \u2714" : "\u043a\u043c \u2716");
                if (!ok) {
                    col = -36768;
                }
            }
            g.drawString(this.font, Component.translatable((String)("gui.aiarsenal.rw.computer.state." + st.name().toLowerCase())).getString() + dist, this.x0 + 52, ry + 12, col, false);
        }
        int rx = this.x0 + 226;
        g.fill(rx, this.y0 + 16, this.x0 + 400 - 4, this.y0 + 236 - 6, -15785452);
        g.drawString(this.font, (Component)Component.translatable((String)"gui.aiarsenal.rw.computer.target"), rx + 6, this.y0 + 22, -12192, false);
        g.drawString(this.font, "X", rx + 6, this.y0 + 46, -5191504, false);
        g.drawString(this.font, "Z", rx + 78, this.y0 + 46, -5191504, false);
        String tgt = c.hasTarget ? "X " + c.tx + "  Z " + c.tz : Component.translatable((String)"gui.aiarsenal.rw.computer.no_target").getString();
        g.drawString(this.font, tgt, rx + 6, this.y0 + 82, c.hasTarget ? -7536740 : -36768, false);
        this.armBtn.setMessage((Component)Component.translatable((String)(c.armed ? "gui.aiarsenal.rw.computer.armed" : "gui.aiarsenal.rw.computer.disarmed")));
        boolean counting = c.countdown >= 0;
        boolean any = false;
        for (LaunchComputerBlockEntity.PadInfo i : pads) {
            if (!c.selected.contains(i.pos().asLong()) || i.state() != LaunchPadBlockEntity.State.READY.ordinal()) continue;
            any = true;
        }
        this.launchBtn.visible = !counting;
        this.launchBtn.active = c.armed && c.hasTarget && any;
        this.abortBtn.visible = counting;
        boolean bl = this.slider.active = !counting;
        if (counting) {
            String s = String.format("T\u2212 %.1f c", (double)c.countdown / 20.0);
            g.pose().pushPose();
            g.pose().translate((float)(rx + 76), (float)(this.y0 + 196), 0.0f);
            g.pose().scale(2.0f, 2.0f, 1.0f);
            g.drawCenteredString(this.font, s, 0, 0, c.countdown / 10 % 2 == 0 ? -44992 : -16320);
            g.pose().popPose();
        } else if (!c.msg.isEmpty()) {
            g.drawWordWrap(this.font, (FormattedText)Component.translatable((String)c.msg), rx + 6, this.y0 + 194, 144, -12192);
        }
        super.render(g, mouseX, mouseY, pt);
    }

    private class SpreadSlider
    extends AbstractSliderButton {
        SpreadSlider(int x, int y, int w, int h, int v) {
            super(x, y, w, h, (Component)Component.empty(), (double)v / 200.0);
            this.updateMessage();
        }

        protected void updateMessage() {
            this.setMessage((Component)Component.translatable((String)"gui.aiarsenal.rw.computer.spread_label", (Object[])new Object[]{(int)Math.round(this.value * 200.0)}));
        }

        protected void applyValue() {
        }

        public void onRelease(double mx, double my) {
            super.onRelease(mx, my);
            ComputerScreen.this.send(2, (int)Math.round(this.value * 200.0), 0);
        }
    }
}

