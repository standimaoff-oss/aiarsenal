package ru.aiforge.realwar.item;
/** 1943–45 and 1812 representative uniforms; common-side catalogue. Generated from tools/historical_uniforms.py. */
public final class HistoricalUniforms {
    private HistoricalUniforms() {}
    public static final String[] SETS = {
        "ww2_su_infantry",
        "ww2_su_officer",
        "ww2_su_tanker",
        "ww2_su_pilot",
        "ww2_su_general",
        "ww2_de_infantry",
        "ww2_de_officer",
        "ww2_de_tanker",
        "ww2_de_pilot",
        "ww2_de_general",
        "nap_fr_infantry",
        "nap_fr_officer",
        "nap_fr_general",
        "nap_ri_infantry",
        "nap_ri_officer",
        "nap_ri_general"
    };
    public static boolean contains(String set) { return java.util.Arrays.asList(SETS).contains(set); }
}
