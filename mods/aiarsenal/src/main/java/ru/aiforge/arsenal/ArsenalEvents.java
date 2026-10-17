package ru.aiforge.arsenal;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.aiforge.arsenal.command.ArsenalCommand;
import ru.aiforge.arsenal.control.RemoteControl;
import ru.aiforge.arsenal.core.Tickets;
import ru.aiforge.arsenal.entity.IcbmEntity;
import ru.aiforge.arsenal.nuke.ChargeRegistry;
import ru.aiforge.arsenal.nuke.NuclearBlast;

/** Forge-bus events: blast waves, virtual ICBM flights, remote-control sessions, commands. */
@Mod.EventBusSubscriber(modid = AiArsenal.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ArsenalEvents {
    private ArsenalEvents() {
    }

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        MinecraftServer server = e.getServer();
        NuclearBlast.tickAll(server);
        RemoteControl.tick(server);
        virtualFlights(server);
    }

    /** ICBMs above the atmosphere: advanced mathematically, the RV re-appears near the target. */
    private static void virtualFlights(MinecraftServer server) {
        ChargeRegistry reg = ChargeRegistry.get(server);
        for (ChargeRegistry.Charge c : reg.charges.toArray(new ChargeRegistry.Charge[0])) {
            if (c.status != 3) continue;
            c.time += 1;
            double s = IcbmEntity.progress(c.time, c.total);
            c.pos = IcbmEntity.path(c.launch, c.aim, c.apex, s);
            ServerLevel sl = server.getLevel(c.dim);
            if (sl == null) continue;
            double rs = IcbmEntity.resumeS(c.launch, c.aim);
            if (s >= rs - 0.04 && ((long) c.time) % 10 == 0) {
                Vec3 at = IcbmEntity.path(c.launch, c.aim, c.apex, rs);
                Tickets.keep(sl, at, 3);
                Tickets.keep(sl, c.aim, 3);
            }
            if (s >= rs) IcbmEntity.resume(sl, c);
            if (((long) c.time) % 20 == 0) reg.setDirty();
        }
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) RemoteControl.restore(p);
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && RemoteControl.inSession(p)) RemoteControl.stop(p, null);
    }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent e) {
        for (ServerPlayer p : e.getServer().getPlayerList().getPlayers())
            if (RemoteControl.inSession(p)) RemoteControl.stop(p, null);
        ru.aiforge.realwar.radar.TerrainOverview.shutdown();
    }

    /** Hitting the operator dummy pulls the operator out of the drone and passes the hit on. */
    @SubscribeEvent
    public static void dummyHit(LivingAttackEvent e) {
        if (e.getEntity().level().isClientSide || !RemoteControl.isDummy(e.getEntity())) return;
        e.setCanceled(true);
        ServerPlayer p = RemoteControl.ownerOfDummy(e.getEntity().getServer(), e.getEntity());
        if (p == null) {
            e.getEntity().discard();
            return;
        }
        RemoteControl.stop(p, "msg.aiarsenal.operator_hit");
        p.hurt(e.getSource(), e.getAmount());
    }

    @SubscribeEvent
    public static void joined(net.minecraftforge.event.entity.EntityJoinLevelEvent e) {
        if (!e.getLevel().isClientSide() && RemoteControl.isDummy(e.getEntity())
                && !RemoteControl.activeDummy(e.getEntity().getUUID())) {
            // restore() cannot find a dummy while its chunk is unloaded. Reject
            // the orphan when it loads, including after a server restart.
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void commands(RegisterCommandsEvent e) {
        ArsenalCommand.register(e.getDispatcher());
    }
}
