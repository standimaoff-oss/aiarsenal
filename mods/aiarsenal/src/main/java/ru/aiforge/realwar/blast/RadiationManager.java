/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.nbt.CompoundTag
 *  net.minecraft.nbt.ListTag
 *  net.minecraft.nbt.Tag
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.server.level.ServerPlayer
 *  net.minecraft.world.effect.MobEffect
 *  net.minecraft.world.effect.MobEffectInstance
 *  net.minecraft.world.entity.EquipmentSlot
 *  net.minecraft.world.entity.player.Player
 *  net.minecraft.world.item.Item
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.level.levelgen.Heightmap$Types
 *  net.minecraft.world.level.saveddata.SavedData
 *  net.minecraft.world.phys.Vec3
 *  net.minecraftforge.event.TickEvent$Phase
 *  net.minecraftforge.event.TickEvent$PlayerTickEvent
 *  net.minecraftforge.event.entity.player.PlayerEvent$Clone
 *  net.minecraftforge.eventbus.api.SubscribeEvent
 *  net.minecraftforge.fml.LogicalSide
 *  net.minecraftforge.fml.common.Mod$EventBusSubscriber
 */
package ru.aiforge.realwar.blast;

import ru.aiforge.realwar.RWConfig;
import ru.aiforge.realwar.blast.BlastPhysics;
import ru.aiforge.realwar.blast.ModDamage;
import ru.aiforge.realwar.item.GeigerCounterItem;
import ru.aiforge.realwar.item.HazmatArmorItem;
import ru.aiforge.realwar.item.TooltipItem;
import ru.aiforge.realwar.network.ModNetwork;
import ru.aiforge.realwar.network.RadiationSyncPacket;
import ru.aiforge.realwar.registry.ModEffects;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="aiarsenal")
public final class RadiationManager {
    public static final String DOSE_KEY = "aiarsenal_rw_dose";

    private RadiationManager() {
    }

    public static PlumeData data(ServerLevel level) {
        return (PlumeData)level.getDataStorage().computeIfAbsent(PlumeData::load, PlumeData::new, "aiarsenal_rw_plumes");
    }

    public static double windAngle(ServerLevel level) {
        long day = level.getDayTime() / 24000L;
        long h = level.getSeed() ^ day * -7046029254386353131L;
        h ^= h >>> 33;
        h *= -49064778989728563L;
        h ^= h >>> 33;
        h *= -4265267296055464877L;
        h ^= h >>> 33;
        return (double)(h >>> 11) / 9.007199254740992E15 * Math.PI * 2.0;
    }

    public static void addPlume(ServerLevel level, Vec3 burst, double yieldKt, boolean surface, double mpb) {
        double scale = (Double)RWConfig.COMMON.falloutLengthScale.get();
        double angle = RadiationManager.windAngle(level);
        double dirX = Math.cos(angle);
        double dirZ = Math.sin(angle);
        double fireballBlocks = BlastPhysics.fireballRadiusM(yieldKt) / mpb;
        double r0 = Math.max(8.0, fireballBlocks * 1.6);
        double length = surface ? 8000.0 * Math.pow(yieldKt, 0.45) / mpb * scale : 0.0;
        double i1 = surface ? 4000.0 : 0.0;
        double hot = surface ? 3000.0 : 150.0;
        Plume p = new Plume(burst.x, burst.z, dirX, dirZ, length, r0 * 0.6, i1, hot, r0, level.getGameTime());
        PlumeData d = RadiationManager.data(level);
        d.plumes.add(p);
        d.setDirty();
    }

    private static double hourTicks() {
        return ((Integer)RWConfig.COMMON.radiationHourTicks.get()).intValue();
    }

