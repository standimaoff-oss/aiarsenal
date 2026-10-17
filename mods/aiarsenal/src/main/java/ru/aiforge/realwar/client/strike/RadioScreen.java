package ru.aiforge.realwar.client.strike;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import ru.aiforge.arsenal.nuke.ChargeRegistry;
import ru.aiforge.realwar.client.sight.MapScreen;
import ru.aiforge.realwar.item.DesignatorItem;
import ru.aiforge.realwar.item.RadioItem;
import ru.aiforge.realwar.network.ModNetwork;
import ru.aiforge.realwar.ordnance.Ballistics;
import ru.aiforge.realwar.strike.StrikeManager;
import ru.aiforge.realwar.strike.StrikeRequestPacket;
import ru.aiforge.realwar.strike.StrikeType;

/**
 * Fire-support screen of the R-187P1 radio (reply 19). Compact War Thunder style panel: strike list with cooldowns, target source
 * (map / crosshair / designator), approach heading, nuclear fuze, CALL.
 */
public class RadioScreen extends Screen {
    private static StrikeType sel = StrikeType.ARTILLERY;
    private static Vec3 target;
    private static String targetSrc = "";
    private static boolean fromMe = true;
    private static int fuze = ChargeRegistry.FUZE_AIR;
    private static final int W = 300, H = 236;
    private int x0, y0;

