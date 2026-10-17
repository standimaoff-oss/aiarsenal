package ru.aiforge.arsenal.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.PacketDistributor;
import ru.aiforge.arsenal.AiArsenal;
import ru.aiforge.arsenal.entity.IcbmEntity;
import ru.aiforge.arsenal.entity.NukeEntity;
import ru.aiforge.arsenal.network.ArsenalNetwork;
import ru.aiforge.arsenal.network.ChargeListPacket;
import ru.aiforge.arsenal.network.DetonatorPacket;
import ru.aiforge.arsenal.nuke.ChargeRegistry;
import ru.aiforge.arsenal.nuke.NuclearBlast;
import ru.aiforge.arsenal.registry.ArsenalRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * Remote detonator (coded radio command link): lists every armed nuclear charge of the player (operators see all),
 * detonates one or all at any distance, switches fuzes. Works for charges in unloaded chunks and for ICBMs in flight.
 */
public class DetonatorItem extends Item {
    public DetonatorItem(Properties p) {
        super(p);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack s = player.getItemInHand(hand);
        if (!level.isClientSide) send((ServerPlayer) player, true);
        return InteractionResultHolder.sidedSuccess(s, level.isClientSide);
    }

    static boolean mine(ServerPlayer p, ChargeRegistry.Charge c) {
        return c.owner == null || c.owner.equals(p.getUUID()) || p.hasPermissions(2);
    }

    public static void send(ServerPlayer p, boolean open) {
        List<ChargeListPacket.Entry> l = new ArrayList<>();
        for (ChargeRegistry.Charge c : ChargeRegistry.get(p.server).charges)
            if (mine(p, c))
                l.add(new ChargeListPacket.Entry(c.id, c.kind.ordinal(), c.fuze, c.status, (int) c.pos.x, (int) c.pos.y, (int) c.pos.z,
                        c.dim.location().toString()));
        ArsenalNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new ChargeListPacket(l, open));
    }

    public static void action(ServerPlayer p, int action, int id) {
        ChargeRegistry reg = ChargeRegistry.get(p.server);
        switch (action) {
            case DetonatorPacket.DETONATE -> {
                ChargeRegistry.Charge c = reg.byId(id);
                if (c != null && mine(p, c)) detonate(p, c);
            }
            case DetonatorPacket.ALL -> {
                for (ChargeRegistry.Charge c : reg.charges.toArray(new ChargeRegistry.Charge[0])) if (mine(p, c)) detonate(p, c);
            }
            case DetonatorPacket.FUZE -> {
                ChargeRegistry.Charge c = reg.byId(id);
                if (c != null && mine(p, c)) {
                    c.fuze = (c.fuze + 1) % 3;
                    reg.setDirty();
                }
            }
            default -> {
            }
        }
        send(p, false);
    }

    /** Detonates a registered charge wherever it is (entity loaded or not, virtual flight). */
    public static boolean detonate(ServerPlayer p, ChargeRegistry.Charge c) {
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), ArsenalRegistry.SND_DETONATOR.get(), SoundSource.PLAYERS, 1f, 1f);
        AiArsenal.LOGGER.info("Remote detonation #{} {} by {} at {}", c.id, c.kind.id, p.getGameProfile().getName(), c.pos);
        return detonate(p.server, c, p);
    }

    public static boolean detonate(net.minecraft.server.MinecraftServer server, ChargeRegistry.Charge c, @javax.annotation.Nullable Entity by) {
        ChargeRegistry reg = ChargeRegistry.get(server);
        ServerLevel sl = server.getLevel(c.dim);
        if (sl == null) return false;
        Entity e = c.entity == null ? null : sl.getEntity(c.entity);
        if (e instanceof NukeEntity n) {
            n.detonate(sl);
            return true;
        }
        if (e instanceof IcbmEntity i) {
            i.detonate(sl);
            return true;
        }
        // entity not loaded (far away) or virtual flight: detonate at the last known position; the entity removes itself
        // when its chunk loads because its registry entry is gone
        reg.remove(c);
        NuclearBlast.detonate(sl, c.pos, c.kind, null, by);
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("tooltip.aiarsenal.detonator").withStyle(ChatFormatting.GRAY));
    }
}
