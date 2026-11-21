package com.nullfaction.network;

public enum NullAction {
    SUMMON_T1, SUMMON_T2, SUMMON_T3,
    FOLLOW, GUARD, HOLD, ATTACK, RECALL,
    BOUNTY_STRIKE, VOID_PURGE,
    PORTAL_AMBUSH, TNT_BARRAGE, WITHER_CANNON,
    BUILD_BUNKER, BUILD_SKYBRIDGE, BUILD_STASIS, BUILD_CANNON, BUILD_TURRET, CLEAR_AREA;

    public static NullAction parse(String s) {
        try { return valueOf(s); } catch (IllegalArgumentException e) { return null; }
    }
}
