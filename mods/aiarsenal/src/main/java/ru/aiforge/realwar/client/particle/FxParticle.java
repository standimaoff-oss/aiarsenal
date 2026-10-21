/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.platform.GlStateManager$DestFactor
 *  com.mojang.blaze3d.platform.GlStateManager$SourceFactor
 *  com.mojang.blaze3d.systems.RenderSystem
 *  com.mojang.blaze3d.vertex.BufferBuilder
 *  com.mojang.blaze3d.vertex.DefaultVertexFormat
 *  com.mojang.blaze3d.vertex.Tesselator
 *  com.mojang.blaze3d.vertex.VertexFormat$Mode
 *  net.minecraft.client.multiplayer.ClientLevel
 *  net.minecraft.client.particle.ParticleRenderType
 *  net.minecraft.client.particle.TextureSheetParticle
 *  net.minecraft.client.renderer.GameRenderer
 *  net.minecraft.client.renderer.LightTexture
 *  net.minecraft.client.renderer.texture.TextureAtlas
 *  net.minecraft.client.renderer.texture.TextureAtlasSprite
 *  net.minecraft.client.renderer.texture.TextureManager
 *  net.minecraft.resources.ResourceLocation
 *  net.minecraft.util.Mth
 *  net.minecraft.world.phys.AABB
 */
package ru.aiforge.realwar.client.particle;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

public class FxParticle
extends TextureSheetParticle {
    private static float savedFogStart;
    private static float savedFogEnd;
    public static final ParticleRenderType ADDITIVE;
    public static final ParticleRenderType SMOKE;
    private final boolean additive;
    private boolean shaderSafe;
    /** Fixed before ParticleEngine queues this particle; never change its queue key in flight. */
    public void useShaderSafeRendering() { shaderSafe = true; }
    private final boolean volumetricSmoke;
    public boolean volumetricSmoke() { return volumetricSmoke; }
    public float lifeSeconds() { return lifetime / 20f; }
    public Motion motion;
    public float size;
    private float prevSize;
    public boolean fullBright;
    public float spin;
    public float drag = 1.0f;
    public double sortKey;

    private static void fogPush() {
        savedFogStart = RenderSystem.getShaderFogStart();
        savedFogEnd = RenderSystem.getShaderFogEnd();
        RenderSystem.setShaderFogStart((float)(savedFogEnd * 6.0f));
        RenderSystem.setShaderFogEnd((float)(savedFogEnd * 8.0f));
    }

    private static void fogPop() {
        RenderSystem.setShaderFogStart((float)savedFogStart);
        RenderSystem.setShaderFogEnd((float)savedFogEnd);
    }

    public FxParticle(ClientLevel level, double x, double y, double z, TextureAtlasSprite sprite, int lifetime, boolean additive, float size) {
        super(level, x, y, z);
        this.additive = additive;
        this.volumetricSmoke = !additive && sprite.contents().name().getPath().contains("smoke");
        this.size = size;
        this.prevSize = size;
        this.lifetime = Math.max(2, lifetime);
        this.hasPhysics = false;
        this.gravity = 0.0f;
        this.zd = 0.0;
        this.yd = 0.0;
        this.xd = 0.0;
        this.oRoll = this.roll = level.random.nextFloat() * ((float)Math.PI * 2);
        this.setSprite(sprite);
    }

    public void setPosition(double nx, double ny, double nz) {
        this.x = nx;
        this.y = ny;
        this.z = nz;
    }

    public void translate(double dx, double dy, double dz) {
        this.x += dx;
        this.y += dy;
        this.z += dz;
    }

    public boolean isAdditive() {
        return this.additive;
    }

    public double lerpX(float pt) {
        return Mth.lerp((double)pt, (double)this.xo, (double)this.x);
    }

    public double lerpY(float pt) {
        return Mth.lerp((double)pt, (double)this.yo, (double)this.y);
    }

    public double lerpZ(float pt) {
        return Mth.lerp((double)pt, (double)this.zo, (double)this.z);
    }

    public double px() {
        return this.x;
    }

    public double py() {
        return this.y;
    }

    public double pz() {
        return this.z;
    }

    public void roll(float radians) {
        this.oRoll = this.roll = radians;
    }

    public void color(float r, float g, float b) {
        this.rCol = r;
        this.gCol = g;
        this.bCol = b;
    }

    public void alpha(float a) {
        this.alpha = Mth.clamp((float)a, (float)0.0f, (float)1.0f);
    }

    public void velocity(double vx, double vy, double vz) {
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
    }

    public double getDeltaX() {
        return this.xd;
    }

    public double getDeltaY() {
        return this.yd;
    }

    public double getDeltaZ() {
        return this.zd;
    }

    public void scaleVelocity(double k) {
        this.xd *= k;
        this.yd *= k;
        this.zd *= k;
    }

    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        this.oRoll = this.roll;
        this.prevSize = this.size;
        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }
        if (this.motion != null) {
            this.motion.apply(this, this.age);
        } else {
            this.x += this.xd;
            this.y += this.yd;
            this.z += this.zd;
            this.xd *= (double)this.drag;
            this.yd *= (double)this.drag;
            this.zd *= (double)this.drag;
        }
        this.roll += this.spin;
        double h = (double)Math.max(this.size, 0.25f) * 0.75;
        this.setBoundingBox(new AABB(this.x - h, this.y - h, this.z - h, this.x + h, this.y + h, this.z + h));
    }

    public float getQuadSize(float partial) {
        return Mth.lerp((float)partial, (float)this.prevSize, (float)this.size);
    }

    public ParticleRenderType getRenderType() {
        return this.additive ? ADDITIVE : SMOKE;
    }

    protected int getLightColor(float partial) {
        if (this.fullBright || this.additive) {
            return 0xF000F0;
        }
        int l = super.getLightColor(partial);
        int sky = Math.max(l >> 20 & 0xF, 3);
        int blk = Math.max(l >> 4 & 0xF, 2);
        return LightTexture.pack((int)blk, (int)sky);
    }

    static {
        ADDITIVE = new ParticleRenderType(){

            public void begin(BufferBuilder b, TextureManager tm) {
                RenderSystem.depthMask((boolean)false);
                RenderSystem.setShader(GameRenderer::getParticleShader);
                RenderSystem.setShaderTexture((int)0, (ResourceLocation)TextureAtlas.LOCATION_PARTICLES);
                RenderSystem.enableBlend();
                RenderSystem.blendFunc((GlStateManager.SourceFactor)GlStateManager.SourceFactor.SRC_ALPHA, (GlStateManager.DestFactor)GlStateManager.DestFactor.ONE);
                FxParticle.fogPush();
                b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
            }

            public void end(Tesselator t) {
                t.end();
                FxParticle.fogPop();
                RenderSystem.defaultBlendFunc();
                RenderSystem.depthMask((boolean)true);
            }

            public String toString() {
                return "aiarsenal:additive";
            }
        };
        SMOKE = new ParticleRenderType(){

            public void begin(BufferBuilder b, TextureManager tm) {
                RenderSystem.depthMask((boolean)true);
                RenderSystem.setShader(GameRenderer::getParticleShader);
                RenderSystem.setShaderTexture((int)0, (ResourceLocation)TextureAtlas.LOCATION_PARTICLES);
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                FxParticle.fogPush();
                b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
            }

            public void end(Tesselator t) {
                t.end();
                FxParticle.fogPop();
            }

            public String toString() {
                return "aiarsenal:smoke";
            }
        };
    }

    public static interface Motion {
        public void apply(FxParticle var1, int var2);
    }
}

