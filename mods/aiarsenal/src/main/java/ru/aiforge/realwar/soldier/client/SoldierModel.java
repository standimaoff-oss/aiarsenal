package ru.aiforge.realwar.soldier.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.resources.ResourceLocation;
import ru.aiforge.realwar.soldier.SoldierEntity;

/**
 * r27: the AI soldier body - the classic Steve shape (the player's own head and
 * body geometry).  The base pass draws the Steve skin; the uniform is a second
 * overlay pass over the same geometry (see {@link SoldierUniformLayer}).
 */
public class SoldierModel extends HumanoidModel<SoldierEntity> {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(new ResourceLocation("aiarsenal", "soldier"), "main");

    public SoldierModel(ModelPart root) {
        super(root);
    }

    /** 64x64 Steve/player humanoid geometry. */
    public static LayerDefinition createBodyLayer() {
        return LayerDefinition.create(HumanoidModel.createMesh(CubeDeformation.NONE, 0.0f), 64, 64);
    }
}
