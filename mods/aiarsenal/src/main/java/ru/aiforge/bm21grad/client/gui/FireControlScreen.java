package ru.aiforge.bm21grad.client.gui;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.Vec3;
import ru.aiforge.bm21grad.Bm21Grad;
import ru.aiforge.bm21grad.ammo.RocketType;
import ru.aiforge.bm21grad.ballistics.Ballistics;
import ru.aiforge.bm21grad.client.ClientEvents;
import ru.aiforge.bm21grad.entity.Bm21Entity;
import ru.aiforge.bm21grad.network.ModNetwork;
import ru.aiforge.bm21grad.network.VehicleCommandPacket;

/**
 * Fire-control computer: terrain map from client chunks (north up), click to designate a target or
 * type coordinates, live ballistic solution (azimuth in mils, elevation, time of flight, apex),
 * reachable-zone rings, ammo / mode selection, reload, combat/travel position and fire commands.
 */
public class FireControlScreen extends Screen {
    private static final int TEX = 256;
    private static final int[] ZOOMS = {1, 2, 4, 8};
    private static final int[] MODES = {1, 2, 4, 10, 20, 40};
    private static final ResourceLocation MAP_ID = ResourceLocation.fromNamespaceAndPath(Bm21Grad.MODID, "fcs_map");

    private final Bm21Entity v;
    private DynamicTexture tex;
    private int zoom = 3; // 2048-block view: the whole reachable zone fits
    private int centerX, centerZ;
    private int mapX, mapY, mapSize;
    private int age;
    private EditBox boxX, boxZ;
    private Button ammoBtn, modeBtn;

    // solution cache
    private BlockPos solTarget;
    private RocketType solType;
    private Vec3 solFrom;
    private Ballistics.Solution sol;
    private double minRange, maxRange;
    private RocketType ringType;
    private float ringScale = -1;

    public FireControlScreen(Bm21Entity v) {
        super(Component.translatable("screen.aiarsenal.fcs"));
        this.v = v;
    }

    @Override
    protected void init() {
        mapSize = Math.min(TEX, Math.min(height - 64, width - 196));
        mapX = 8;
        mapY = Math.max(12, (height - 52 - mapSize) / 2);
        int px = mapX + mapSize + 8, pw = Math.min(180, width - px - 6), y = mapY;
        boxX = new EditBox(font, px, y, 50, 14, Component.literal("X"));
        boxZ = new EditBox(font, px + 54, y, 50, 14, Component.literal("Z"));
        for (EditBox b : new EditBox[]{boxX, boxZ}) {
            b.setMaxLength(8);
            b.setFilter(s -> s.matches("-?\\d{0,7}"));
            addRenderableWidget(b);
        }
        v.getTarget().ifPresent(t -> {
            boxX.setValue(Integer.toString(t.getX()));
            boxZ.setValue(Integer.toString(t.getZ()));
        });
        addRenderableWidget(Button.builder(Component.translatable("screen.aiarsenal.set"), b -> setTargetFromBoxes())
                .bounds(px + 108, y, pw - 108, 14).build());
        y += 18;
        ammoBtn = addRenderableWidget(Button.builder(ammoLabel(), b -> {
            RocketType[] all = RocketType.values();
            int i = v.getAmmoSelection().ordinal() % (all.length - 1) + 1;
            send(VehicleCommandPacket.AMMO, i);
        }).bounds(px, y, pw, 16).build());
        y += 18;
        modeBtn = addRenderableWidget(Button.builder(modeLabel(), b -> {
            int cur = v.getFireMode(), next = MODES[0];
            for (int i = 0; i < MODES.length; i++) if (MODES[i] == cur) { next = MODES[(i + 1) % MODES.length]; break; }
            send(VehicleCommandPacket.MODE, next);
        }).bounds(px, y, pw, 16).build());
        y += 18;
        int hw = (pw - 4) / 2;
        addRenderableWidget(Button.builder(Component.translatable("screen.aiarsenal.reload"), b -> send(VehicleCommandPacket.RELOAD, 0)).bounds(px, y, hw, 16).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.aiarsenal.position"), b -> send(VehicleCommandPacket.SHIELDS, 0)).bounds(px + hw + 4, y, hw, 16).build());
        y += 18;
        addRenderableWidget(Button.builder(Component.translatable("screen.aiarsenal.fire"), b -> send(VehicleCommandPacket.FIRE, 0)).bounds(px, y, hw, 16).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.aiarsenal.cease"), b -> send(VehicleCommandPacket.CEASE, 0)).bounds(px + hw + 4, y, hw, 16).build());
        y += 18;
        int tw = (pw - 8) / 3;
        addRenderableWidget(Button.builder(Component.translatable("screen.aiarsenal.clear"), b -> send(VehicleCommandPacket.CLEAR_TARGET, 0)).bounds(px, y, tw, 16).build());
        addRenderableWidget(Button.builder(Component.literal("−"), b -> { zoom = Math.min(ZOOMS.length - 1, zoom + 1); rebuildMap(); }).bounds(px + tw + 4, y, tw, 16).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> { zoom = Math.max(0, zoom - 1); rebuildMap(); }).bounds(px + 2 * tw + 8, y, tw, 16).build());
        if (tex == null) {
            tex = new DynamicTexture(TEX, TEX, false);
            Minecraft.getInstance().getTextureManager().register(MAP_ID, tex);
        }
        rebuildMap();
    }

    private Component ammoLabel() {
        RocketType t = v.getAmmoSelection();
        return Component.translatable("screen.aiarsenal.ammo", Component.translatable(t.translationKey()), v.loadedCount(t), v.rackCount(t));
    }

    private Component modeLabel() {
        return Component.translatable("screen.aiarsenal.mode", v.getFireMode());
    }

    private void send(int action, int value) {
        ClientEvents.send(v, action, value);
    }

    private void setTargetFromBoxes() {
        try {
            int x = Integer.parseInt(boxX.getValue()), z = Integer.parseInt(boxZ.getValue());
            designate(x, z);
        } catch (NumberFormatException ignored) {
            // incomplete input
        }
    }

    private void designate(int x, int z) {
        Integer y = clientGround(x, z);
        ModNetwork.CHANNEL.sendToServer(new VehicleCommandPacket(v.getId(), VehicleCommandPacket.TARGET, y == null ? 1 : 0,
                new BlockPos(x, y == null ? 0 : y, z)));
        boxX.setValue(Integer.toString(x));
        boxZ.setValue(Integer.toString(z));
    }

    /** Top block y if the chunk is loaded on the client, else null (the server resolves it). */
    private Integer clientGround(int x, int z) {
        ClientLevel lv = Minecraft.getInstance().level;
        if (lv == null) return null;
        LevelChunk c = (LevelChunk) lv.getChunkSource().getChunk(x >> 4, z >> 4, ChunkStatus.FULL, false);
        if (c == null) return null;
        return c.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15);
    }

