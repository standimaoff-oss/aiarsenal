/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.vertex.PoseStack
 *  com.mojang.math.Axis
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer
 *  net.minecraft.client.renderer.MultiBufferSource
 *  net.minecraft.client.renderer.texture.OverlayTexture
 *  net.minecraft.resources.ResourceLocation
 *  net.minecraft.world.item.BlockItem
 *  net.minecraft.world.item.Item
 *  net.minecraft.world.item.ItemDisplayContext
 *  net.minecraft.world.item.ItemStack
 *  net.minecraftforge.registries.ForgeRegistries
 */
package ru.aiforge.realwar.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import ru.aiforge.realwar.client.render.GlbAssets;
import ru.aiforge.realwar.client.render.GlbRenderer;
import ru.aiforge.realwar.client.render.LaunchPadRenderer;
import ru.aiforge.realwar.missile.LaunchPadBlock;
import ru.aiforge.realwar.missile.LaunchPadItem;
import ru.aiforge.realwar.missile.MissileItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

public class GlbItemRenderer
extends BlockEntityWithoutLevelRenderer {
    private static GlbItemRenderer inst;

    public static GlbItemRenderer instance() {
        if (inst == null) {
            Minecraft mc = Minecraft.getInstance();
            inst = new GlbItemRenderer(mc);
        }
        return inst;
    }

    private GlbItemRenderer(Minecraft mc) {
        super(mc.getBlockEntityRenderDispatcher(), mc.getEntityModels());
    }

    public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
        double scale;
        ResourceLocation model;
        Item item = stack.getItem();
        if (item instanceof ru.aiforge.realwar.handheld.LauncherWeaponItem || item instanceof ru.aiforge.realwar.handheld.HandheldAmmoItem
                || item instanceof ru.aiforge.realwar.item.RadioItem) {
            this.renderHandheld(stack, item, ctx, ps, buf, light);
            return;
        }
        double ox = 0.0;
        double oz = 0.0;
        boolean missile = false;
        double len = 1.0;
        if (item instanceof MissileItem) {
            MissileItem mi = (MissileItem)item;
            model = mi.type.model();
            len = mi.type.cls.length;
            scale = 1.25 / len;
            missile = true;
        } else if (item instanceof LaunchPadItem) {
            LaunchPadBlock b = (LaunchPadBlock)((BlockItem)item).getBlock();
            int n = b.level;
            model = new ResourceLocation("aiarsenal", "models/glb/launch_pad_" + n + ".glb");
            double top = LaunchPadRendererDims.TOWER[n] + LaunchPadBlock.HEIGHT[n];
            scale = 1.05 / Math.max(top, (double)n * 1.0);
            ox = oz = (double)(-n) / 2.0;
        } else {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
            if (id == null) {
                return;
            }
            model = new ResourceLocation("aiarsenal", "models/glb/" + id.getPath() + ".glb");
            scale = id.getPath().equals("siren") ? 0.5 : 1.0;
            oz = -0.5;
            ox = -0.5;
        }
        GlbAssets.Loaded lm = GlbAssets.get(model);
        if (lm == null) {
            return;
        }
        ps.pushPose();
        if (missile) {
            ps.translate(0.5, 0.5, 0.5);
            if (ctx == ItemDisplayContext.GUI) {
                ps.mulPose(Axis.ZP.rotationDegrees(-35.0f));
            }
            ps.mulPose(Axis.XP.rotationDegrees(-90.0f));
            ps.scale((float)scale, (float)scale, (float)scale);
        } else {
            ps.translate(0.5, 0.0, 0.5);
            ps.scale((float)scale, (float)scale, (float)scale);
            ps.translate(ox, 0.0, oz);
        }
        GlbRenderer.render(lm, lm.staticPose(), ps, buf, light, OverlayTexture.NO_OVERLAY);
        ps.popPose();
    }

    /** Shoulder launchers (RPG-7 hides the warhead once fired) and their rounds, placed by handheld_view.json. */
    private void renderHandheld(ItemStack stack, Item item, ItemDisplayContext ctx, PoseStack ps, MultiBufferSource buf, int light) {
        String id = ForgeRegistries.ITEMS.getKey(item).getPath();
        ResourceLocation model = new ResourceLocation("aiarsenal", "models/glb/" + id + ".glb");
        GlbAssets.Loaded lm = GlbAssets.get(model);
        if (lm == null) return;
        net.minecraft.world.entity.player.Player pl = Minecraft.getInstance().player;
        boolean aiming = pl != null && pl.isUsingItem() && pl.getUseItem() == stack;
        float[] v = HandheldView.get(id, ctx, aiming);
        float[] b = HandheldView.bounds(model, lm);
        ps.pushPose();
        HandheldView.apply(ps, v, b);
        ru.aiforge.realwar.glb.Pose pose = lm.staticPose();
        if (item instanceof ru.aiforge.realwar.handheld.LauncherWeaponItem lw && lw.kind == ru.aiforge.realwar.handheld.HandheldKind.RPG7
                && !ru.aiforge.realwar.handheld.LauncherWeaponItem.loaded(stack) && ctx != ItemDisplayContext.GUI) {
            pose = lm.scratch();
            pose.reset();
            pose.hide("RPG7_Rocket", true);
            pose.compute();
        }
        GlbRenderer.render(lm, pose, ps, buf, light, OverlayTexture.NO_OVERLAY);
        ps.popPose();
    }

    static final class LaunchPadRendererDims {
        static final double[] TOWER = LaunchPadRenderer.TOWER;

        LaunchPadRendererDims() {
        }
    }
}

