/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.resources.ResourceLocation
 *  net.minecraft.sounds.SoundEvent
 *  net.minecraftforge.registries.DeferredRegister
 *  net.minecraftforge.registries.ForgeRegistries
 *  net.minecraftforge.registries.IForgeRegistry
 *  net.minecraftforge.registries.RegistryObject
 */
package ru.aiforge.realwar.registry;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.RegistryObject;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create((IForgeRegistry)ForgeRegistries.SOUND_EVENTS, (String)"aiarsenal");
    public static final RegistryObject<SoundEvent> NUKE_BLAST = ModSounds.reg("rw_nuke_blast");
    public static final RegistryObject<SoundEvent> NUKE_RUMBLE = ModSounds.reg("nuke_rumble");
    public static final RegistryObject<SoundEvent> NUKE_FAR = ModSounds.reg("rw_nuke_far");
    public static final RegistryObject<SoundEvent> BLAST_LARGE = ModSounds.reg("blast_large");
    public static final RegistryObject<SoundEvent> BLAST_MEDIUM = ModSounds.reg("blast_medium");
    public static final RegistryObject<SoundEvent> BLAST_SMALL = ModSounds.reg("blast_small");
    public static final RegistryObject<SoundEvent> GEIGER_CLICK = ModSounds.reg("geiger_click");
    public static final RegistryObject<SoundEvent> ARM_BEEP = ModSounds.reg("arm_beep");
    public static final RegistryObject<SoundEvent> BOMB_WHISTLE = ModSounds.reg("rw_bomb_whistle");
    public static final RegistryObject<SoundEvent> SHOCK_CRACK = ModSounds.reg("shock_crack");
    public static final RegistryObject<SoundEvent> ROCKET_LAUNCH_SMALL = ModSounds.reg("rocket_launch_small");
    public static final RegistryObject<SoundEvent> ROCKET_LAUNCH_LARGE = ModSounds.reg("rocket_launch_large");
    public static final RegistryObject<SoundEvent> SAM_LAUNCH = ModSounds.reg("sam_launch");
    public static final RegistryObject<SoundEvent> ROCKET_WHOOSH = ModSounds.reg("rocket_whoosh");
    public static final RegistryObject<SoundEvent> CANNON_TANK = ModSounds.reg("cannon_tank");
    public static final RegistryObject<SoundEvent> CANNON_HOWITZER = ModSounds.reg("cannon_howitzer");
    public static final RegistryObject<SoundEvent> SHELL_WHISTLE = ModSounds.reg("shell_whistle");
    public static final RegistryObject<SoundEvent> FLAK_BURST = ModSounds.reg("flak_burst");
    public static final RegistryObject<SoundEvent> ENGINE_IDLE = ModSounds.reg("engine_idle");
    public static final RegistryObject<SoundEvent> ENGINE_START = ModSounds.reg("engine_start");
    public static final RegistryObject<SoundEvent> RELOAD_CLANK = ModSounds.reg("reload_clank");
    public static final RegistryObject<SoundEvent> HYDRAULIC = ModSounds.reg("hydraulic");
    public static final RegistryObject<SoundEvent> LOCK_TONE = ModSounds.reg("lock_tone");
    public static final RegistryObject<SoundEvent> LOCK_DONE = ModSounds.reg("lock_done");
    public static final RegistryObject<SoundEvent> SIREN_WAIL = ModSounds.reg("siren_wail");
    // reply 6: helicopters / boats / submarine
    public static final RegistryObject<SoundEvent> ROTOR = ModSounds.reg("rotor");
    public static final RegistryObject<SoundEvent> ROTOR_TURBINE = ModSounds.reg("rotor_turbine");
    public static final RegistryObject<SoundEvent> BOAT_ENGINE = ModSounds.reg("boat_engine");
    public static final RegistryObject<SoundEvent> CRAFT_CANNON = ModSounds.reg("craft_cannon");
    public static final RegistryObject<SoundEvent> CRAFT_MG = ModSounds.reg("craft_mg");
    public static final RegistryObject<SoundEvent> TORPEDO_LAUNCH = ModSounds.reg("torpedo_launch");
    public static final RegistryObject<SoundEvent> FLARE_POP = ModSounds.reg("flare_pop");
    public static final RegistryObject<SoundEvent> SONAR_PING = ModSounds.reg("sonar_ping");
    public static final RegistryObject<SoundEvent> MISSILE_WARN = ModSounds.reg("missile_warn");
    public static final RegistryObject<SoundEvent> PROP_PISTON = ModSounds.reg("prop_piston");
    public static final RegistryObject<SoundEvent> AIR_RUSH = ModSounds.reg("air_rush");
    // r22 small arms (procedural, tools/make_sounds_r22.py)
    public static final RegistryObject<SoundEvent> RIFLE_AK = ModSounds.reg("rifle_ak");
    // r23 (tools/make_sounds_r23.py)
    public static final RegistryObject<SoundEvent> RIFLE_T5000 = ModSounds.reg("rifle_t5000");
    public static final RegistryObject<SoundEvent> MG_PKP = ModSounds.reg("mg_pkp");
    public static final RegistryObject<SoundEvent> BOLT_CYCLE = ModSounds.reg("bolt_cycle");
    // r24 (tools/make_sounds_r24.py)
    public static final RegistryObject<SoundEvent> SHOTGUN_SAIGA = ModSounds.reg("shotgun_saiga");
    public static final RegistryObject<SoundEvent> SHOTGUN_870 = ModSounds.reg("shotgun_870");
    public static final RegistryObject<SoundEvent> PUMP_ACTION = ModSounds.reg("pump_action");
    public static final RegistryObject<SoundEvent> SHELL_IN = ModSounds.reg("shell_in");
    public static final RegistryObject<SoundEvent> RIFLE_M4 = ModSounds.reg("rifle_m4");
    public static final RegistryObject<SoundEvent> MAG_OUT = ModSounds.reg("mag_out");
    public static final RegistryObject<SoundEvent> MAG_IN = ModSounds.reg("mag_in");
    public static final RegistryObject<SoundEvent> BOLT_RACK = ModSounds.reg("bolt_rack");
    public static final RegistryObject<SoundEvent> DRY_FIRE = ModSounds.reg("dry_fire");
    public static final RegistryObject<SoundEvent> SELECTOR = ModSounds.reg("selector");
    // r26 WW2 small arms (tools/make_sounds_r26.py)
    public static final RegistryObject<SoundEvent> SMG_PPSH = ModSounds.reg("smg_ppsh");
    public static final RegistryObject<SoundEvent> SMG_MP40 = ModSounds.reg("smg_mp40");
    public static final RegistryObject<SoundEvent> RIFLE_MOSIN = ModSounds.reg("rifle_mosin");
    public static final RegistryObject<SoundEvent> RIFLE_K98 = ModSounds.reg("rifle_k98");
    public static final RegistryObject<SoundEvent> CLIP_IN = ModSounds.reg("clip_in");

    public static final RegistryObject<SoundEvent> PISTOL_C96 = ModSounds.reg("pistol_c96");
    public static final RegistryObject<SoundEvent> PISTOL_TT33 = ModSounds.reg("pistol_tt33");

    public static final RegistryObject<SoundEvent> MUSKET_FIRE = reg("musket_fire");
    public static final RegistryObject<SoundEvent> FLINTLOCK_FIRE = reg("flintlock_fire");
    public static final RegistryObject<SoundEvent> POWDER_CANNON = reg("powder_cannon");
    public static final RegistryObject<SoundEvent> PAPER_LOAD = ModSounds.reg("paper_load");
    public static final RegistryObject<SoundEvent> RAMROD = ModSounds.reg("ramrod");

    private ModSounds() {
    }

    private static RegistryObject<SoundEvent> reg(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent((ResourceLocation)new ResourceLocation("aiarsenal", name)));
    }
}

