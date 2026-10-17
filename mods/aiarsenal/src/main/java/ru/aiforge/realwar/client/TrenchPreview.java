package ru.aiforge.realwar.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.OptionalDouble;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.model.data.ModelData;
import ru.aiforge.realwar.field.EntrenchingToolItem;
import ru.aiforge.realwar.field.TrenchBlock;
import ru.aiforge.realwar.field.TrenchPlan;
import ru.aiforge.realwar.registry.ModBlocks;

/**
 * r13: while the entrenching tool holds a start point, the trench that the next right-click would dig is drawn as
 * a 50 % transparent "x-ray" through the ground: duckboards + revetted walls exactly as they will be placed, a light
 * outline of every trench cell (red where the ground cannot be dug) and a marker on the start point.
 */
public final class TrenchPreview {
    private TrenchPreview() {
    }

    /** Translucent block models without depth test (visible through the ground) and matching outline lines. */
    private static final class Types extends RenderType {
        private Types(String n, VertexFormat f, VertexFormat.Mode m, int b, boolean c, boolean s, Runnable a, Runnable r) {
            super(n, f, m, b, c, s, a, r);
        }

        static final RenderType GHOST = create("aiarsenal_trench_ghost", DefaultVertexFormat.BLOCK, VertexFormat.Mode.QUADS, 1 << 18, false, false,
                CompositeState.builder().setShaderState(RENDERTYPE_TRANSLUCENT_SHADER).setTextureState(BLOCK_SHEET_MIPPED)
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setLightmapState(LIGHTMAP).setDepthTestState(NO_DEPTH_TEST)
                        .setWriteMaskState(COLOR_WRITE).setCullState(CULL).createCompositeState(false));
        static final RenderType LINES = create("aiarsenal_trench_lines", DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES, 1 << 14, false, false,
                CompositeState.builder().setShaderState(RENDERTYPE_LINES_SHADER).setLineState(new LineStateShard(OptionalDouble.of(2.0)))
                        .setLayeringState(VIEW_OFFSET_Z_LAYERING).setTransparencyState(TRANSLUCENT_TRANSPARENCY).setDepthTestState(NO_DEPTH_TEST)
                        .setWriteMaskState(COLOR_WRITE).setCullState(NO_CULL).createCompositeState(false));
    }

    private static BlockPos lastA, lastB;
    private static long lastTick = -1;
    private static TrenchPlan cached;

    public static void render(RenderLevelStageEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        ItemStack st = mc.player.getMainHandItem();
        if (!(st.getItem() instanceof EntrenchingToolItem)) return;
        BlockPos a = EntrenchingToolItem.start(st, mc.level);
        if (a == null) return;
        BlockPos b = EntrenchingToolItem.aimed(mc.player, e.getPartialTick());       // same 64-block aim as the server

        PoseStack ps = e.getPoseStack();
        Vec3 cam = e.getCamera().getPosition();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);
        float pulse = 0.75f + 0.25f * (float) Math.sin((mc.level.getGameTime() + e.getPartialTick()) * 0.25);
        VertexConsumer lines = buf.getBuffer(Types.LINES);
        LevelRenderer.renderLineBox(ps, lines, new AABB(a).inflate(0.02), 1.0f, 0.85f, 0.2f, pulse);   // start marker

        if (b != null) {
            long tick = mc.level.getGameTime();
            if (!b.equals(lastB) || !a.equals(lastA) || tick - lastTick > 10 || cached == null) {   // re-plan on change / 2 Hz
                cached = TrenchPlan.plan(mc.level, a, b);
                lastA = a; lastB = b; lastTick = tick;
            }
            TrenchPlan plan = cached;
            if (plan.cells.isEmpty()) {
                LevelRenderer.renderLineBox(ps, lines, new AABB(b).inflate(0.02), 1.0f, 0.15f, 0.1f, 0.9f);   // too long
            } else {
                for (TrenchPlan.Cell c : plan.cells) {
                    AABB box = new AABB(c.floor()).expandTowards(0, 1, 0).inflate(0.01);
                    if (c.ok()) LevelRenderer.renderLineBox(ps, lines, box, 0.55f, 1.0f, 0.55f, 0.55f * pulse);
                    else LevelRenderer.renderLineBox(ps, lines, box, 1.0f, 0.2f, 0.15f, 0.8f);
                }
                VertexConsumer ghost = buf.getBuffer(Types.GHOST);
                BlockState duck = ModBlocks.DUCKBOARD.get().defaultBlockState();
                for (TrenchPlan.Cell c : plan.cells) {
                    if (c.ok() && !c.done()) ghostBlock(mc, ps, ghost, duck, c.floor(), 0.6f, 1.0f, 0.6f);
                }
                BlockState wall = ModBlocks.TRENCH_WALL.get().defaultBlockState();
                for (TrenchPlan.Wall w : plan.walls) {
                    ghostBlock(mc, ps, ghost, wall.setValue(TrenchBlock.FACING, w.facing()), w.pos(), 1.0f, 1.0f, 1.0f);
                }
            }
        }
        ps.popPose();
        buf.endBatch(Types.GHOST);
        buf.endBatch(Types.LINES);
    }

    private static final RandomSource RND = RandomSource.create();

    private static void ghostBlock(Minecraft mc, PoseStack ps, VertexConsumer vc, BlockState s, BlockPos pos, float r, float g, float b) {
        BakedModel model = mc.getBlockRenderer().getBlockModel(s);
        ps.pushPose();
        ps.translate(pos.getX(), pos.getY(), pos.getZ());
        PoseStack.Pose pose = ps.last();
        for (Direction d : DIRS) {
            RND.setSeed(42L);
            for (BakedQuad q : model.getQuads(s, d, RND, ModelData.EMPTY, null)) {
                vc.putBulkData(pose, q, r, g, b, 0.5f, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, false);
            }
        }
        ps.popPose();
    }

    private static final Direction[] DIRS = {Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, null};
}