    private void rebuildMap() {
        ClientLevel lv = Minecraft.getInstance().level;
        NativeImage img = tex.getPixels();
        if (lv == null || img == null) return;
        centerX = Mth.floor(v.getX());
        centerZ = Mth.floor(v.getZ());
        int s = ZOOMS[zoom];
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        int[] prevRow = new int[TEX];
        for (int j = 0; j < TEX; j++) {
            for (int i = 0; i < TEX; i++) {
                int x = centerX + (i - TEX / 2) * s, z = centerZ + (j - TEX / 2) * s;
                LevelChunk c = (LevelChunk) lv.getChunkSource().getChunk(x >> 4, z >> 4, ChunkStatus.FULL, false);
                int col;
                int h = Integer.MIN_VALUE;
                if (c == null) {
                    boolean grid = ((x >> 4) + (z >> 4) & 1) == 0;
                    col = grid ? 0xFF1E1E1E : 0xFF262626;
                } else {
                    h = c.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15);
                    mp.set(x, h, z);
                    MapColor mc = c.getBlockState(mp).getMapColor(lv, mp);
                    MapColor.Brightness br = MapColor.Brightness.NORMAL;
                    if (j > 0 && prevRow[i] != Integer.MIN_VALUE) {
                        if (h > prevRow[i]) br = MapColor.Brightness.HIGH;
                        else if (h < prevRow[i]) br = MapColor.Brightness.LOW;
                    }
                    col = mc == MapColor.NONE ? 0xFF000000 : mc.calculateRGBColor(br);
                }
                prevRow[i] = h;
                img.setPixelRGBA(i, j, col);
            }
        }
        tex.upload();
    }

    @Override
    public void tick() {
        age++;
        if (!v.isAlive()) {
            onClose();
            return;
        }
        if (age % 100 == 0 || Math.abs(v.getX() - centerX) > 8 * ZOOMS[zoom] || Math.abs(v.getZ() - centerZ) > 8 * ZOOMS[zoom]) rebuildMap();
        ammoBtn.setMessage(ammoLabel());
        modeBtn.setMessage(modeLabel());
        boxX.tick();
        boxZ.tick();
    }

    private void updateSolution() {
        RocketType t = v.fireType();
        if (ringType != t || ringScale != v.getRangeScale()) {
            ringType = t;
            ringScale = v.getRangeScale();
            double thrust = Ballistics.thrust(ringScale, t == RocketType.NONE ? 1 : t.rangeFactor);
            minRange = Ballistics.simulate(Ballistics.MIN_ELEV, thrust, 2.45, 0).horizontalDistance();
            maxRange = Ballistics.maxRange(thrust, 0, 0);
        }
        BlockPos tg = v.getTarget().orElse(null);
        Vec3 from = v.launcherPivot();
        if (tg == null) {
            sol = null;
            solTarget = null;
            return;
        }
        if (!tg.equals(solTarget) || t != solType || solFrom == null || solFrom.distanceToSqr(from) > 0.25) {
            solTarget = tg;
            solType = t;
            solFrom = from;
            sol = v.solve(tg, t);
        }
    }

    private int[] toScreen(double wx, double wz) {
        double s = ZOOMS[zoom] * (double) TEX / mapSize;
        return new int[]{mapX + (int) Math.round((wx - centerX) / s + mapSize / 2.0), mapY + (int) Math.round((wz - centerZ) / s + mapSize / 2.0)};
    }

    private double[] toWorld(double sx, double sy) {
        double s = ZOOMS[zoom] * (double) TEX / mapSize;
        return new double[]{centerX + (sx - mapX - mapSize / 2.0) * s, centerZ + (sy - mapY - mapSize / 2.0) * s};
    }

    private boolean inMap(double sx, double sy) {
        return sx >= mapX && sy >= mapY && sx < mapX + mapSize && sy < mapY + mapSize;
    }

    private void dot(GuiGraphics g, int x, int y, int r, int col) {
        if (inMap(x, y)) g.fill(x - r, y - r, x + r + 1, y + r + 1, col);
    }

    private void line(GuiGraphics g, double x0, double y0, double x1, double y1, int col) {
        int n = (int) Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        for (int i = 0; i <= n; i += 1) {
            double f = n == 0 ? 0 : i / (double) n;
            int x = (int) Math.round(x0 + (x1 - x0) * f), y = (int) Math.round(y0 + (y1 - y0) * f);
            if (inMap(x, y)) g.fill(x, y, x + 1, y + 1, col);
        }
    }

    private void ring(GuiGraphics g, double wx, double wz, double r, int col) {
        int n = 180;
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n;
            int[] p = toScreen(wx + Math.cos(a) * r, wz + Math.sin(a) * r);
            dot(g, p[0], p[1], 0, col);
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        updateSolution();
        g.fill(mapX - 2, mapY - 2, mapX + mapSize + 2, mapY + mapSize + 2, 0xFF7A6A3A);
        g.blit(MAP_ID, mapX, mapY, mapSize, mapSize, 0, 0, TEX, TEX, TEX, TEX);
        // grid every 100 blocks
        int s = ZOOMS[zoom];
        int step = s >= 4 ? 200 : 100;
        double[] w0 = toWorld(mapX, mapY), w1 = toWorld(mapX + mapSize, mapY + mapSize);
        for (int gx = (int) Math.ceil(w0[0] / step) * step; gx < w1[0]; gx += step) {
            int[] p = toScreen(gx, 0);
            g.fill(p[0], mapY, p[0] + 1, mapY + mapSize, 0x30FFFFFF);
        }
        for (int gz = (int) Math.ceil(w0[1] / step) * step; gz < w1[1]; gz += step) {
            int[] p = toScreen(0, gz);
            g.fill(mapX, p[1], mapX + mapSize, p[1] + 1, 0x30FFFFFF);
        }
        Vec3 piv = v.launcherPivot();
        ring(g, piv.x, piv.z, minRange, 0xFFFF5050);
        ring(g, piv.x, piv.z, maxRange, 0xFF50FF50);
        int[] vp = toScreen(v.getX(), v.getZ());
        // hull direction and launcher direction
        Vec3 fwd = v.forward();
        double launcherYaw = v.getYRot() + v.getTurret();
        Vec3 ld = Vec3.directionFromRotation(0, (float) launcherYaw);
        double len = 14;
        line(g, vp[0], vp[1], vp[0] + fwd.x * 6, vp[1] + fwd.z * 6, 0xFFFFFFFF);
        line(g, vp[0], vp[1], vp[0] + ld.x * len, vp[1] + ld.z * len, 0xFFFFA020);
        dot(g, vp[0], vp[1], 2, 0xFF2080FF);
        if (solTarget != null) {
            int[] tp = toScreen(solTarget.getX() + 0.5, solTarget.getZ() + 0.5);
            line(g, vp[0], vp[1], tp[0], tp[1], 0x90FF3030);
            int col = sol != null && sol.ok() ? 0xFFFF2020 : 0xFF909090;
            line(g, tp[0] - 5, tp[1] - 5, tp[0] + 5, tp[1] + 5, col);
            line(g, tp[0] - 5, tp[1] + 5, tp[0] + 5, tp[1] - 5, col);
            double disp = v.horizontalDistanceTo(solTarget) * 0.012 * 2;
            ring(g, solTarget.getX() + 0.5, solTarget.getZ() + 0.5, Math.max(disp, 3), 0xC0FFD040);
        }
        g.drawString(font, "N", mapX + mapSize / 2 - 2, mapY + 3, 0xFFFFFFFF);
        g.drawString(font, Component.translatable("screen.aiarsenal.scale", TEX * s), mapX + 4, mapY + mapSize - 11, 0xFFFFFFFF);
        if (inMap(mx, my)) {
            double[] w = toWorld(mx, my);
            double d = Math.sqrt(Math.pow(w[0] - piv.x, 2) + Math.pow(w[1] - piv.z, 2));
            String str = String.format("%d, %d  %d m  %s", (int) Math.floor(w[0]), (int) Math.floor(w[1]), (int) d,
                    Ballistics.mils(Ballistics.yawTo(piv, w[0], w[1])));
            int tx = Math.min(mx + 8, mapX + mapSize - font.width(str) - 2), ty = Math.max(mapY + 2, my - 10);
            g.fill(tx - 2, ty - 2, tx + 2 + font.width(str), ty + 9, 0xC0000000);
            g.drawString(font, str, tx, ty, d <= maxRange && d >= minRange ? 0xFF80FF80 : 0xFFFF8080);
        }
        super.render(g, mx, my, pt);
        // control hints at the bottom
        var hl = new java.util.ArrayList<net.minecraft.util.FormattedCharSequence>();
        hl.addAll(font.split(Component.translatable("screen.aiarsenal.help1"), width - 8));
        hl.addAll(font.split(Component.translatable("screen.aiarsenal.help2"), width - 8));
        int hy = height - 2 - hl.size() * 10;
        g.fill(0, hy - 3, width, height, 0xB0000000);
        for (var l : hl) {
            g.drawString(font, l, width / 2 - font.width(l) / 2, hy, 0xFFE0E0E0);
            hy += 10;
        }
        // info panel
        int px = mapX + mapSize + 8, y = mapY + 18 * 6 + 4, lh = 10;
        g.drawString(font, title, px, mapY - 11 < 0 ? 0 : mapY - 11, 0xFFFFD060);
        int ph = v.getMissionPhase();
        g.drawString(font, Component.translatable("hud.aiarsenal.phase." + ph).append(ph != 0 ? " (" + (v.getMission() & 0xFF) + ")" : ""), px, y, ph == Bm21Entity.PH_FIRING ? 0xFFFF6040 : 0xFFE0E0E0);
        y += lh;
        g.drawString(font, Component.translatable("screen.aiarsenal.loaded", v.loadedCount(), v.rackTotal()), px, y, 0xFFE0E0E0);
        y += lh;
        g.drawString(font, Component.translatable("screen.aiarsenal.zone", (int) minRange, (int) maxRange), px, y, 0xFFB0B0B0);
        y += lh;
        if (solTarget != null && sol != null) {
            double d = v.horizontalDistanceTo(solTarget);
            String az = Ballistics.mils(Ballistics.yawTo(piv, solTarget.getX() + 0.5, solTarget.getZ() + 0.5));
            g.drawString(font, Component.translatable("screen.aiarsenal.tgt", solTarget.getX(), solTarget.getY(), solTarget.getZ()), px, y, 0xFF80FFFF);
            y += lh;
            g.drawString(font, Component.translatable("screen.aiarsenal.dist", (int) d, az), px, y, 0xFF80FFFF);
            y += lh;
            if (sol.ok()) {
                g.drawString(font, Component.translatable("screen.aiarsenal.sol", String.format("%.1f", sol.elevationDeg()),
                        String.format("%.1f", sol.flightTicks() / 20.0), (int) sol.apex()), px, y, 0xFF80FF80);
            } else {
                g.drawString(font, Component.translatable("msg.aiarsenal.reason." + sol.reason()), px, y, 0xFFFF6060);
            }
        } else {
            g.drawString(font, Component.translatable("screen.aiarsenal.hint"), px, y, 0xFFA0A0A0);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && inMap(mx, my)) {
            double[] w = toWorld(mx, my);
            designate((int) Math.floor(w[0]), (int) Math.floor(w[1]));
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (inMap(mx, my)) {
            zoom = Mth.clamp(zoom - (int) Math.signum(delta), 0, ZOOMS.length - 1);
            rebuildMap();
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        if (tex != null) {
            Minecraft.getInstance().getTextureManager().release(MAP_ID);
            tex = null;
        }
    }
}
