package ru.aiforge.tubombers.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.entity.PartEntity;

/** Hitbox cell of the airframe (vanilla hitboxes cannot be 50 m long or rotate): picking, interaction and damage. */
public class BomberPart extends PartEntity<BomberEntity> {
    public final float lx, ly, lz;
    private final EntityDimensions size;

    public BomberPart(BomberEntity parent, float lx, float ly, float lz, float w, float h) {
        super(parent);
        this.lx = lx;
        this.ly = ly;
        this.lz = lz;
        this.size = EntityDimensions.scalable(w, h);
        refreshDimensions();
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean hurt(DamageSource src, float amount) {
        return !isInvulnerableTo(src) && getParent().hurtFromPart(this, src, amount);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        return getParent().interactFromPart(player, hand);
    }

    @Override
    public boolean is(Entity e) {
        return this == e || getParent() == e;
    }

    @Override
    public ItemStack getPickResult() {
        return getParent().getPickResult();
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return size;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        throw new UnsupportedOperationException();
    }
}