    public static double sampleRate(ServerLevel level, double x, double z) {
        PlumeData d = RadiationManager.data(level);
        if (d.plumes.isEmpty()) {
            return 0.0;
        }
        long now = level.getGameTime();
        double hour = RadiationManager.hourTicks();
        double sum = 0.0;
        boolean pruned = false;
        Iterator<Plume> it = d.plumes.iterator();
        while (it.hasNext()) {
            Plume p = it.next();
            double tH = Math.max(0.25, (double)(now - p.start) / hour);
            double decay = Math.pow(tH, -1.2);
            if (p.peak() * decay < 0.02) {
                it.remove();
                pruned = true;
                continue;
            }
            double dx = x - p.x;
            double dz = z - p.z;
            double r2 = dx * dx + dz * dz;
            double rate = p.hot * Math.exp(-r2 / (p.r0 * p.r0));
            if (p.length > 0.0) {
                double u = dx * p.dirX + dz * p.dirZ;
                double v = -dx * p.dirZ + dz * p.dirX;
                if (u > -p.r0 * 0.5 && u < p.length * 1.15) {
                    double uu = Math.max(u, 0.0);
                    double w = p.sigma0 + 0.11 * uu;
                    double along = Math.exp(-uu / (0.3 * p.length));
                    rate += p.i1 * along * Math.exp(-(v * v) / (2.0 * w * w));
                }
            }
            sum += rate * decay;
        }
        if (pruned) {
            d.setDirty();
        }
        return sum;
    }