    public RadioScreen() {
        super(Component.translatable("gui.aiarsenal.radio.title"));
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new RadioScreen());
    }

    @Override
    protected void init() {
        x0 = (width - W) / 2;
        y0 = (height - H) / 2;
        int y = y0 + 22;
        for (StrikeType t : StrikeType.values()) {
            addRenderableWidget(Button.builder(Component.translatable("gui.aiarsenal.radio.strike." + t.key()), b -> {
                sel = t;
                rebuildWidgets();
            }).bounds(x0 + 8, y, 132, 18).build()).active = sel != t;
            y += 20;
        }
        int ty = y0 + 130;
        addRenderableWidget(Button.builder(Component.translatable("gui.aiarsenal.radio.t_map"), b -> openMap()).bounds(x0 + 8, ty, 92, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.aiarsenal.radio.t_aim"), b -> aimTarget()).bounds(x0 + 104, ty, 92, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.aiarsenal.radio.t_designator"), b -> designatorTarget()).bounds(x0 + 200, ty, 92, 18).build());
        addRenderableWidget(Button.builder(Component.translatable(fromMe ? "gui.aiarsenal.radio.app_me" : "gui.aiarsenal.radio.app_look"), b -> {
            fromMe = !fromMe;
            rebuildWidgets();
        }).bounds(x0 + 8, ty + 36, 140, 18).build());
        if (sel == StrikeType.NUCLEAR)
            addRenderableWidget(Button.builder(Component.translatable(fuze == ChargeRegistry.FUZE_AIR ? "gui.aiarsenal.radio.fuze_air" : "gui.aiarsenal.radio.fuze_remote"), b -> {
                fuze = fuze == ChargeRegistry.FUZE_AIR ? ChargeRegistry.FUZE_REMOTE : ChargeRegistry.FUZE_AIR;
                rebuildWidgets();
            }).bounds(x0 + 152, ty + 36, 140, 18).build());
        Button call = addRenderableWidget(Button.builder(Component.translatable("gui.aiarsenal.radio.call"), b -> call()).bounds(x0 + 8, y0 + H - 26, 184, 20).build());
        call.active = target != null;
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose()).bounds(x0 + 196, y0 + H - 26, 96, 20).build());
    }

    private void openMap() {
        Player p = Minecraft.getInstance().player;
        if (p == null) return;
        BlockPos cur = target == null ? null : BlockPos.containing(target);
        MapScreen ms = new MapScreen(new MapScreen.Params(Component.translatable("gui.aiarsenal.radio.map"), p.position(), 0.0, StrikeManager.MAX_RANGE, false, cur,
                Ballistics.scaleK() / 1000.0, pos -> {
                    target = Vec3.atBottomCenterOf(pos);
                    targetSrc = "gui.aiarsenal.radio.src_map";
                }, () -> target = null));
        ms.parent = new RadioScreen();
        Minecraft.getInstance().setScreen(ms);
    }

    private void aimTarget() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        HitResult h = mc.player.pick(1500.0, 0f, false);
        if (h instanceof BlockHitResult bh && h.getType() != HitResult.Type.MISS) {
            target = bh.getLocation();
            targetSrc = "gui.aiarsenal.radio.src_aim";
        }
        rebuildWidgets();
    }

    private void designatorTarget() {
        Player p = Minecraft.getInstance().player;
        if (p == null) return;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            Vec3 v = DesignatorItem.target(p.getInventory().getItem(i));
            if (v != null) {
                target = v;
                targetSrc = "gui.aiarsenal.radio.src_designator";
                break;
            }
        }
        rebuildWidgets();
    }

    private float yaw() {
        Player p = Minecraft.getInstance().player;
        if (p == null || target == null) return 0f;
        double dx = target.x - p.getX(), dz = target.z - p.getZ();
        if (!fromMe || dx * dx + dz * dz < 40 * 40) return p.getYRot();
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    private void call() {
        if (target == null) return;
        ModNetwork.CHANNEL.sendToServer(new StrikeRequestPacket(sel.ordinal(), target.x, target.z, yaw(), fuze));
        onClose();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.fill(x0, y0, x0 + W, y0 + H, 0xE0101418);
        g.fill(x0, y0, x0 + W, y0 + 16, 0xFF26303A);
        g.drawString(font, title, x0 + 6, y0 + 4, 0xFFE8C46A, false);
        Player p = Minecraft.getInstance().player;
        ItemStack radio = p == null ? ItemStack.EMPTY : StrikeManager.radioIn(p);
        long now = p == null ? 0 : p.level().getGameTime();
        // strike list status column
        int y = y0 + 22;
        for (StrikeType t : StrikeType.values()) {
            long ready = RadioItem.readyAt(radio, t.key());
            boolean creative = p != null && p.getAbilities().instabuild;
            String st = ready > now && !creative ? Component.translatable("gui.aiarsenal.radio.cd", (ready - now + 19) / 20).getString()
                    : Component.translatable("gui.aiarsenal.radio.ready").getString();
            g.drawString(font, st, x0 + 146, y + 5, ready > now && !creative ? 0xFFD06040 : 0xFF7CD07C, false);
            y += 20;
        }
        // description of the selected strike
        int dy = y0 + 22;
        for (var line : font.split(Component.translatable("gui.aiarsenal.radio.desc." + sel.key()), 90)) {
            g.drawString(font, line, x0 + 206, dy, 0xFFB8C0C8, false);
            dy += 10;
        }
        int ty = y0 + 130;
        g.drawString(font, Component.translatable("gui.aiarsenal.radio.target"), x0 + 8, ty - 11, 0xFFE8C46A, false);
        String tgt;
        if (target == null) tgt = Component.translatable("gui.aiarsenal.radio.no_target").getString();
        else {
            double d = p == null ? 0 : Math.sqrt(Math.pow(target.x - p.getX(), 2) + Math.pow(target.z - p.getZ(), 2));
            tgt = (int) target.x + " / " + (int) target.z + "   " + String.format("%.2f", d / 1000.0) + " " + Component.translatable("hud.aiarsenal.rw.km").getString()
                    + "   " + Component.translatable(targetSrc).getString();
            if (d > StrikeManager.MAX_RANGE) tgt += "  " + Component.translatable("gui.aiarsenal.radio.far").getString();
        }
        g.drawString(font, tgt, x0 + 8, ty + 22, target == null ? 0xFF9098A0 : 0xFFFFFFFF, false);
        g.drawString(font, Component.translatable("gui.aiarsenal.radio.hint"), x0 + 8, ty + 60, 0xFF707880, false);
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
