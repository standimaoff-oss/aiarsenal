package ru.aiforge.arsenal.qa;

import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.UUID;
import net.minecraft.commands.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.*;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.aiforge.arsenal.control.RemoteControl;
import ru.aiforge.arsenal.entity.*;
import ru.aiforge.arsenal.item.*;
import ru.aiforge.realwar.blast.BlastManager;

/** Opt-in item/session preparation. Flight, fire and exit are driven by the real client. */
@Mod.EventBusSubscriber(modid="aiarsenal")
public final class DroneContextQa {
    private static ArsEntity drone;
    private static Vec3 origin;
    private static UUID dummy;
    private static int tasks,projectiles;
    @SubscribeEvent public static void joined(net.minecraftforge.event.entity.EntityJoinLevelEvent e) {
        if(Boolean.getBoolean("aiarsenal.stressQa") && !e.getLevel().isClientSide() && drone!=null && e.getEntity() instanceof MissileEntity)projectiles++;
    }
    private static void check(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    private static void reply(CommandSourceStack s,JsonObject o){s.sendSuccess(()->Component.literal(o.toString()),false);}
    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        if(!Boolean.getBoolean("aiarsenal.stressQa"))return;
        var root=Commands.literal("arsenal_drone_context").requires(s->s.hasPermission(2));
        root.then(Commands.literal("prepare").then(Commands.argument("id",StringArgumentType.greedyString()).executes(c->{
            var p=c.getSource().getPlayerOrException();var l=p.serverLevel();
            check(!p.getAbilities().instabuild && !RemoteControl.inSession(p),"requires survival and no existing session");
            if(drone!=null)drone.discard();
            Item item=BuiltInRegistries.ITEM.get(new ResourceLocation(StringArgumentType.getString(c,"id")));
            check(item instanceof UnitItem,"not a unit item");
            p.getInventory().clearContent();p.getInventory().selected=0;p.setYRot(0);p.setXRot(0);
            p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(item));
            BlockPos floor=BlockPos.containing(p.getX()+4,-61,p.getZ()+4);
            var hit=new BlockHitResult(Vec3.atCenterOf(floor).add(0,.5,0),Direction.UP,floor,false);
            check(item.useOn(new UseOnContext(p,InteractionHand.MAIN_HAND,hit)).consumesAction(),"placement failed");
            check(p.getMainHandItem().isEmpty(),"unit item not consumed");
            var found=l.getEntitiesOfClass(ArsEntity.class,new AABB(floor).inflate(10),e->e.kind()==((UnitItem)item).kind);
            check(found.size()==1,"expected one placed drone");drone=found.get(0);
            check(drone instanceof FpvDrone || drone instanceof ReconDrone,"unsupported drone");
            Item controller=BuiltInRegistries.ITEM.get(new ResourceLocation("aiarsenal",drone instanceof FpvDrone?"fpv_goggles":"tablet"));
            p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(controller));
            origin=p.position();tasks=BlastManager.completedBlasts;projectiles=0;dummy=null;
            reply(c.getSource(),status(p));return 1;
        })));
        root.then(Commands.literal("link").executes(c->{
            var p=c.getSource().getPlayerOrException();check(drone!=null && !RemoteControl.inSession(p),"requires placed drone without session");
            drone.interact(p,InteractionHand.MAIN_HAND);
            check(RemoteControl.inSession(p) && p.getCamera()==drone,"link/camera session failed");
            check(p.getMainHandItem().getOrCreateTag().hasUUID("Drone"),"controller not linked");
            dummy=RemoteControl.session(p.getUUID()).dummy();
            reply(c.getSource(),status(p));return 1;
        }));
        root.then(Commands.literal("status").executes(c->{reply(c.getSource(),status(c.getSource().getPlayerOrException()));return 1;}));
        root.then(Commands.literal("designate").executes(c->{
            var p=c.getSource().getPlayerOrException();check(drone instanceof ReconDrone,"not recon");
            // Production designation handler; the later F key must actually consume/launch ammunition.
            ((ReconDrone)drone).designate(p,new Vec3(drone.getX()+45,-60,drone.getZ()+45),null);
            check(TabletItem.target(p.getMainHandItem())!=null,"designation not stored in tablet");
            reply(c.getSource(),status(p));return 1;
        }));
        root.then(Commands.literal("reconnect").executes(c->{
            var p=c.getSource().getPlayerOrException();check(!RemoteControl.inSession(p),"already in session");
            var stack=p.getMainHandItem();check(stack.getItem().use(p.serverLevel(),p,InteractionHand.MAIN_HAND).getResult().consumesAction(),"controller reconnect failed");
            check(RemoteControl.inSession(p) && p.getCamera()==drone,"camera reconnect failed");
            dummy=RemoteControl.session(p.getUUID()).dummy();reply(c.getSource(),status(p));return 1;
        }));
        root.then(Commands.literal("cleanup").executes(c->{
            var p=c.getSource().getPlayerOrException();if(RemoteControl.inSession(p))RemoteControl.stop(p,null);
            if(drone!=null)drone.discard();return 1;
        }));
        event.getDispatcher().register(root);
    }
    private static JsonObject status(ServerPlayer p) {
        check(drone!=null,"no drone");JsonObject o=new JsonObject();o.addProperty("session",RemoteControl.inSession(p));
        o.addProperty("cameraDrone",p.getCamera()==drone);o.addProperty("cameraPlayer",p.getCamera()==p);
        o.addProperty("mode",p.gameMode.getGameModeForPlayer().getName());o.addProperty("removed",drone.isRemoved());
        o.addProperty("entityId",drone.getId());o.addProperty("state",drone.state());o.addProperty("x",drone.getX());o.addProperty("y",drone.getY());o.addProperty("z",drone.getZ());
        o.addProperty("originDistance",p.position().distanceTo(origin));o.addProperty("dummyPresent",dummy!=null && p.serverLevel().getEntity(dummy)!=null);
        o.addProperty("ammo",drone instanceof ReconDrone r?r.ammo():0);o.addProperty("completedTasks",BlastManager.completedBlasts-tasks);
        o.addProperty("activeBlasts",BlastManager.active(p.serverLevel()));o.addProperty("projectiles",projectiles);return o;
    }
}
