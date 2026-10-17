package ru.aiforge.realwar.missile;

import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Temporary chunk leases must outlive a paused projectile tick. All callbacks
 * run on the server thread; no worker thread, synchronous generation or permanent
 * forced-chunk entry. Removed entities/worlds release their references. */
@Mod.EventBusSubscriber(modid="aiarsenal")
public final class MissileChunkLeases {
    private static final Map<ServerLevel,Set<MissileEntity>> LIVE=new IdentityHashMap<>();
    private static final Map<ServerLevel,Long> LAST_RENEWAL=new IdentityHashMap<>();
    private MissileChunkLeases() {}
    @SubscribeEvent public static void joined(EntityJoinLevelEvent e) {
        if(e.getLevel() instanceof ServerLevel level && e.getEntity() instanceof MissileEntity missile)
            LIVE.computeIfAbsent(level,k->Collections.newSetFromMap(new IdentityHashMap<>())).add(missile);
    }
    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent e) {
        if(e.phase!=TickEvent.Phase.END || !(e.level instanceof ServerLevel level) || level.getGameTime()%10!=0)return;
        Set<MissileEntity> missiles=LIVE.get(level);if(missiles==null)return;
        missiles.removeIf(net.minecraft.world.entity.Entity::isRemoved);
        for(var missile:missiles)missile.renewChunkLease(level);
        LAST_RENEWAL.put(level,level.getGameTime());
        if(missiles.isEmpty())LIVE.remove(level);
    }
    @SubscribeEvent public static void unloaded(LevelEvent.Unload e) {
        if(e.getLevel() instanceof ServerLevel level) {LIVE.remove(level);LAST_RENEWAL.remove(level);}
    }
    @SubscribeEvent public static void stopped(ServerStoppingEvent e) {LIVE.clear();LAST_RENEWAL.clear();}

    /** Test diagnostics only; never changes tickets or entity visibility. */
    public static String diagnostics(MissileEntity missile) {
        if (!(missile.level() instanceof ServerLevel level)) return "client";
        return "tracked="+LIVE.getOrDefault(level,Collections.emptySet()).contains(missile)
                +" clock="+level.getGameTime()+" renewal="+LAST_RENEWAL.get(level)
                +" present="+(level.getEntity(missile.getUUID())==missile)
                +" ticking="+level.isPositionEntityTicking(missile.blockPosition())
                +" chunk="+level.hasChunk(missile.chunkPosition().x,missile.chunkPosition().z);
    }
}
