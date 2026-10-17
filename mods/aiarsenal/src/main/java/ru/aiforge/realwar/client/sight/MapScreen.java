/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.platform.NativeImage
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.gui.GuiGraphics
 *  net.minecraft.client.gui.components.Button
 *  net.minecraft.client.gui.components.EditBox
 *  net.minecraft.client.gui.components.events.GuiEventListener
 *  net.minecraft.client.gui.screens.Screen
 *  net.minecraft.client.multiplayer.ClientLevel
 *  net.minecraft.client.player.LocalPlayer
 *  net.minecraft.client.renderer.texture.DynamicTexture
 *  net.minecraft.core.BlockPos
 *  net.minecraft.network.chat.Component
 *  net.minecraft.network.chat.FormattedText
 *  net.minecraft.network.chat.MutableComponent
 *  net.minecraft.resources.ResourceLocation
 *  net.minecraft.util.FormattedCharSequence
 *  net.minecraft.util.Mth
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.phys.Vec3
 */
package ru.aiforge.realwar.client.sight;

import com.mojang.blaze3d.platform.NativeImage;
import ru.aiforge.realwar.client.sight.MapCache;
import ru.aiforge.realwar.client.sight.RadarClient;
import ru.aiforge.realwar.network.ModNetwork;
import ru.aiforge.realwar.radar.RadarFirePacket;
import ru.aiforge.realwar.radar.RadarNet;
import ru.aiforge.realwar.radar.RadarQueryPacket;
import ru.aiforge.realwar.vehicle.SamLauncherEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public class MapScreen
extends Screen {
    public static final double WORLD_SPAN = 5000.0;
    private final Params p;
    public Screen parent;
    private double cx;
    private double cz;
    private double scale = 1.0;
    private int mx;
    private int my;
    private int mw;
    private int mh;
    private DynamicTexture tex;
    private ResourceLocation texId;
    private boolean dirty = true;
    private EditBox bx;
    private EditBox bz;
    private EditBox by;
    private BlockPos pending;
    private boolean dragging;
    private double dragX;
    private double dragZ;
    private int selected = -1;
    private int listScroll;
    private int listX;
    private int listY;
    private int listW;
    private int listH;
    private Button fireBtn;

    private boolean radar() {
        return this.p.radar() != null;
    }

    public MapScreen(Params p) {
        super(p.title());
        this.p = p;
        this.pending = p.current();
    }

    protected void init() {
        this.mx = 10;
        this.my = 26;
        this.mw = this.width - (this.radar() ? 224 : 190);
        this.mh = this.height - 40;
        if (this.tex != null) {
            this.tex.close();
        }
        this.tex = new DynamicTexture(this.mw, this.mh, false);
        this.texId = Minecraft.getInstance().getTextureManager().register("aiarsenal_rw_map", this.tex);
        if (this.cx == 0.0 && this.cz == 0.0) {
            Vec3 c = this.p.current() != null ? new Vec3((double)this.p.current().getX(), 0.0, (double)this.p.current().getZ()) : this.p.origin();
            this.cx = this.p.maxRange > 0.0 && this.p.current() == null ? this.p.origin().x : c.x;
            this.cz = this.p.maxRange > 0.0 && this.p.current() == null ? this.p.origin().z : c.z;
            double want = this.p.maxRange > 0.0 ? this.p.maxRange * 2.3 / (double)Math.min(this.mw, this.mh) : 1.0;
            this.scale = Math.max(0.25, Math.min(5000.0 / (double)Math.min(this.mw, this.mh), want));
        }
        OverviewClient.ensure(this.p.origin().x, this.p.origin().z);
        int rx = this.mx + this.mw + 10;
        int rw = this.width - rx - 8;
        if (this.radar()) {
            this.listX = rx;
            this.listY = 40;
            this.listW = rw;
            this.listH = this.height - 40 - 62;
            RadarClient.reset();
            this.query();
            if (this.p.radar().fire()) {
                this.fireBtn = (Button)this.addRenderableWidget(Button.builder((Component)Component.translatable((String)(this.pick() ? "gui.aiarsenal.rw.radar.launch" : "gui.aiarsenal.rw.radar.fire")), b -> this.fire()).bounds(rx, this.height - 54, rw, 20).build());
                this.fireBtn.active = false;
            }
            if (this.pick()) {
                this.listH -= 48;
                this.addRenderableWidget(Button.builder((Component)Component.translatable((String)"gui.aiarsenal.rw.map.clear"), b -> {
                    this.p.onClear().run();
                    this.pending = null;
                    this.selected = -1;
                }).bounds(rx, this.height - 78, rw / 2 - 2, 20).build());
                this.addRenderableWidget(Button.builder((Component)Component.translatable((String)"gui.aiarsenal.rw.map.center"), b -> {
                    this.cx = this.p.origin().x;
                    this.cz = this.p.origin().z;
                    this.dirty = true;
                }).bounds(rx + rw / 2 + 2, this.height - 78, rw - rw / 2 - 2, 20).build());
            }
            if (!this.p.radar().fire()) {
                this.addRenderableWidget(Button.builder((Component)Component.translatable((String)"gui.aiarsenal.rw.map.center"), b -> {
                    this.cx = this.p.origin().x;
                    this.cz = this.p.origin().z;
                    this.dirty = true;
                }).bounds(rx, this.height - 54, rw, 20).build());
            }
            this.addRenderableWidget(Button.builder((Component)Component.translatable((String)"gui.aiarsenal.rw.map.close"), b -> this.onClose()).bounds(rx, this.height - 30, rw, 20).build());
            return;
        }
        this.bx = new EditBox(this.font, rx, 50, rw, 16, (Component)Component.literal((String)"X"));
        this.bz = new EditBox(this.font, rx, 84, rw, 16, (Component)Component.literal((String)"Z"));
        this.by = new EditBox(this.font, rx, 118, rw, 16, (Component)Component.literal((String)"Y"));
        for (EditBox b2 : new EditBox[]{this.bx, this.bz, this.by}) {
            b2.setFilter(s -> s.matches("-?\\d{0,9}"));
            b2.setMaxLength(10);
            this.addRenderableWidget(b2);
        }
        this.by.visible = this.p.askAlt();
        this.by.active = this.p.askAlt();
        this.syncBoxes();
        int by0 = this.p.askAlt() ? 144 : 118;
        this.addRenderableWidget(Button.builder((Component)Component.translatable((String)"gui.aiarsenal.rw.map.send"), b -> this.send()).bounds(rx, by0, rw, 20).build());
        this.addRenderableWidget(Button.builder((Component)Component.translatable((String)"gui.aiarsenal.rw.map.clear"), b -> {
            this.p.onClear().run();
            this.pending = null;
            this.syncBoxes();
        }).bounds(rx, by0 + 24, rw, 20).build());
        this.addRenderableWidget(Button.builder((Component)Component.translatable((String)"gui.aiarsenal.rw.map.center"), b -> {
            this.cx = this.p.origin().x;
            this.cz = this.p.origin().z;
            this.dirty = true;
        }).bounds(rx, by0 + 48, rw, 20).build());
        this.addRenderableWidget(Button.builder((Component)Component.translatable((String)"gui.aiarsenal.rw.map.close"), b -> this.onClose()).bounds(rx, this.height - 30, rw, 20).build());
    }

    private void query() {
        ModNetwork.CHANNEL.sendToServer(new RadarQueryPacket(this.p.radar().vehId()));
    }

    private void fire() {
        if (this.pick() && this.selected < 0) {
            if (this.pending != null) {
                ModNetwork.CHANNEL.sendToServer(new RadarFirePacket(this.p.radar().vehId(), -1));
            }
            return;
        }
        if (this.selected >= 0) {
            ModNetwork.CHANNEL.sendToServer(new RadarFirePacket(this.p.radar().vehId(), this.selected));
        }
    }

    private RadarNet.Contact byId(int id) {
        for (RadarNet.Contact c : RadarClient.contacts) {
            if (c.id() != id) continue;
            return c;
        }
        return null;
    }

    private double[] cpos(RadarNet.Contact c, float pt) {
        ClientLevel lv = Minecraft.getInstance().level;
        double age = lv == null ? 0.0 : (double)Math.min(40.0f, (float)(lv.getGameTime() - RadarClient.stamp) + pt);
        return new double[]{c.x() + (double)c.vx() * age, c.y() + (double)c.vy() * age, c.z() + (double)c.vz() * age};
    }

    private static int typeColor(byte t) {
        return switch (t) {
            case 0 -> -11718;
            case 1 -> -46534;
            case 2 -> -12920577;
            default -> -5197648;
        };
    }

    private void clampCenter() {
        double h = 2500.0;
        this.cx = Mth.clamp((double)this.cx, (double)(this.p.origin().x - h), (double)(this.p.origin().x + h));
        this.cz = Mth.clamp((double)this.cz, (double)(this.p.origin().z - h), (double)(this.p.origin().z + h));
    }

    public void onClose() {
        Minecraft.getInstance().setScreen(this.parent);
    }

    public void removed() {
        if (this.tex != null) {
            this.tex.close();
            this.tex = null;
        }
    }

    private void syncBoxes() {
        this.bx.setValue(this.pending == null ? "" : String.valueOf(this.pending.getX()));
        this.bz.setValue(this.pending == null ? "" : String.valueOf(this.pending.getZ()));
        this.by.setValue(this.pending == null ? String.valueOf(Mth.floor((double)this.p.origin().y) + 40) : String.valueOf(this.pending.getY()));
    }

    private void send() {
        try {
            int h;
            int x = Integer.parseInt(this.bx.getValue());
            int z = Integer.parseInt(this.bz.getValue());
            int y = this.p.askAlt() ? (this.by.getValue().isEmpty() ? (int)this.p.origin().y + 40 : Integer.parseInt(this.by.getValue())) : ((h = MapCache.heightAt(Minecraft.getInstance().level, x, z)) == Integer.MIN_VALUE ? (int)this.p.origin().y : h);
            this.pending = new BlockPos(x, y, z);
            this.p.onSet().accept(this.pending);
        }
        catch (NumberFormatException numberFormatException) {
            // empty catch block
        }
    }

    private double wx(double px) {
        return this.cx + (px - (double)this.mx - (double)this.mw / 2.0) * this.scale;
    }

    private double wz(double py) {
        return this.cz + (py - (double)this.my - (double)this.mh / 2.0) * this.scale;
    }

    private int sx(double x) {
        return (int)Math.round((double)this.mx + (double)this.mw / 2.0 + (x - this.cx) / this.scale);
    }

    private int sz(double z) {
        return (int)Math.round((double)this.my + (double)this.mh / 2.0 + (z - this.cz) / this.scale);
    }

    private void bake() {
        ClientLevel level = Minecraft.getInstance().level;
        NativeImage im = this.tex.getPixels();
        if (im == null || level == null) {
            return;
        }
        MapCache.Tile last = null;
        int lcx = Integer.MIN_VALUE;
        int lcz = Integer.MIN_VALUE;
        for (int py = 0; py < this.mh; ++py) {
            int z = Mth.floor((double)(this.cz + ((double)py - (double)this.mh / 2.0) * this.scale));
            for (int px = 0; px < this.mw; ++px) {
                int x = Mth.floor((double)(this.cx + ((double)px - (double)this.mw / 2.0) * this.scale));
                int tx = x >> 4;
                int tz = z >> 4;
                if (tx != lcx || tz != lcz) {
                    last = MapCache.tile(level, tx, tz);
                    lcx = tx;
                    lcz = tz;
                }
                int argb;
                if (last != null) {
                    argb = last.rgb[(z & 0xF) * 16 + (x & 0xF)];
                } else {
                    // strategic overview (server-sampled terrain) for chunks this client never loaded
                    int ov = OverviewClient.rgbAt(x, z);
                    argb = ov != 0 ? ov : ((((x >> 4) + (z >> 4)) & 1) == 0 ? -15262697 : -14999269);
                }
                int abgr = argb & 0xFF00FF00 | argb >> 16 & 0xFF | (argb & 0xFF) << 16;
                im.setPixelRGBA(px, py, abgr);
            }
        }
        this.tex.upload();
        this.dirty = false;
    }

    public boolean isPauseScreen() {
        return false;
    }

    private int seenTiles = -1;

    public void tick() {
        if (Minecraft.getInstance().level.getGameTime() % 40L == 0L) {
            this.dirty = true;
        }
        if (OverviewClient.received != this.seenTiles && Minecraft.getInstance().level.getGameTime() % 5L == 0L) {
            this.seenTiles = OverviewClient.received;
            this.dirty = true;
        }
        if (this.radar()) {
            if (Minecraft.getInstance().level.getGameTime() % 10L == 0L) {
                this.query();
            }
            if (this.fireBtn != null) {
                RadarNet.Contact c = this.byId(this.selected);
                this.fireBtn.active = c != null && RadarClient.linked || this.pick() && this.selected < 0 && this.pending != null;
            }
        }
    }

    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        int iy;
        g.fill(0, 0, this.width, this.height, -16052469);
        if (this.dirty) {
            this.bake();
        }
        g.drawString(this.font, this.title, this.mx, 8, -1511208, false);
        g.blit(this.texId, this.mx, this.my, 0.0f, 0.0f, this.mw, this.mh, this.mw, this.mh);
        g.enableScissor(this.mx, this.my, this.mx + this.mw, this.my + this.mh);
        this.grid(g);
        this.renderVillages(g);
        if (!this.radar()) {
            this.ring(g, this.p.maxRange(), -20480);
            if (this.p.minRange() > 1.0) {
                this.ring(g, this.p.minRange(), -42422);
            }
        }
        LocalPlayer pl = Minecraft.getInstance().player;
        this.marker(g, pl.getX(), pl.getZ(), -8586148, 3);
        int ox = this.sx(this.p.origin().x);
        int oz = this.sz(this.p.origin().z);
        g.fill(ox - 4, oz - 4, ox + 5, oz + 5, -16777216);
        g.fill(ox - 3, oz - 3, ox + 4, oz + 4, -1);
        if (this.pending != null) {
            int tx = this.sx((double)this.pending.getX() + 0.5);
            int tz = this.sz((double)this.pending.getZ() + 0.5);
            this.line(g, ox, oz, tx, tz, -1057011142);
            g.fill(tx - 9, tz, tx + 10, tz + 1, -46534);
            g.fill(tx, tz - 9, tx + 1, tz + 10, -46534);
            g.fill(tx - 4, tz - 4, tx + 5, tz - 3, -46534);
            g.fill(tx - 4, tz + 4, tx + 5, tz + 5, -46534);
            g.fill(tx - 4, tz - 4, tx - 3, tz + 5, -46534);
            g.fill(tx + 4, tz - 4, tx + 5, tz + 5, -46534);
        }
        if (this.radar()) {
            this.renderContacts(g, mouseX, mouseY, pt, ox, oz);
        }
        this.renderLegend(g);
        g.disableScissor();
        g.renderOutline(this.mx - 1, this.my - 1, this.mw + 2, this.mh + 2, -10852784);
        if (mouseX >= this.mx && mouseX < this.mx + this.mw && mouseY >= this.my && mouseY < this.my + this.mh) {
            double x = this.wx(mouseX);
            double z = this.wz(mouseY);
            double d = Math.hypot(x - this.p.origin().x, z - this.p.origin().z);
            String s = (int)x + " / " + (int)z + "   " + String.format("%.1f", d * this.p.kmPerBlock()) + " " + Component.translatable((String)"hud.aiarsenal.rw.km").getString();
            g.drawString(this.font, s, this.mx + 4, this.my + this.mh - 11, -1, true);
        }
        if (this.radar()) {
            this.renderList(g, mouseX, mouseY, pt);
            super.render(g, mouseX, mouseY, pt);
            return;
        }
        int rx = this.mx + this.mw + 10;
        g.drawString(this.font, "X", rx, 40, -4667224, false);
        g.drawString(this.font, "Z", rx, 74, -4667224, false);
        if (this.p.askAlt()) {
            g.drawString(this.font, (Component)Component.translatable((String)"gui.aiarsenal.rw.map.alt"), rx, 108, -4667224, false);
        }
        int n = iy = this.p.askAlt() ? 216 : 190;
        if (this.pending != null) {
            double d = Math.hypot((double)this.pending.getX() + 0.5 - this.p.origin().x, (double)this.pending.getZ() + 0.5 - this.p.origin().z);
            double brg = Math.toDegrees(Math.atan2((double)this.pending.getX() + 0.5 - this.p.origin().x, -((double)this.pending.getZ() + 0.5 - this.p.origin().z)));
            if (brg < 0.0) {
                brg += 360.0;
            }
            g.drawString(this.font, Component.translatable((String)"hud.aiarsenal.rw.range").getString() + ": " + String.format("%.2f", d * this.p.kmPerBlock()) + " " + Component.translatable((String)"hud.aiarsenal.rw.km").getString(), rx, iy, -1511208, false);
            g.drawString(this.font, Component.translatable((String)"hud.aiarsenal.rw.az").getString() + ": " + String.format("%05.1f\u00b0", brg), rx, iy + 11, -1511208, false);
            boolean ok = d <= this.p.maxRange() && d >= this.p.minRange();
            g.drawString(this.font, (Component)Component.translatable((String)(ok ? "gui.aiarsenal.rw.map.inrange" : "gui.aiarsenal.rw.map.outrange")), rx, iy + 22, ok ? -8586148 : -46534, false);
        }
        int hy = iy + 44;
        for (FormattedCharSequence line : this.font.split((FormattedText)Component.translatable((String)"gui.aiarsenal.rw.map.help"), this.width - rx - 6)) {
            g.drawString(this.font, line, rx, hy, -7694720, false);
            hy += 10;
        }
        super.render(g, mouseX, mouseY, pt);
    }

    private void renderContacts(GuiGraphics g, int mouseX, int mouseY, float pt, int ox, int oz) {
        for (float[] s : RadarClient.sources) {
            int sx0 = this.sx(s[0]);
            int sz0 = this.sz(s[1]);
            this.ringAt(g, s[0], s[1], s[2], 1613807680);
            g.fill(sx0 - 2, sz0 - 2, sx0 + 3, sz0 + 3, -13574064);
        }
        for (RadarNet.Contact c : RadarClient.contacts) {
            double[] q = this.cpos(c, pt);
            int x = this.sx(q[0]);
            int z = this.sz(q[2]);
            int col = MapScreen.typeColor(c.type());
            boolean sel = c.id() == this.selected;
            int x2 = this.sx(q[0] + (double)(c.vx() * 100.0f));
            int z2 = this.sz(q[2] + (double)(c.vz() * 100.0f));
            this.line(g, x, z, x2, z2, col & 0xFFFFFF | 0xA0000000);
            int r = c.type() == 1 ? 2 : 3;
            g.fill(x - r - 1, z - r - 1, x + r + 2, z + r + 2, -16777216);
            g.fill(x - r, z - r, x + r + 1, z + r + 1, col);
            if (c.type() == 0) {
                g.fill(x - 1, z - r - 3, x + 2, z - r - 1, col);
            }
            if (!sel) continue;
            this.line(g, ox, oz, x, z, -1325400065);
            g.renderOutline(x - 8, z - 8, 17, 17, -1);
            String s = this.label(c, q);
            g.drawString(this.font, s, x + 11, z - 4, -1, true);
        }
        if (!RadarClient.linked) {
            MutableComponent t = Component.translatable((String)(this.pick() ? "gui.aiarsenal.rw.radar.nolink_yars" : (this.p.radar().fire() ? "gui.aiarsenal.rw.radar.nolink" : "gui.aiarsenal.rw.radar.off")));
            int tw = this.font.width((FormattedText)t);
            g.fill(this.mx + this.mw / 2 - tw / 2 - 8, this.my + 20, this.mx + this.mw / 2 + tw / 2 + 8, this.my + 44, -802158584);
            g.drawCenteredString(this.font, (Component)t, this.mx + this.mw / 2, this.my + 28, -38310);
        }
    }

    private void renderVillages(GuiGraphics g) {
        boolean names = this.scale < 3.0;
        for (int[] v : OverviewClient.villages) {
            int x = this.sx(v[0]);
            int z = this.sz(v[1]);
            if (x < this.mx - 10 || x > this.mx + this.mw + 10 || z < this.my - 10 || z > this.my + this.mh + 10) continue;
            // little house: roof + walls
            g.fill(x - 3, z - 1, x + 4, z + 4, -16777216);
            g.fill(x - 2, z, x + 3, z + 3, -1061);
            for (int k = 0; k < 4; ++k) {
                g.fill(x - k, z - 4 + k, x + k + 1, z - 3 + k, -4508125);
            }
            if (names) {
                g.drawString(this.font, Component.translatable("gui.aiarsenal.rw.map.village").getString(), x + 6, z - 2, -1061, true);
            }
        }
    }

    private void renderLegend(GuiGraphics g) {
        int x = this.mx + 4;
        int y = this.my + 4;
        int tiles = OverviewClient.tileCount();
        String st = Component.translatable("gui.aiarsenal.rw.map.overview", tiles, 121, OverviewClient.villages.size()).getString();
        int w = Math.max(this.font.width(st) + 8, 132);
        g.fill(x, y, x + w, y + 46, -1341123568);
        g.drawString(this.font, st, x + 4, y + 3, tiles >= 100 ? -8586148 : -20480, false);
        int[] cols = {-11893016, -11634134, -7566200, -1061, -1};
        String[] keys = {"water", "forest", "mountain", "village", "snow"};
        for (int i = 0; i < cols.length; ++i) {
            int lx = x + 4 + (i % 3) * 44;
            int ly = y + 15 + (i / 3) * 14;
            g.fill(lx, ly + 1, lx + 7, ly + 8, cols[i]);
            g.drawString(this.font, Component.translatable("gui.aiarsenal.rw.map.legend." + keys[i]).getString(), lx + 9, ly, -3355444, false);
        }
    }

    private String label(RadarNet.Contact c, double[] q) {
        double d = Math.hypot(q[0] - this.p.origin().x, q[2] - this.p.origin().z);
        double v = Math.sqrt(c.vx() * c.vx() + c.vy() * c.vy() + c.vz() * c.vz()) * 20.0;
        return Component.translatable((String)("gui.aiarsenal.rw.radar.t" + c.type())).getString() + " #" + c.id() + "  " + (int)d + " \u043c  Y " + (int)q[1] + "  " + (int)v + " \u043c/\u0441";
    }

    private void renderList(GuiGraphics g, int mouseX, int mouseY, float pt) {
        Entity v;
        int rx = this.listX;
        ArrayList<RadarNet.Contact> cs = new ArrayList<RadarNet.Contact>(RadarClient.contacts);
        g.drawString(this.font, (Component)Component.translatable((String)"gui.aiarsenal.rw.radar.targets", (Object[])new Object[]{cs.size()}), rx, 28, -1511208, false);
        g.fill(rx - 2, this.listY - 2, rx + this.listW + 2, this.listY + this.listH + 2, -15459820);
        g.renderOutline(rx - 2, this.listY - 2, this.listW + 4, this.listH + 4, -10852784);
        int rows = this.listH / 22;
        this.listScroll = Mth.clamp((int)this.listScroll, (int)0, (int)Math.max(0, cs.size() - rows));
        g.enableScissor(rx, this.listY, rx + this.listW, this.listY + this.listH);
        for (int i = 0; i < rows && i + this.listScroll < cs.size(); ++i) {
            boolean hov;
            RadarNet.Contact c = cs.get(i + this.listScroll);
            int y = this.listY + i * 22;
            boolean sel = c.id() == this.selected;
            boolean bl = hov = mouseX >= rx && mouseX < rx + this.listW && mouseY >= y && mouseY < y + 22;
            if (sel) {
                g.fill(rx, y, rx + this.listW, y + 21, -12957142);
            } else if (hov) {
                g.fill(rx, y, rx + this.listW, y + 21, -14011350);
            }
            int col = MapScreen.typeColor(c.type());
            g.fill(rx + 3, y + 7, rx + 10, y + 14, col);
            double[] q = this.cpos(c, pt);
            double d = Math.hypot(q[0] - this.p.origin().x, q[2] - this.p.origin().z);
            g.drawString(this.font, Component.translatable((String)("gui.aiarsenal.rw.radar.t" + c.type())).getString() + " #" + c.id(), rx + 15, y + 2, -1511208, false);
            g.drawString(this.font, (int)d + " \u043c \u00b7 Y " + (int)q[1], rx + 15, y + 12, -6641520, false);
        }
        g.disableScissor();
        if (cs.isEmpty()) {
            g.drawString(this.font, (Component)Component.translatable((String)"gui.aiarsenal.rw.radar.clear"), rx + 4, this.listY + 6, -7694720, false);
        }
        Entity entity = v = Minecraft.getInstance().player == null ? null : Minecraft.getInstance().player.getVehicle();
        if (v instanceof SamLauncherEntity) {
            SamLauncherEntity s = (SamLauncherEntity)v;
            if (this.p.radar().fire()) {
                Object st;
                int col = -6641520;
                if (s.lockId() < 0) {
                    st = Component.translatable((String)"gui.aiarsenal.rw.radar.st_idle").getString();
                } else if (!s.locked()) {
                    st = Component.translatable((String)"gui.aiarsenal.rw.radar.st_lock").getString() + " " + s.lockProgress() * 100 / 40 + "%";
                    col = -20480;
                } else {
                    st = Component.translatable((String)"gui.aiarsenal.rw.radar.st_ready").getString();
                    col = -8586148;
                }
                g.drawString(this.font, (String)st, rx, this.height - 66, col, false);
            }
        }
        if (v instanceof ru.aiforge.realwar.vehicle.YarsVehicle yv) {
            int stg = yv.stage();
            int col = stg == 5 ? -8586148 : (stg == 0 || stg == 1 ? -46534 : -20480);
            String st = Component.translatable("gui.aiarsenal.rw.yars.st" + stg).getString();
            if (stg == 4) st = st + " " + (int)(yv.erectFrac() * 100.0f) + "%";
            if (yv.armed()) st = st + " \u00b7 " + Component.translatable("gui.aiarsenal.rw.yars.armed").getString();
            g.drawString(this.font, st, rx, this.height - 90, col, false);
            if (this.pending != null) {
                double d = Math.hypot((double)this.pending.getX() + 0.5 - this.p.origin().x, (double)this.pending.getZ() + 0.5 - this.p.origin().z);
                g.drawString(this.font, this.pending.getX() + " / " + this.pending.getZ() + "  " + String.format("%.2f", d * this.p.kmPerBlock()) + " " + Component.translatable("hud.aiarsenal.rw.km").getString(), rx, this.height - 101, -1511208, false);
            }
        }
        List help = this.font.split((FormattedText)Component.translatable((String)(this.pick() ? "gui.aiarsenal.rw.radar.help_yars" : "gui.aiarsenal.rw.radar.help")), this.mw - 10);
        int hy = this.my + this.mh - 4 - help.size() * 10;
        g.fill(this.mx + 1, hy - 3, this.mx + this.mw - 1, this.my + this.mh - 1, -1341123568);
        for (FormattedCharSequence line : (Iterable<FormattedCharSequence>)(Iterable<?>)(help)) {
            g.drawString(this.font, line, this.mx + 5, hy, -5720424, false);
            hy += 10;
        }
    }

    private void ringAt(GuiGraphics g, double x0, double z0, double rBlocks, int col) {
        int steps = (int)Mth.clamp((double)(rBlocks / this.scale * 2.0), (double)64.0, (double)1600.0);
        for (int i = 0; i < steps; ++i) {
            double a = (double)i * Math.PI * 2.0 / (double)steps;
            int x = this.sx(x0 + Math.cos(a) * rBlocks);
            int z = this.sz(z0 + Math.sin(a) * rBlocks);
            if (x < this.mx || x >= this.mx + this.mw || z < this.my || z >= this.my + this.mh) continue;
            g.fill(x, z, x + 1, z + 1, col);
        }
    }

    private void grid(GuiGraphics g) {
        double z0;
        double x0;
        double step = 10.0;
        while (step / this.scale < 48.0) {
            step *= (double)(step * 5.0 / this.scale < 48.0 ? 5 : 2);
        }
        if (step / this.scale > 160.0) {
            step /= 2.0;
        }
        for (double x = x0 = Math.floor(this.wx(this.mx) / step) * step; x < this.wx(this.mx + this.mw); x += step) {
            int s = this.sx(x);
            g.fill(s, this.my, s + 1, this.my + this.mh, 0x30FFFFFF);
            g.drawString(this.font, String.valueOf((long)x), s + 2, this.my + 2, -1862270977, false);
        }
        for (double z = z0 = Math.floor(this.wz(this.my) / step) * step; z < this.wz(this.my + this.mh); z += step) {
            int s = this.sz(z);
            g.fill(this.mx, s, this.mx + this.mw, s + 1, 0x30FFFFFF);
            g.drawString(this.font, String.valueOf((long)z), this.mx + 2, s + 2, -1862270977, false);
        }
    }

    private void ring(GuiGraphics g, double rBlocks, int col) {
        if (rBlocks <= 0.0) {
            return;
        }
        int steps = (int)Mth.clamp((double)(rBlocks / this.scale * 2.0), (double)64.0, (double)1600.0);
        for (int i = 0; i < steps; ++i) {
            double a = (double)i * Math.PI * 2.0 / (double)steps;
            int x = this.sx(this.p.origin().x + Math.cos(a) * rBlocks);
            int z = this.sz(this.p.origin().z + Math.sin(a) * rBlocks);
            if (x < this.mx || x >= this.mx + this.mw || z < this.my || z >= this.my + this.mh) continue;
            g.fill(x, z, x + 1, z + 1, col);
        }
    }

    private void marker(GuiGraphics g, double wx, double wz, int col, int r) {
        int x = this.sx(wx);
        int z = this.sz(wz);
        g.fill(x - r, z - r, x + r + 1, z + r + 1, col);
    }

    private void line(GuiGraphics g, int x0, int y0, int x1, int y1, int col) {
        int n = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        if (n > 4000) {
            return;
        }
        for (int i = 0; i < n; i += 2) {
            int x = x0 + (x1 - x0) * i / n;
            int y = y0 + (y1 - y0) * i / n;
            g.fill(x, y, x + 1, y + 1, col);
        }
    }

    private boolean inMap(double x, double y) {
        return x >= (double)this.mx && x < (double)(this.mx + this.mw) && y >= (double)this.my && y < (double)(this.my + this.mh);
    }

    public boolean mouseClicked(double x, double y, int button) {
        if (this.radar()) {
            int i;
            if (button == 0 && x >= (double)this.listX && x < (double)(this.listX + this.listW) && y >= (double)this.listY && y < (double)(this.listY + this.listH) && (i = (int)((y - (double)this.listY) / 22.0) + this.listScroll) >= 0 && i < RadarClient.contacts.size()) {
                this.selected = RadarClient.contacts.get(i).id();
                RadarNet.Contact c = RadarClient.contacts.get(i);
                if (button == 0) {
                    this.cx = c.x();
                    this.cz = c.z();
                    this.dirty = true;
                    this.clampCenter();
                }
                return true;
            }
            if (button == 0 && this.inMap(x, y)) {
                float pt = Minecraft.getInstance().getFrameTime();
                double best = 144.0;
                int bid = -1;
                for (RadarNet.Contact c : RadarClient.contacts) {
                    double dz;
                    double[] q = this.cpos(c, pt);
                    double dx = (double)this.sx(q[0]) - x;
                    double d = dx * dx + (dz = (double)this.sz(q[2]) - y) * dz;
                    if (!(d < best)) continue;
                    best = d;
                    bid = c.id();
                }
                this.selected = bid;
                if (bid < 0 && this.pick()) {
                    int bxp = Mth.floor((double)this.wx(x));
                    int bzp = Mth.floor((double)this.wz(y));
                    this.pending = new BlockPos(bxp, MapScreen.groundY(bxp, bzp, (int)this.p.origin().y), bzp);
                    this.p.onSet().accept(this.pending);
                } else if (bid >= 0 && this.pick()) {
                    this.pending = null;
                }
                return true;
            }
            if (this.inMap(x, y)) {
                this.dragging = true;
                this.dragX = x;
                this.dragZ = y;
                return true;
            }
            return super.mouseClicked(x, y, button);
        }
        if (this.inMap(x, y)) {
            if (button == 0) {
                int bxp = Mth.floor((double)this.wx(x));
                int bzp = Mth.floor((double)this.wz(y));
                int yy = this.p.askAlt() ? (this.by.getValue().matches("-?\\d+") ? Integer.parseInt(this.by.getValue()) : (int)this.p.origin().y + 40) : MapScreen.groundY(bxp, bzp, (int)this.p.origin().y);
                this.pending = new BlockPos(bxp, yy, bzp);
                this.syncBoxes();
                return true;
            }
            this.dragging = true;
            this.dragX = x;
            this.dragZ = y;
            return true;
        }
        return super.mouseClicked(x, y, button);
    }

    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (this.dragging && button != 0) {
            this.cx -= dx * this.scale;
            this.cz -= dy * this.scale;
            this.clampCenter();
            this.dirty = true;
            return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }

    public boolean mouseReleased(double x, double y, int button) {
        this.dragging = false;
        return super.mouseReleased(x, y, button);
    }

    public boolean mouseScrolled(double x, double y, double d) {
        if (this.radar() && x >= (double)this.listX) {
            this.listScroll -= (int)Math.signum(d);
            return true;
        }
        if (this.inMap(x, y)) {
            double before = this.wx(x);
            double beforeZ = this.wz(y);
            this.scale = Mth.clamp((double)(this.scale * (d > 0.0 ? 0.8 : 1.25)), (double)0.25, (double)Math.max(1.0, 5000.0 / (double)Math.min(this.mw, this.mh)));
            this.cx += before - this.wx(x);
            this.cz += beforeZ - this.wz(y);
            this.dirty = true;
            return true;
        }
        return super.mouseScrolled(x, y, d);
    }

    public boolean keyPressed(int key, int sc, int mod) {
        if (this.radar() && (key == 257 || key == 335 || key == 32)) {
            if (this.fireBtn != null && this.fireBtn.active) {
                this.fire();
            }
            return true;
        }
        if (key == 257 || key == 335) {
            this.send();
            return true;
        }
        if (key == 77 && !(this.getFocused() instanceof EditBox) || this.radar() && key == 82) {
            this.onClose();
            return true;
        }
        return super.keyPressed(key, sc, mod);
    }

    public record Params(Component title, Vec3 origin, double minRange, double maxRange, boolean askAlt, BlockPos current, double kmPerBlock, Consumer<BlockPos> onSet, Runnable onClear, Radar radar) {
        public Params(Component title, Vec3 origin, double minRange, double maxRange, boolean askAlt, BlockPos current, double kmPerBlock, Consumer<BlockPos> onSet, Runnable onClear) {
            this(title, origin, minRange, maxRange, askAlt, current, kmPerBlock, onSet, onClear, null);
        }
    }

    /** pick = Yars mode: radar contacts AND free map-point targeting, the fire button launches. */
    public record Radar(int vehId, boolean fire, boolean pick) {
        public Radar(int vehId, boolean fire) {
            this(vehId, fire, false);
        }
    }

    private boolean pick() {
        return this.p.radar() != null && this.p.radar().pick();
    }

    private static int groundY(int x, int z, int fallback) {
        int h = MapCache.heightAt(Minecraft.getInstance().level, x, z);
        if (h == Integer.MIN_VALUE) h = OverviewClient.heightAt(x, z);
        return h == Integer.MIN_VALUE ? fallback : h;
    }
}

