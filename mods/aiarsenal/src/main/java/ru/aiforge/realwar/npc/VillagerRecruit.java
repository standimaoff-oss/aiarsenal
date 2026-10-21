package ru.aiforge.realwar.npc;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.aiforge.realwar.handheld.FirearmItem;

/**
 * The player's own faction: dress the villagers.
 *
 * <p>Right-click a villager holding emeralds: for <b>3 emeralds</b> he takes the king's shilling — the villager
 * becomes a soldier of the player's side in a modern uniform, with the profession you selected.
 * Sneak + right-click (with an emerald in hand) cycles the profession on the action bar without paying.
 * If your off hand holds a firearm he takes <em>that</em> weapon instead of the standard issue rifle;
 * if it holds a uniform piece he wears that piece. Recruits never shoot their employer, follow him,
 * and fight every AI army.</p>
 */
@Mod.EventBusSubscriber(modid = "aiarsenal", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VillagerRecruit {
    public static final int PRICE = 3;
    private static final Map<UUID, Profession> SELECTED = new WeakHashMap<>();

    private VillagerRecruit() {
    }

    public static Profession selected(ServerPlayer p) {
        return SELECTED.getOrDefault(p.getUUID(), Profession.ASSAULT);
    }

    @SubscribeEvent
    public static void interact(PlayerInteractEvent.EntityInteract e) {
        if (e.getLevel().isClientSide || !(e.getTarget() instanceof Villager villager)) return;
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        ItemStack hand = p.getItemInHand(e.getHand());
        if (!hand.is(Items.EMERALD)) return;
        e.setCanceled(true);

        if (p.isShiftKeyDown()) {                       // choose the trade, free of charge
            Profession[] all = Profession.values();
            Profession next = all[(selected(p).ordinal() + 1) % all.length];
            SELECTED.put(p.getUUID(), next);
            p.displayClientMessage(Component.translatable("msg.aiarsenal.recruit.profession",
                    Component.translatable(next.langKey())), true);
            return;
        }
        if (hand.getCount() < PRICE && !p.getAbilities().instabuild) {
            p.displayClientMessage(Component.translatable("msg.aiarsenal.recruit.price", PRICE), true);
            return;
        }
        Profession prof = selected(p);
        SoldierEntity s = SoldierSpawner.spawnOne((net.minecraft.server.level.ServerLevel) e.getLevel(),
                villager.position(), Faction.PLAYER, Era.MODERN, prof.rank);
        if (s == null) return;
        if (!p.getAbilities().instabuild) hand.shrink(PRICE);
        s.setProfession(prof);
        s.setOwner(p.getUUID());
        s.setSquad(Math.abs(p.getUUID().hashCode() % 90000) + 1000, -1, p.position());

        ItemStack off = p.getOffhandItem();
        if (off.getItem() instanceof FirearmItem fa) {          // any weapon you hand him
            ItemStack gun = off.copy();
            gun.setCount(1);
            FirearmItem.setAmmo(gun, fa.kind.cap);
            s.setItemSlot(EquipmentSlot.MAINHAND, gun);
        } else if (off.getItem() instanceof ru.aiforge.realwar.item.UniformItem) {   // any uniform piece you hand him
            ItemStack piece = off.copy();
            piece.setCount(1);
            for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                    EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                if (piece.getItem().canEquip(piece, slot, s)) {
                    s.setItemSlot(slot, piece);
                    break;
                }
            }
        }
        villager.discard();
        e.getLevel().playSound(null, s.getX(), s.getY(), s.getZ(),
                SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 0.7f, 1.2f);
        p.displayClientMessage(Component.translatable("msg.aiarsenal.recruit.done",
                Component.translatable(prof.langKey())), true);
    }
}
