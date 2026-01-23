package com.azuredoom.levelingcore.level.mobs;

public final class MobLevelData {

    public int level;

    public long lastRecalcTick;

    public boolean locked;

    public int lastAppliedLevel;

    public MobLevelData(int level, long lastRecalcTick) {
        this.level = level;
        this.lastRecalcTick = lastRecalcTick;
        this.locked = false;
        this.lastAppliedLevel = level;
    }
}
