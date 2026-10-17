package ru.aiforge.arsenal.qa;

import com.google.gson.*;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.*;
import net.minecraft.commands.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.aiforge.realwar.blast.BlastManager;
import ru.aiforge.realwar.craft.*;
import ru.aiforge.realwar.missile.*;
import ru.aiforge.realwar.napoleonic.*;
import ru.aiforge.realwar.registry.ModS3;

/** Opt-in diagnostics in a disposable world. Preparation invokes real item use;
 * observations never write ammunition, loading timers, locks or flight progress.
 * Missile references survive chunk unload so an unloaded projectile cannot pass as
 * a completed impact. This is NOT registered on a normal player server. */
@Mod.EventBusSubscriber(modid="aiarsenal")
public final class ContextQa {
    private static final List<MissileEntity> missiles = new ArrayList<>();
    private static final Map<String,Integer> spawned = new TreeMap<>();
    private static boolean observing;
    private static LaunchPadBlockEntity pad;
    private static MissileType missileType;
    private static Vec3 target;
    private static CraftEntity craft;
    private static PowderCannonEntity cannon;
    private static long blocksBefore;
    private static int tasksBefore;
    private ContextQa() {}

    @SubscribeEvent public static void joined(EntityJoinLevelEvent e) {
        if (!Boolean.getBoolean("aiarsenal.stressQa") || !observing || e.getLevel().isClientSide()) return;
        Entity entity=e.getEntity();
        var id=BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (id.getNamespace().equals("aiarsenal")) spawned.merge(id.toString(),1,Integer::sum);
        if(entity instanceof MissileEntity m) missiles.add(m);
    }
    private static Item item(String id) {
        var key=new ResourceLocation(id);
        if(!BuiltInRegistries.ITEM.containsKey(key)) throw new IllegalArgumentException("Unknown item "+id);
        return BuiltInRegistries.ITEM.get(key);
    }
    private static void require(boolean ok,String reason) { if(!ok) throw new IllegalStateException(reason); }
    private static void reset() {
        require(missiles.stream().allMatch(Entity::isRemoved),"previous missiles still alive/unloaded");
        observing=true; missiles.clear(); spawned.clear();
        blocksBefore=BlastManager.totalBlocksChanged; tasksBefore=BlastManager.completedBlasts;
    }
    private static void say(CommandSourceStack source,JsonObject result) {
        source.sendSuccess(()->Component.literal(result.toString()),false);
    }
    private static JsonObject result(String action) {
        JsonObject o=new JsonObject(); o.addProperty("action",action); return o;
    }
    private static void hold(ServerPlayer p,Item i,int count) {
        p.getInventory().clearContent(); p.getInventory().selected=0;
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(i,count));
    }
    private static UseOnContext onTop(ServerPlayer p,BlockPos floor) {
        return new UseOnContext(p,InteractionHand.MAIN_HAND,new BlockHitResult(
            Vec3.atCenterOf(floor).add(0,.5,0),Direction.UP,floor,false));
    }
    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        if(!Boolean.getBoolean("aiarsenal.stressQa")) return;
        var root=Commands.literal("arsenal_context").requires(s->s.hasPermission(2));
        root.then(Commands.literal("inventory").executes(c->{
            JsonObject o=result("inventory"); JsonArray ms=new JsonArray(),cs=new JsonArray();
            for(var t:MissileType.values()) ms.add(t.itemName());
            for(var id:BuiltInRegistries.ITEM.keySet().stream().filter(i->i.getNamespace().equals("aiarsenal")).sorted().toList()) {
                if(BuiltInRegistries.ITEM.get(id) instanceof CraftItem ci) {
                    JsonObject row=new JsonObject();row.addProperty("id",id.toString());row.addProperty("air",ci.kind.isAir());
                    row.addProperty("sub",ci.kind.isSub());cs.add(row);
                }
            }
            o.add("missiles",ms);o.add("craft",cs);say(c.getSource(),o);return 1;
        }));
        root.then(Commands.literal("preparemissile").then(Commands.argument("id",StringArgumentType.greedyString()).executes(c->{
            var p=c.getSource().getPlayerOrException();var l=p.serverLevel();reset();
            require(!p.getAbilities().instabuild,"survival required for consumption checks");
            Item ammo=item("aiarsenal:"+StringArgumentType.getString(c,"id"));
            require(ammo instanceof MissileItem,"not missile ammunition");missileType=((MissileItem)ammo).type;
            BlockPos floor=BlockPos.containing(p.getX()+4,-61,p.getZ()+4);
            var block=switch(missileType.padLevel()){case 1->ModS3.PAD_1.get();case 2->ModS3.PAD_2.get();default->ModS3.PAD_3.get();};
            hold(p,block.asItem(),1);
            require(p.getMainHandItem().useOn(onTop(p,floor)).consumesAction(),"pad item placement failed");
            pad=(LaunchPadBlockEntity)l.getBlockEntity(floor.above());require(pad!=null,"missing pad block entity");
            require(p.getMainHandItem().isEmpty(),"pad item not consumed");
            target=new Vec3(floor.getX()+Math.max(250,missileType.cls.minRange+100),-60,floor.getZ()+.5);
            hold(p,ammo,2);pad.interact(p,InteractionHand.MAIN_HAND);
            require(p.getMainHandItem().getCount()==1,"missile not consumed exactly once");
            require(pad.state()==LaunchPadBlockEntity.State.LOADING,"loading did not start");
            JsonObject o=result("missile_loading");o.addProperty("id",missileType.itemName());o.addProperty("remainingAmmo",1);
            o.addProperty("targetX",target.x);o.addProperty("targetZ",target.z);say(c.getSource(),o);return 1;
        })));
        root.then(Commands.literal("abortmissiles").executes(c->{
            JsonObject o=result("failed_case_cleanup");int n=0;
            for(var m:missiles)if(!m.isRemoved()){m.discard();n++;}
            o.addProperty("aborted",n);say(c.getSource(),o);return 1;
        }));
        root.then(Commands.literal("launchmissile").executes(c->{
            require(pad!=null && pad.ready(),"pad not ready");
            require(pad.scheduleLaunch(target,0),"launch rejected");say(c.getSource(),result("launch_scheduled"));return 1;
        }));
        root.then(Commands.literal("missilestatus").executes(c->{
            JsonObject o=result("missile_status");o.addProperty("pad",pad==null?"NONE":pad.state().name());
            o.addProperty("spawned",missiles.size());o.addProperty("removed",missiles.stream().filter(Entity::isRemoved).count());
            o.addProperty("warheads",missiles.stream().filter(m->m.mode()==1).count());
            o.addProperty("activeBlasts",BlastManager.active(c.getSource().getLevel()));
            o.addProperty("completedTasks",BlastManager.completedBlasts-tasksBefore);
            o.addProperty("changedBlocks",BlastManager.totalBlocksChanged-blocksBefore);
            o.addProperty("expectedEntities",missileType==null?0:missileType.cls!=MissileType.Cls.CRUISE && missileType.warheads>1?1+missileType.warheads:1);
            JsonArray entities=new JsonArray();for(var m:missiles) {JsonObject e=new JsonObject();e.addProperty("uuid",m.getUUID().toString());e.addProperty("mode",m.mode());e.addProperty("age",m.tickCount);e.addProperty("removed",m.isRemoved());e.addProperty("x",m.getX());e.addProperty("y",m.getY());e.addProperty("z",m.getZ());e.addProperty("present",c.getSource().getLevel().getEntity(m.getUUID())==m);entities.add(e);}o.add("entities",entities);
            say(c.getSource(),o);return 1;
        }));
        root.then(Commands.literal("preparecraft").then(Commands.argument("id",StringArgumentType.greedyString()).executes(c->{
            var p=c.getSource().getPlayerOrException();var l=p.serverLevel();reset();
            require(!p.getAbilities().instabuild,"survival required");
            Item i=item(StringArgumentType.getString(c,"id"));require(i instanceof CraftItem,"not craft item");
            if(craft!=null) {p.stopRiding();craft.discard();}
            // Player looks straight down at the independently prepared runway/pool.
            p.setXRot(90);p.setYRot(0);hold(p,i,1);
            require(i.use(l,p,InteractionHand.MAIN_HAND).getResult().consumesAction(),"craft item use failed");
            require(p.getMainHandItem().isEmpty(),"craft item not consumed");
            var found=l.getEntitiesOfClass(CraftEntity.class,p.getBoundingBox().inflate(30),e->e.kind==((CraftItem)i).kind);
            require(found.size()==1,"expected one placed craft, got "+found.size());craft=found.get(0);
            p.setXRot(0);p.setYRot(0);
            craft.interact(p,InteractionHand.MAIN_HAND);
            require(p.getVehicle()==craft,"boarding interaction failed");
            p.connection.teleport(p.getX(),p.getY(),p.getZ(),0,0);
            say(c.getSource(),craftStatus(p));return 1;
        })));
        root.then(Commands.literal("craftstatus").executes(c->{say(c.getSource(),craftStatus(c.getSource().getPlayerOrException()));return 1;}));
        root.then(Commands.literal("servicecraft").executes(c->{
            var p=c.getSource().getPlayerOrException();require(craft!=null,"no craft");
            // Production interaction, not service() and not an ammunition NBT write.
            hold(p,item("aiarsenal:craft_service_kit"),2);craft.interact(p,InteractionHand.MAIN_HAND);
            require(p.getMainHandItem().getCount()==1,"service kit not consumed exactly once");
            JsonObject o=craftStatus(p);o.addProperty("kitsRemaining",1);say(c.getSource(),o);return 1;
        }));
        root.then(Commands.literal("preparecannon").then(Commands.argument("id",StringArgumentType.greedyString()).executes(c->{
            var p=c.getSource().getPlayerOrException();var l=p.serverLevel();reset();
            require(!p.getAbilities().instabuild,"survival required");
            if(cannon!=null)cannon.discard();Item i=item(StringArgumentType.getString(c,"id"));require(i instanceof CannonItem,"not cannon");
            hold(p,i,1);p.setYRot(0);p.setXRot(0);BlockPos floor=BlockPos.containing(p.getX()+4,-61,p.getZ()+4);
            require(i.useOn(onTop(p,floor)).consumesAction(),"cannon placement failed");
            require(p.getMainHandItem().isEmpty(),"cannon not consumed");
            var found=l.getEntitiesOfClass(PowderCannonEntity.class,new AABB(floor).inflate(4));
            require(found.size()==1,"expected one cannon");cannon=found.get(0);
            say(c.getSource(),cannonStatus());return 1;
        })));
        root.then(Commands.literal("loadcannon").executes(c->{
            var p=c.getSource().getPlayerOrException();require(cannon!=null && cannon.loading()==-1,"cannon not empty");
            hold(p,cannon.charge(),2);cannon.interact(p,InteractionHand.MAIN_HAND);
            require(p.getMainHandItem().getCount()==1 && cannon.loading()>0,"charge/load failed");
            say(c.getSource(),cannonStatus());return 1;
        }));
        root.then(Commands.literal("firecannon").executes(c->{
            var p=c.getSource().getPlayerOrException();require(cannon!=null && cannon.loading()==0,"cannon not ready");
            hold(p,Items.FLINT_AND_STEEL,1);cannon.interact(p,InteractionHand.MAIN_HAND);
            require(cannon.loading()==-1 && p.getMainHandItem().getDamageValue()==1,"ignition did not fire/wear tool");
            say(c.getSource(),cannonStatus());return 1;
        }));
        root.then(Commands.literal("cannonstatus").executes(c->{say(c.getSource(),cannonStatus());return 1;}));
        event.getDispatcher().register(root);
    }
    private static JsonObject cannonStatus() {
        require(cannon!=null,"no cannon");JsonObject o=result("cannon_status");o.addProperty("loading",cannon.loading());
        o.addProperty("charge",BuiltInRegistries.ITEM.getKey(cannon.charge()).toString());
        o.addProperty("x",cannon.getX());o.addProperty("y",cannon.getY());o.addProperty("z",cannon.getZ());
        o.add("spawned",new Gson().toJsonTree(spawned));return o;
    }
    private static JsonObject craftStatus(ServerPlayer p) {
        require(craft!=null,"no craft");JsonObject o=result("craft_status");o.addProperty("kind",craft.kind.id);
        o.addProperty("riding",p.getVehicle()==craft);o.addProperty("alive",craft.isAlive());o.addProperty("engine",craft.engineOn());
        o.addProperty("x",craft.getX());o.addProperty("y",craft.getY());o.addProperty("z",craft.getZ());o.addProperty("hp",craft.hp());
        o.addProperty("fuel",craft.fuel());o.addProperty("selected",craft.weaponFor(0));
        o.addProperty("bulletsAlive",ru.aiforge.realwar.ordnance.BulletManager.count(p.serverLevel()));
        JsonArray a=new JsonArray();for(int n=0;n<craft.kind.loads.length;n++) {
            var load=craft.kind.loads[n];JsonObject e=new JsonObject();e.addProperty("weapon",load.weapon().id);
            e.addProperty("ammo",craft.ammo(n));e.addProperty("usable",craft.canUse(0,n));a.add(e);
        }o.add("loads",a);o.add("spawned",new Gson().toJsonTree(spawned));return o;
    }
}
