/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.renderer.texture.AbstractTexture
 *  net.minecraft.client.renderer.texture.TextureAtlas
 *  net.minecraft.client.renderer.texture.TextureAtlasSprite
 *  net.minecraft.resources.ResourceLocation
 */
package ru.aiforge.realwar.client.particle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;

public final class Sprites {
    private Sprites() {
    }

    private static TextureAtlasSprite get(String name) {
        AbstractTexture tex = Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_PARTICLES);
        if (!(tex instanceof TextureAtlas)) {
            throw new IllegalStateException("\u0410\u0442\u043b\u0430\u0441 \u0447\u0430\u0441\u0442\u0438\u0446 \u043d\u0435 \u043d\u0430\u0439\u0434\u0435\u043d \u0432 TextureManager: " + String.valueOf(tex));
        }
        TextureAtlas atlas = (TextureAtlas)tex;
        // the 1.20 particle atlas loads textures/particle/ via a directory source that strips "particle/":
        // the sprite id is aiarsenal:<name> (the old aiarsenal:particle/<name> lookup gave the magenta missing sprite)
        TextureAtlasSprite sp = atlas.getSprite(new ResourceLocation("aiarsenal", name));
        if (sp.contents().name().equals(net.minecraft.client.renderer.texture.MissingTextureAtlasSprite.getLocation())) {
            sp = atlas.getSprite(new ResourceLocation("aiarsenal", "particle/" + name));
        }
        return sp;
    }

    public static TextureAtlasSprite glow() {
        return Sprites.get("glow");
    }

    public static TextureAtlasSprite flare() {
        return Sprites.get("flare");
    }

    public static TextureAtlasSprite smoke(int i) {
        return Sprites.get("smoke_" + (i & 3));
    }
}

