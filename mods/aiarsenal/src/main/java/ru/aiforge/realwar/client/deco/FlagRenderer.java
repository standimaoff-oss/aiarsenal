package ru.aiforge.realwar.client.deco;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import ru.aiforge.realwar.RealWar;
import ru.aiforge.realwar.deco.FlagBlock;
import ru.aiforge.realwar.deco.FlagBlockEntity;

/** Animated flag: the 16 x 10 cloth grid (1.5 x 1 in the file, shown as 3 x 2 blocks) modelled in Blender (models/block/deco/flag_cloth.obj) is bent by a travelling
 *  wave (zero at the hoist, growing to the fly), sags a little and flutters faster in rain. Beyond 64 blocks the
 *  wave freezes (no per-frame maths for far flags). */
public class FlagRenderer implements BlockEntityRenderer<FlagBlockEntity> {
    private static final ResourceLocation MESH = new ResourceLocation("aiarsenal", "models/block/deco/flag_cloth.obj");
    private static float[] tri;     // x, y, z, u, v per vertex, 3 vertices per triangle
    private static final float CL = 1.5f, CH = 1.0f;        // size of the modelled grid
    private static final double DL = 3.0, DH = 2.0;         // displayed cloth: 3 x 2 blocks

    public FlagRenderer(BlockEntityRendererProvider.Context c) {
    }

    public static void clear() {
        tri = null;
    }

    private static void load() {
        List<float[]> v = new ArrayList<>(), vt = new ArrayList<>();
        List<Float> out = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(Minecraft.getInstance().getResourceManager().getResource(MESH).orElseThrow().open(), StandardCharsets.UTF_8))) {
            String l;
            while ((l = r.readLine()) != null) {
                String[] t = l.trim().split("\\s+");
                if (t[0].equals("v")) v.add(new float[]{Float.parseFloat(t[1]), Float.parseFloat(t[2]), Float.parseFloat(t[3])});
                else if (t[0].equals("vt")) vt.add(new float[]{Float.parseFloat(t[1]), Float.parseFloat(t[2])});
                else if (t[0].equals("f") && t.length == 4) {
                    for (int i = 1; i <= 3; ++i) {
                        String[] q = t[i].split("/");
                        float[] p = v.get(Integer.parseInt(q[0]) - 1), uv = vt.get(Integer.parseInt(q[1]) - 1);
                        out.add(p[0]); out.add(p[1]); out.add(p[2]); out.add(uv[0]); out.add(1f - uv[1]);
                    }
                }
            }
        } catch (Exception e) {
            RealWar.LOGGER.error("flag cloth mesh", e);
        }
        float[] a = new float[out.size()];
        for (int i = 0; i < a.length; ++i) a[i] = out.get(i);
        tri = a;
    }

    /** Displaced cloth point for grid coords (u along the fly 0..1, v down 0..1). */
    private static void pos(double u, double v, double t, double amp, double[] o) {
        double w = Math.sin(u * 7.6 - t * 4.0 + v * 0.8) + 0.35 * Math.sin(u * 15.5 - t * 6.0 + v * 1.9);
        o[0] = 0.22 * amp * u * w;
        o[1] = -v * DH - 0.12 * u * u * (1.6 - amp);
        o[2] = -u * DL * (1 - 0.025 * amp * (1 + Math.sin(t * 2.1 + u * 3)));
    }

    @Override
    public void render(FlagBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
        if (tri == null) load();
        if (tri.length == 0 || be.getLevel() == null) return;
        BlockState st = be.getBlockState();
        if (!(st.getBlock() instanceof FlagBlock fb)) return;
        Direction f = st.getValue(FlagBlock.FACING);
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        boolean anim = cam.distanceToSqr(Vec3.atCenterOf(be.getBlockPos())) < 64 * 64;
        long ph = (be.getBlockPos().asLong() * 2654435761L) >>> 40;
        double t = anim ? (be.getLevel().getGameTime() + pt) / 20.0 * (be.getLevel().isRaining() ? 1.5 : 1.0) + (ph & 1023) / 97.0 : (ph & 1023) / 97.0;
        double amp = be.getLevel().isThundering() ? 1.5 : be.getLevel().isRaining() ? 1.3 : 1.0;
        ps.pushPose();
        ps.translate(0.5, 0, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(-f.toYRot() + 180f));
        ps.translate(0, 0.97, -0.07);
        Matrix4f m = ps.last().pose();
        Matrix3f nm = ps.last().normal();
        VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(fb.texture));
        double[] p = new double[3], pu = new double[3], pv = new double[3];
        float[] vx = new float[15];
        for (int i = 0; i + 15 <= tri.length; i += 15) {
            for (int k = 0; k < 3; ++k) {
                int b = i + k * 5;
                double u = Math.max(0, -tri[b + 2] / CL), v = Math.max(0, -tri[b + 1] / CH);
                pos(u, v, t, amp, p);
                pos(u + 0.02, v, t, amp, pu);
                pos(u, v + 0.02, t, amp, pv);
                double ax = pu[0] - p[0], ay = pu[1] - p[1], az = pu[2] - p[2];
                double bx = pv[0] - p[0], by = pv[1] - p[1], bz = pv[2] - p[2];
                double nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
                double nl = Math.sqrt(nx * nx + ny * ny + nz * nz) + 1e-9;
                vx[k * 5] = (float) p[0]; vx[k * 5 + 1] = (float) p[1]; vx[k * 5 + 2] = (float) p[2];
                vx[k * 5 + 3] = tri[b + 3]; vx[k * 5 + 4] = tri[b + 4];
                emit(vc, m, nm, vx, k, (float) (nx / nl), (float) (ny / nl), (float) (nz / nl), light);
            }
            emit(vc, m, nm, vx, 2, lastN[0], lastN[1], lastN[2], light);   // triangle -> degenerate quad
        }
        ps.popPose();
    }

    private final float[] lastN = new float[3];

    private void emit(VertexConsumer vc, Matrix4f m, Matrix3f nm, float[] vx, int k, float nx, float ny, float nz, int light) {
        lastN[0] = nx; lastN[1] = ny; lastN[2] = nz;
        vc.vertex(m, vx[k * 5], vx[k * 5 + 1], vx[k * 5 + 2]).color(255, 255, 255, 255).uv(vx[k * 5 + 3], vx[k * 5 + 4])
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(nm, nx, ny, nz).endVertex();
    }

    @Override
    public int getViewDistance() {
        return 128;
    }
}
