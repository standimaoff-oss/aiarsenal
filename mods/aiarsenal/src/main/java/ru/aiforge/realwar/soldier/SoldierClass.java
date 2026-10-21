package ru.aiforge.realwar.soldier;

import net.minecraft.resources.ResourceLocation;

/**
 * r27: classes of the summonable AI soldiers. The body is the classic Steve skin
 * (the player's own head and body shape); the uniform is an overlay drawn over it.
 */
public enum SoldierClass {
    COMMANDER("commander"),
    TANKER("tanker"),
    ASSAULT("assault"),
    MEDIC("medic");

    public final String id;

    SoldierClass(String id) {
        this.id = id;
    }

    public static SoldierClass byId(String id) {
        for (SoldierClass c : values()) if (c.id.equals(id)) return c;
        return null;
    }

    /** 64x64 uniform overlay texture (drawn over the Steve skin). */
    public ResourceLocation uniformTexture() {
        return new ResourceLocation("aiarsenal", "textures/entity/soldier/uniform_" + id + ".png");
    }

    public String nameKey() {
        return "entity.aiarsenal.soldier." + id;
    }
}
