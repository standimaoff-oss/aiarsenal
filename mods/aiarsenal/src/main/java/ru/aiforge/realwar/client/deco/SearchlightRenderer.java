package ru.aiforge.realwar.client.deco;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import ru.aiforge.realwar.deco.SearchlightBlockEntity;

/** Yoke turns in azimuth, drum tilts on the trunnions (both baked OBJ parts); when on: lens glare + volumetric cone
 *  clipped to the first block the beam hits (client ray every 2 ticks). Sweep = same time function as the server. */
public class SearchlightRenderer implements BlockEntityRenderer<SearchlightBlockEntity> {
    public SearchlightRenderer(BlockEntityRendererProvider.Context c) {
    }

    @Override
    public void render(SearchlightBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
        if (be.getLevel() == null) return;
        BlockState st = be.getBlockState();
        double time = be.getLevel().getGameTime() + pt;
        float ty, tp;
        Entity tgt = be.on() && be.trackId >= 0 ? be.getLevel().getEntity(be.trackId) : null;
        Vec3 o = be.pivot();
        if (tgt != null) {
            Vec3 d = tgt.getPosition(pt).add(0, tgt.getBbHeight() * 0.5, 0).subtract(o);
            ty = (float) Math.toDegrees(Math.atan2(d.x, -d.z));
            tp = (float) Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        } else {
            float[] a = be.patrolAim(time);
            ty = a[0];
            tp = a[1];
        }
        if (Float.isNaN(be.cYaw)) {
            be.cYaw = ty;
            be.cPitch = tp;
        }
        float k = tgt != null ? 0.35f : 1f;                       // lock-on: smooth slew; patrol: exact
        be.cYaw += Mth.wrapDegrees(ty - be.cYaw) * k;
        be.cPitch += (tp - be.cPitch) * k;
        float yaw = be.cYaw, pitch = Mth.clamp(be.cPitch, -20f, 80f);

        ps.pushPose();
        ps.translate(0.5, 0, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(-yaw));
        ps.translate(-0.5, 0, -0.5);
        DecoModels.draw(DecoModels.YOKE, ps, buf, st, light);
        Vec3 pv = SearchlightBlockEntity.PIVOT;
        ps.translate(pv.x, pv.y, pv.z);
        ps.mulPose(Axis.XP.rotationDegrees(pitch));
        ps.translate(-pv.x, -pv.y, -pv.z);
        boolean on = be.on();
        DecoModels.draw(on ? DecoModels.DRUM : DecoModels.DRUM_OFF, ps, buf, st, on ? LightTexture.FULL_BRIGHT : light);
        if (on) {
            Vec3 dir = SearchlightBlockEntity.dir(yaw, pitch);
            long gt = be.getLevel().getGameTime();
            if (gt - be.cBeamTick >= 2 || be.cBeamTick < 0) {
                be.cBeamTick = gt;
                Vec3 a = o.add(dir.scale(0.45)), b = o.add(dir.scale(SearchlightBlockEntity.RANGE));
                BlockHitResult r = be.getLevel().clip(new ClipContext(a, b, ClipContext.Block.VISUAL, ClipContext.Fluid.ANY, null));
                be.cBeam = r.getType() == HitResult.Type.MISS ? SearchlightBlockEntity.RANGE : r.getLocation().distanceTo(o);
            }
            float len = (float) Math.max(0.5, be.cBeam - 0.36);
            VertexConsumer vc = buf.getBuffer(DecoRenderTypes.GLOW);
            ps.translate(pv.x, pv.y, pv.z - 0.37);
            float spread = (float) Math.tan(Math.toRadians(SearchlightBlockEntity.CONE_DEG * 0.75));
            Glow.cone(ps.last().pose(), vc, 0.30f, 0.30f + len * spread, len, 255, 248, 225, 46, 14);
            Glow.cone(ps.last().pose(), vc, 0.13f, 0.13f + len * spread * 0.35f, len, 255, 252, 240, 40, 10);
            Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            Vec3 toCam = cam.subtract(o).normalize();
            float glare = (float) Math.pow(Math.max(0, toCam.dot(dir)), 6);
            Glow.halo(ps, vc, 0.45f + 1.4f * glare, 255, 250, 230, (int) (90 + 150 * glare));
        }
        ps.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(SearchlightBlockEntity be) {
        return be.on();
    }

    @Override
    public int getViewDistance() {
        return 256;
    }
}
