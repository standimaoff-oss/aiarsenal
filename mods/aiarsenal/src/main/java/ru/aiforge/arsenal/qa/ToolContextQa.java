package ru.aiforge.arsenal.qa;

import com.google.gson.JsonObject;
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
import ru.aiforge.realwar.blast.BlastManager;
import ru.aiforge.realwar.field.*;
import ru.aiforge.realwar.blockentity.BombBlockEntity;

/** Contextual production tool interactions; never enabled in a normal game. */
@Mod.EventBusSubscriber(modid="aiarsenal")
public final class ToolContextQa {
    private static BlockPos start,ied;
    private static int tasks;
    private static long blocks;
    private static void check(boolean b,String message){if(!b)throw new IllegalStateException(message);}
    private static ItemStack hold(ServerPlayer p,String id) {
        p.getInventory().clearContent();p.getInventory().selected=0;
        var stack=new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation("aiarsenal",id)));
        p.setItemInHand(InteractionHand.MAIN_HAND,stack);return stack;
    }
    private static UseOnContext at(ServerPlayer p,BlockPos pos) {
        return new UseOnContext(p,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos).add(0,.5,0),Direction.UP,pos,false));
    }
    private static void say(CommandSourceStack s,JsonObject o){s.sendSuccess(()->Component.literal(o.toString()),false);}
    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        if(!Boolean.getBoolean("aiarsenal.stressQa"))return;
        var root=Commands.literal("arsenal_tool_context").requires(s->s.hasPermission(2));
        root.then(Commands.literal("recipes").executes(c->{
            try {say(c.getSource(),RecipeContextQa.run(c.getSource().getPlayerOrException()));}
            catch(java.io.IOException e){throw new IllegalStateException(e);}return 1;
        }));
        root.then(Commands.literal("trench").executes(c->{
            var p=c.getSource().getPlayerOrException();check(!p.getAbilities().instabuild,"survival required");
            start=BlockPos.containing(p.getX()+4,-61,p.getZ()+4);var tool=hold(p,"entrenching_tool");
            tool.useOn(at(p,start));check(start.equals(EntrenchingToolItem.start(tool,p.level())),"start point not set");
            tool.useOn(at(p,start.offset(4,0,0)));
            check(EntrenchingToolItem.start(tool,p.level())==null && tool.getDamageValue()>0,"trench not scheduled/tool not worn");
            say(c.getSource(),trenchStatus(p));return 1;
        }));
        root.then(Commands.literal("trenchstatus").executes(c->{say(c.getSource(),trenchStatus(c.getSource().getPlayerOrException()));return 1;}));
        root.then(Commands.literal("arming").executes(c->{
            var p=c.getSource().getPlayerOrException();check(!p.getAbilities().instabuild,"survival required");
            BlockPos floor=BlockPos.containing(p.getX()+4,-61,p.getZ()+4);var charge=hold(p,"rds_4");
            check(charge.useOn(at(p,floor)).consumesAction() && charge.isEmpty(),"charge placement failed");
            var be=p.serverLevel().getBlockEntity(floor.above());check(be instanceof BombBlockEntity,"no charge block entity");
            var bomb=(BombBlockEntity)be;check(!bomb.isArmed(),"fresh charge unexpectedly armed");
            var plug=hold(p,"arming_plug");plug.useOn(at(p,floor.above()));check(bomb.isArmed(),"not armed");
            plug.useOn(at(p,floor.above()));check(!bomb.isArmed(),"not disarmed");
            JsonObject o=new JsonObject();o.addProperty("armedThenDisarmed",true);o.addProperty("plugCount",plug.getCount());say(c.getSource(),o);return 1;
        }));
        root.then(Commands.literal("ied").executes(c->{
            var p=c.getSource().getPlayerOrException();check(!p.getAbilities().instabuild,"survival required");
            BlockPos floor=BlockPos.containing(p.getX()+8,-61,p.getZ()+8);ied=floor.above();
            tasks=BlastManager.completedBlasts;blocks=BlastManager.totalBlocksChanged;
            var charge=hold(p,"trap_ied");check(charge.useOn(at(p,floor)).consumesAction() && charge.isEmpty(),"IED placement failed");
            var remote=hold(p,"remote_detonator");p.setShiftKeyDown(true);
            try {remote.useOn(at(p,ied));} finally {p.setShiftKeyDown(false);}
            check(remote.hasTag() && remote.getTag().getInt("lx")==ied.getX() && remote.getTag().getInt("lz")==ied.getZ(),"remote not linked");
            say(c.getSource(),iedStatus(p));return 1;
        }));
        root.then(Commands.literal("iedstatus").executes(c->{say(c.getSource(),iedStatus(c.getSource().getPlayerOrException()));return 1;}));
        event.getDispatcher().register(root);
    }
    private static JsonObject trenchStatus(ServerPlayer p) {
        check(start!=null,"no trench");int floors=0,open=0;
        for(int i=0;i<5;i++) {if(p.level().getBlockState(start.offset(i,-1,0)).is(ru.aiforge.realwar.registry.ModBlocks.DUCKBOARD.get()))floors++;
            if(p.level().getBlockState(start.offset(i,0,0)).isAir())open++;}
        JsonObject o=new JsonObject();o.addProperty("floors",floors);o.addProperty("open",open);o.addProperty("pending",TrenchJobs.pending());o.addProperty("toolDamage",p.getMainHandItem().getDamageValue());return o;
    }
    private static JsonObject iedStatus(ServerPlayer p) {
        check(ied!=null,"no IED");var state=p.level().getBlockState(ied);boolean present=state.getBlock() instanceof IedBlock;
        JsonObject o=new JsonObject();o.addProperty("present",present);o.addProperty("armed",present && state.getValue(IedBlock.ARMED));
        o.addProperty("completedTasks",BlastManager.completedBlasts-tasks);o.addProperty("changedBlocks",BlastManager.totalBlocksChanged-blocks);
        o.addProperty("activeBlasts",BlastManager.active(p.serverLevel()));return o;
    }
}
