"""Compile the actual client key arbitration against small Minecraft/Forge API fixtures.
Installed client assertions additionally check no Oculus pipeline rebuild during R.
"""
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]

@unittest.skipUnless(shutil.which('javac'),'JDK required; run in verification workflow')
class ReloadKeyGuardTest(unittest.TestCase):
    def test_context_key_and_repeat_isolation(self):
        sources={
          'net/minecraft/client/KeyMapping.java':'''package net.minecraft.client;
            public class KeyMapping { public String name; public int key,clicks;
            public KeyMapping(String n,int k){name=n;key=k;}
            public String getName(){return name;} public boolean matches(int k,int s){return key==k;}
            public boolean consumeClick(){if(clicks==0)return false;clicks--;return true;} }''',
          'net/minecraft/client/Minecraft.java':'''package net.minecraft.client;
            public class Minecraft {public static final Minecraft INSTANCE=new Minecraft();
            public static Minecraft getInstance(){return INSTANCE;}
            public Object screen; public Player player=new Player();public Options options=new Options();
            public static class Player {public Object vehicle;public Object getVehicle(){return vehicle;}}
            public static class Options {public KeyMapping[] keyMappings;}}''',
          'net/minecraftforge/api/distmarker/Dist.java':'package net.minecraftforge.api.distmarker; public enum Dist { CLIENT }',
          'net/minecraftforge/client/event/InputEvent.java':'''package net.minecraftforge.client.event;
            public class InputEvent {public record Key(int getKey,int getScanCode,int getAction) {}}''',
          'net/minecraftforge/eventbus/api/EventPriority.java':'package net.minecraftforge.eventbus.api; public enum EventPriority { HIGHEST,NORMAL }',
          'net/minecraftforge/eventbus/api/SubscribeEvent.java':'package net.minecraftforge.eventbus.api; @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface SubscribeEvent {EventPriority priority() default EventPriority.NORMAL;}',
          'net/minecraftforge/fml/common/Mod.java':'''package net.minecraftforge.fml.common;
            public @interface Mod {public @interface EventBusSubscriber {String modid();net.minecraftforge.api.distmarker.Dist value();}}''',
          'ru/aiforge/realwar/vehicle/LauncherVehicle.java':'package ru.aiforge.realwar.vehicle; public class LauncherVehicle {}',
          'ru/aiforge/realwar/client/GunClient.java':'package ru.aiforge.realwar.client; public class GunClient {public static Object weapon;public static Object held(){return weapon;}}',
          'ru/aiforge/realwar/client/VehicleClient.java':'''package ru.aiforge.realwar.client;
            public class VehicleClient {public static final net.minecraft.client.KeyMapping KEY_RELOAD=new net.minecraft.client.KeyMapping("reload",82);}''',
          'ru/aiforge/arsenal/AiArsenal.java':'''package ru.aiforge.arsenal;
            public class AiArsenal {public static final Log LOGGER=new Log();public static class Log {public void info(String s){}}}''',
          'ru/aiforge/realwar/client/ShaderReloadKeyGuard.java':(ROOT/'mods/aiarsenal/src/main/java/ru/aiforge/realwar/client/ShaderReloadKeyGuard.java').read_text(),
          'TestGuard.java':'''import net.minecraft.client.*;import net.minecraftforge.client.event.InputEvent;
            import ru.aiforge.realwar.client.*;
            public class TestGuard {
              static void expect(boolean v){if(!v)throw new AssertionError();}
              static void press(int key,int action){ShaderReloadKeyGuard.key(new InputEvent.Key(key,0,action));}
              public static void main(String[] args) throws Exception {
                expect(ShaderReloadKeyGuard.class.getMethod("key",InputEvent.Key.class)
                    .getAnnotation(net.minecraftforge.eventbus.api.SubscribeEvent.class).priority()
                    ==net.minecraftforge.eventbus.api.EventPriority.HIGHEST);
                var mc=Minecraft.getInstance();var shader=new KeyMapping("iris.keybind.reload",82);
                var other=new KeyMapping("other.mod",82);var own=VehicleClient.KEY_RELOAD;
                mc.options.keyMappings=new KeyMapping[]{shader,other,own};
                shader.clicks=1;press(82,1);expect(shader.clicks==1); // empty handed remains available
                GunClient.weapon=new Object();other.clicks=1;own.clicks=1;
                press(82,1);expect(shader.clicks==0 && other.clicks==1 && own.clicks==1);
                shader.clicks=5;press(82,2);expect(shader.clicks==0); // held-key repeat
                shader.clicks=1;mc.screen=new Object();press(82,1);expect(shader.clicks==1);
                mc.screen=null;press(82,0);expect(shader.clicks==1); // release
                press(69,1);expect(shader.clicks==1); // unrelated key
                own.key=69;press(82,1);expect(shader.clicks==1); // user rebind
                own.key=82;shader.key=69;press(82,1);expect(shader.clicks==1);
                shader.key=82;GunClient.weapon=null;mc.player.vehicle=new ru.aiforge.realwar.vehicle.LauncherVehicle();
                press(82,1);expect(shader.clicks==0); // seated launcher
                mc.player=null;shader.clicks=1;press(82,1);expect(shader.clicks==1);
              }}'''
        }
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder);files=[]
            for name,text in sources.items():
                p=root/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text);files.append(str(p))
            subprocess.run(['javac','-d',str(root),*files],check=True,capture_output=True,timeout=30)
            subprocess.run(['java','-cp',str(root),'TestGuard'],check=True,capture_output=True,timeout=10)