    public static double shelterFactor(ServerLevel level, Player p) {
        int z;
        int x = p.getBlockX();
        int ceiling = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z = p.getBlockZ());
        int cover = Math.max(0, ceiling - (p.getBlockY() + 2));
        if (cover <= 0) {
            return 1.0;
        }
        return Math.max(0.03, Math.pow(0.6, cover));
    }

    public static double protectionFactor(Player p) {
        int n = 0;
        for (EquipmentSlot s : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            if (!(p.getItemBySlot(s).getItem() instanceof HazmatArmorItem)) continue;
            ++n;
        }
        return switch (n) {
            case 0 -> 1.0;
            case 1 -> 0.8;
            case 2 -> 0.6;
            case 3 -> 0.35;
            default -> 0.06;
        };
    }

    public static double itemRate(Player p) {
        if (!((Boolean)RWConfig.COMMON.radioactiveItems.get()).booleanValue()) {
            return 0.0;
        }
        double sum = 0.0;
        for (ItemStack st : p.getInventory().items) {
            sum += RadiationManager.stackRate(st);
        }
        for (ItemStack st : p.getInventory().offhand) {
            sum += RadiationManager.stackRate(st);
        }
        return sum;
    }

    private static double stackRate(ItemStack st) {
        TooltipItem ti;
        Item item;
        if (st.isEmpty() || !((item = st.getItem()) instanceof TooltipItem) || (ti = (TooltipItem)item).radioactivity() <= 0.0) {
            return 0.0;
        }
        return ti.radioactivity() * (double)Math.min(st.getCount(), 16);
    }

    public static double getDose(Player p) {
        return p.getPersistentData().getDouble(DOSE_KEY);
    }

    public static void setDose(Player p, double v) {
        p.getPersistentData().putDouble(DOSE_KEY, Math.max(0.0, v));
    }

    public static void addDose(Player p, double rem) {
        RadiationManager.setDose(p, RadiationManager.getDose(p) + rem);
    }

    private static boolean hasGeiger(Player p) {
        for (ItemStack st : p.getInventory().items) {
            if (!(st.getItem() instanceof GeigerCounterItem)) continue;
            return true;
        }
        return p.getOffhandItem().getItem() instanceof GeigerCounterItem;
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        Player player;
        if (e.phase != TickEvent.Phase.END || e.side != LogicalSide.SERVER || !((player = e.player) instanceof ServerPlayer)) {
            return;
        }
        ServerPlayer p = (ServerPlayer)player;
        if (p.tickCount % 20 != 0) {
            return;
        }
        if (!((Boolean)RWConfig.COMMON.radiationEnabled.get()).booleanValue()) {
            return;
        }
        ServerLevel level = p.serverLevel();
        double ambient = RadiationManager.sampleRate(level, p.getX(), p.getZ()) * RadiationManager.shelterFactor(level, (Player)p);
        double items = RadiationManager.itemRate((Player)p);
        double raw = ambient + items;
        double absorbed = raw * RadiationManager.protectionFactor((Player)p);
        double dose = RadiationManager.getDose((Player)p);
        double perSecond = 20.0 / RadiationManager.hourTicks();
        if (absorbed > 0.0) {
            dose += absorbed * perSecond;
        }
        dose = Math.max(0.0, dose - 3.0 * perSecond);
        RadiationManager.setDose((Player)p, dose);
        RadiationManager.applySickness(p, dose);
        if (RadiationManager.hasGeiger((Player)p)) {
            ModNetwork.sendToPlayer(p, new RadiationSyncPacket((float)raw, (float)dose));
        }
    }

    private static void applySickness(ServerPlayer p, double dose) {
        int amp;
        if (dose >= 900.0) {
            p.hurt(ModDamage.source(p.level(), ModDamage.RADIATION), 1000.0f);
            return;
        }
        if (dose >= 500.0) {
            amp = 3;
        } else if (dose >= 250.0) {
            amp = 2;
        } else if (dose >= 100.0) {
            amp = 1;
        } else if (dose >= 25.0) {
            amp = 0;
        } else {
            return;
        }
        MobEffectInstance cur = p.getEffect((MobEffect)ModEffects.RADIATION_SICKNESS.get());
        if (cur == null || cur.getAmplifier() < amp || cur.getDuration() < 120) {
            p.addEffect(new MobEffectInstance((MobEffect)ModEffects.RADIATION_SICKNESS.get(), 400, amp, false, false, true));
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone e) {
        if (!e.isWasDeath()) {
            RadiationManager.setDose(e.getEntity(), RadiationManager.getDose(e.getOriginal()));
        }
    }

    public static final class PlumeData
    extends SavedData {
        public final List<Plume> plumes = new ArrayList<Plume>();

        public static PlumeData load(CompoundTag tag) {
            PlumeData d = new PlumeData();
            ListTag l = tag.getList("plumes", 10);
            for (int i = 0; i < l.size(); ++i) {
                d.plumes.add(Plume.load(l.getCompound(i)));
            }
            return d;
        }

        public CompoundTag save(CompoundTag tag) {
            ListTag l = new ListTag();
            for (Plume p : this.plumes) {
                l.add(p.save());
            }
            tag.put("plumes", (Tag)l);
            return tag;
        }
    }

    public static final class Plume {
        public final double x;
        public final double z;
        public final double dirX;
        public final double dirZ;
        public final double length;
        public final double sigma0;
        public final double i1;
        public final double hot;
        public final double r0;
        public final long start;

        public Plume(double x, double z, double dirX, double dirZ, double length, double sigma0, double i1, double hot, double r0, long start) {
            this.x = x;
            this.z = z;
            this.dirX = dirX;
            this.dirZ = dirZ;
            this.length = length;
            this.sigma0 = sigma0;
            this.i1 = i1;
            this.hot = hot;
            this.r0 = r0;
            this.start = start;
        }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putDouble("x", this.x);
            t.putDouble("z", this.z);
            t.putDouble("dx", this.dirX);
            t.putDouble("dz", this.dirZ);
            t.putDouble("len", this.length);
            t.putDouble("s0", this.sigma0);
            t.putDouble("i1", this.i1);
            t.putDouble("hot", this.hot);
            t.putDouble("r0", this.r0);
            t.putLong("t0", this.start);
            return t;
        }

        static Plume load(CompoundTag t) {
            return new Plume(t.getDouble("x"), t.getDouble("z"), t.getDouble("dx"), t.getDouble("dz"), t.getDouble("len"), t.getDouble("s0"), t.getDouble("i1"), t.getDouble("hot"), t.getDouble("r0"), t.getLong("t0"));
        }

        double peak() {
            return Math.max(this.i1, this.hot);
        }
    }
}

