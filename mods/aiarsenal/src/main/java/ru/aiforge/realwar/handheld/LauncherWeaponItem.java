package ru.aiforge.realwar.handheld;

import java.util.List;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.registries.ForgeRegistries;
import ru.aiforge.realwar.ordnance.AirTargets;
import ru.aiforge.realwar.registry.ModSounds;

/**
 * Shoulder-fired launcher.
 * RPG-7: right click fires when loaded; otherwise holds a 2.2 s reload that consumes one PG-7VL.
 * Igla / Javelin: hold right click with the target in the seeker cone until the lock completes (tone), release to fire.
 * Lock state lives in the item NBT ("Lock" 0..100, "Tgt" entity id, "Pt*" point) so the HUD can show it.
 */
public class LauncherWeaponItem extends Item {
    public final HandheldKind kind;
    private static final int RPG_RELOAD = 44;

    public LauncherWeaponItem(HandheldKind kind, Properties p) {
        super(p.stacksTo(1));
        this.kind = kind;
    }

    public Item ammo() {
        return ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation("aiarsenal", this.kind.ammo));
    }

    public static boolean loaded(ItemStack s) {
        return s.hasTag() && s.getTag().getBoolean("Loaded");
    }

    public static int lock(ItemStack s) {
        return s.hasTag() ? s.getTag().getInt("Lock") : 0;
    }

    public static int target(ItemStack s) {
        return s.hasTag() && s.getTag().contains("Tgt") ? s.getTag().getInt("Tgt") : -1;
    }

    private boolean hasAmmo(Player p) {
        if (p.getAbilities().instabuild) return true;
        Item a = this.ammo();
        for (ItemStack s : p.getInventory().items) if (s.is(a)) return true;
        return p.getOffhandItem().is(a);
    }

    private void takeAmmo(Player p) {
        if (p.getAbilities().instabuild) return;
        Item a = this.ammo();
        if (p.getOffhandItem().is(a)) {
            p.getOffhandItem().shrink(1);
            return;
        }
        for (ItemStack s : p.getInventory().items) {
            if (s.is(a)) {
                s.shrink(1);
                return;
            }
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack st = p.getItemInHand(hand);
        if (p.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.fail(st);
        if (this.kind == HandheldKind.RPG7) {
            if (loaded(st)) {
                if (!level.isClientSide) this.fire(level, p, st, -1, null);
                st.getOrCreateTag().putBoolean("Loaded", false);
                p.getCooldowns().addCooldown(this, 12);
                return InteractionResultHolder.consume(st);
            }
        }
        if (!this.hasAmmo(p)) {
            if (!level.isClientSide) p.displayClientMessage(Component.translatable("msg.aiarsenal.hh.no_ammo", Component.translatable("item.aiarsenal." + this.kind.ammo)), true);
            return InteractionResultHolder.fail(st);
        }
        CompoundTag t = st.getOrCreateTag();
        t.putInt("Lock", 0);
        t.remove("Tgt");
        p.startUsingItem(hand);
        return InteractionResultHolder.consume(st);
    }

    @Override
    public int getUseDuration(ItemStack st) {
        return this.kind == HandheldKind.RPG7 ? RPG_RELOAD : 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack st) {
        return UseAnim.NONE;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack st, Level level, LivingEntity e) {
        if (this.kind == HandheldKind.RPG7 && e instanceof Player p) {
            if (!level.isClientSide && this.hasAmmo(p)) {
                this.takeAmmo(p);
                st.getOrCreateTag().putBoolean("Loaded", true);
                level.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.RELOAD_CLANK.get(), SoundSource.PLAYERS, 0.8f, 1.3f);
            }
        }
        return st;
    }

    @Override
    public void onUseTick(Level level, LivingEntity e, ItemStack st, int remaining) {
        if (!this.kind.guided() || !(e instanceof Player p)) return;
        CompoundTag t = st.getOrCreateTag();
        int lk = t.getInt("Lock");
        if (level.isClientSide) {
            // seeker tone: slow growl while searching, fast while locking, steady when locked
            int used = this.getUseDuration(st) - remaining;
            int prog = lk;
            int period = prog >= 100 ? 3 : (prog > 0 ? 6 : 14);
            if (used % period == 0) {
                level.playLocalSound(p.getX(), p.getY(), p.getZ(), ModSounds.LOCK_TONE.get(), SoundSource.PLAYERS, 0.35f,
                        prog >= 100 ? 1.6f : 0.8f + prog / 200f, false);
            }
            return;
        }
        Seek s = this.seek(level, p);
        int prev = t.contains("Tgt") ? t.getInt("Tgt") : Integer.MIN_VALUE;
        int id = s == null ? Integer.MIN_VALUE : (s.entity != null ? s.entity.getId() : -1);
        if (s == null) {
            lk = Math.max(0, lk - 12);
            if (lk == 0) t.remove("Tgt");
        } else {
            if (id != prev) lk = 0;
            lk = Math.min(100, lk + Math.max(1, 100 / this.kind.lockTicks));
            t.putInt("Tgt", id);
            t.putDouble("PtX", s.pos.x);
            t.putDouble("PtY", s.pos.y);
            t.putDouble("PtZ", s.pos.z);
            if (lk >= 100 && t.getInt("Lock") < 100) {
                level.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.LOCK_DONE.get(), SoundSource.PLAYERS, 0.8f, 1.2f);
            }
        }
        if (lk != t.getInt("Lock")) t.putInt("Lock", lk);
    }

    @Override
    public void releaseUsing(ItemStack st, Level level, LivingEntity e, int remaining) {
        if (!this.kind.guided() || !(e instanceof Player p)) return;
        CompoundTag t = st.getOrCreateTag();
        if (t.getInt("Lock") >= 100 && !level.isClientSide && this.hasAmmo(p)) {
            int id = t.contains("Tgt") ? t.getInt("Tgt") : -1;
            Vec3 pt = new Vec3(t.getDouble("PtX"), t.getDouble("PtY"), t.getDouble("PtZ"));
            this.fire(level, p, st, id, pt);
            this.takeAmmo(p);
            p.getCooldowns().addCooldown(this, 30);
        }
        t.putInt("Lock", 0);
        t.remove("Tgt");
    }

    public record Seek(@Nullable Entity entity, Vec3 pos) {
    }

    /** Best target in the seeker cone: Igla = air targets; Javelin = vehicles/mobs, else the aimed block (point lock). */
    @Nullable
    public Seek seek(Level level, Player p) {
        return seek(level, p, this.kind);
    }

    /** r25: shared with the Igla-S / Stinger firearms */
    @Nullable
    public static Seek seek(Level level, Player p, HandheldKind kind) {
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        double cos = Math.cos(Math.toRadians(kind.cone));
        double r = kind.range;
        Entity best = null;
        double bestDot = cos;
        AABB box = new AABB(eye, eye.add(look.scale(r))).inflate(r * Math.sin(Math.toRadians(kind.cone)) + 8);
        for (Entity e : level.getEntities(p, box, x -> x.isAlive() && x != p.getVehicle())) {
            boolean ok = kind.air() ? AirTargets.isAirTarget(e) || airborne(e) : ground(e);
            if (!ok) continue;
            Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
            double d = to.length();
            if (d < 6 || d > r) continue;
            double dot = to.scale(1 / d).dot(look);
            if (dot <= bestDot) continue;
            if (level.clip(new ClipContext(eye, e.getBoundingBox().getCenter(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p)).getType() == HitResult.Type.BLOCK) continue;
            best = e;
            bestDot = dot;
        }
        if (best != null) return new Seek(best, best.getBoundingBox().getCenter());
        if (kind == HandheldKind.JAVELIN) {
            BlockHitResult bh = level.clip(new ClipContext(eye, eye.add(look.scale(r)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
            if (bh.getType() == HitResult.Type.BLOCK && bh.getLocation().distanceTo(eye) > 20) return new Seek(null, bh.getLocation());
        }
        return null;
    }

    private static boolean airborne(Entity e) {
        if (e instanceof ru.aiforge.arsenal.entity.FpvDrone || e instanceof ru.aiforge.arsenal.entity.ReconDrone || e instanceof ru.aiforge.arsenal.entity.ShahedDrone
                || e instanceof ru.aiforge.arsenal.entity.MissileEntity || e instanceof ru.aiforge.tubombers.entity.BomberEntity) return !e.onGround();
        if (e instanceof net.minecraft.world.entity.monster.Phantom || e instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon) return true;
        return e instanceof net.minecraft.world.entity.animal.FlyingAnimal && !e.onGround();
    }

    private static boolean ground(Entity e) {
        if (e instanceof ru.aiforge.realwar.vehicle.VehicleEntity || e instanceof ru.aiforge.arsenal.entity.LauncherEntity) return true;
        if (e instanceof net.minecraft.world.entity.vehicle.AbstractMinecart || e instanceof net.minecraft.world.entity.vehicle.Boat) return true;
        return e instanceof LivingEntity le && le.getBbWidth() * le.getBbHeight() > 1.2 && !(e instanceof Player);
    }

    private void fire(Level level, Player p, ItemStack st, int targetId, @Nullable Vec3 point) {
        Vec3 look = p.getLookAngle();
        Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 pos = p.getEyePosition().add(look.scale(1.1)).add(right.scale(0.25)).add(0, -0.12, 0);
        AtMissileEntity.launch(level, p, this.kind, pos, look, targetId, point);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), this.kind == HandheldKind.IGLA ? ModSounds.SAM_LAUNCH.get() : ModSounds.ROCKET_LAUNCH_SMALL.get(),
                SoundSource.PLAYERS, 2.0f, this.kind == HandheldKind.JAVELIN ? 1.3f : 1.0f);
        backblast(level, p, look);
        p.setXRot(p.getXRot() - (this.kind == HandheldKind.RPG7 ? 4.0f : 2.0f));
    }

    /** Danger zone behind the tube: RPG-7 ~ 6 m (burns), Igla ~ 4 m, Javelin (soft launch) ~ 2 m. */
    private void backblast(Level level, Player p, Vec3 look) {
        double len = this.kind == HandheldKind.RPG7 ? 6.0 : (this.kind == HandheldKind.IGLA ? 4.0 : 2.0);
        Vec3 back = p.getEyePosition().subtract(look.scale(0.4));
        if (level instanceof ServerLevel sl) {
            for (int i = 0; i < 14; i++) {
                double f = 0.5 + i / 14.0 * len;
                Vec3 q = back.subtract(look.scale(f));
                sl.sendParticles(i < 4 ? net.minecraft.core.particles.ParticleTypes.FLAME : ru.aiforge.arsenal.volumetric.VolumetricParticles.SMOKE.get(),
                        q.x, q.y, q.z, 1, 0.15 * f / len, 0.15 * f / len, 0.15 * f / len, 0.01);
            }
            for (Entity e : level.getEntities(p, new AABB(back, back).inflate(len))) {
                if (!(e instanceof LivingEntity le)) continue;
                Vec3 to = e.getBoundingBox().getCenter().subtract(back);
                double d = to.length();
                if (d > len || d < 1e-3 || to.scale(1 / d).dot(look.reverse()) < 0.75) continue;
                le.hurt(level.damageSources().inFire(), (float) (6.0 * (1 - d / len) + 1));
                if (this.kind == HandheldKind.RPG7) le.setSecondsOnFire(2);
            }
        }
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack a, ItemStack b, boolean slotChanged) {
        return slotChanged || !ItemStack.isSameItem(a, b);
    }

    @Override
    public void appendHoverText(ItemStack st, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("tooltip.aiarsenal." + this.kind.id).withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.aiarsenal.hh.ammo", Component.translatable("item.aiarsenal." + this.kind.ammo)).withStyle(ChatFormatting.DARK_GRAY));
        if (this.kind == HandheldKind.RPG7) {
            tip.add(Component.translatable(loaded(st) ? "tooltip.aiarsenal.hh.loaded" : "tooltip.aiarsenal.hh.empty").withStyle(loaded(st) ? ChatFormatting.GOLD : ChatFormatting.RED));
        }
        tip.add(Component.translatable("tooltip.aiarsenal.hh.use_" + (this.kind.guided() ? "guided" : "rpg")).withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> c) {
        c.accept(new IClientItemExtensions() {
            @Override
            public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return ru.aiforge.realwar.client.render.GlbItemRenderer.instance();
            }

            @Override
            public net.minecraft.client.model.HumanoidModel.ArmPose getArmPose(LivingEntity e, InteractionHand hand, ItemStack st) {
                return net.minecraft.client.model.HumanoidModel.ArmPose.CROSSBOW_HOLD;
            }
        });
    }
}
