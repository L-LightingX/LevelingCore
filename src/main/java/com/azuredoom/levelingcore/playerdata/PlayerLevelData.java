package com.azuredoom.levelingcore.playerdata;

import java.util.UUID;

/**
 * Represents the level-related data of a player within the leveling system. This includes the player's unique
 * identifier and their experience points (XP). The class provides methods to retrieve and modify the player's XP, with
 * constraints ensuring it remains non-negative.
 */
public class PlayerLevelData {

    private final UUID playerId;

    private long xp;

    private int str;

    private int agi;

    private int per;

    private int vit;

    private int intelligence;

    private int con;

    private int abilityPoints;

    private int usedAbilityPoints;

    public PlayerLevelData(UUID playerId) {
        this.playerId = playerId;
        this.xp = 0;
    }

    /**
     * Retrieves the unique identifier of the player.
     *
     * @return The player's unique identifier as a UUID.
     */
    public UUID getPlayerId() {
        return playerId;
    }

    /**
     * Retrieves the player's current experience points (XP).
     *
     * @return The current XP value of the player as a long.
     */
    public long getXp() {
        return xp;
    }

    /**
     * Sets the player's experience points (XP) to the specified value. Ensures that the XP cannot be set to a negative
     * number; any negative input value will be adjusted to zero.
     *
     * @param xp The experience points to assign to the player. Values less than zero will be automatically adjusted to
     *           zero.
     */
    public void setXp(long xp) {
        this.xp = Math.max(0, xp);
    }

    public int getStr() {
        return str;
    }

    public void setStr(int str) {
        this.str = str;
    }

    public int getAgi() {
        return agi;
    }

    public void setAgi(int agi) {
        this.agi = agi;
    }

    public int getPer() {
        return per;
    }

    public void setPer(int per) {
        this.per = per;
    }

    public int getVit() {
        return vit;
    }

    public void setVit(int vit) {
        this.vit = vit;
    }

    public int getIntelligence() {
        return intelligence;
    }

    public void setIntelligence(int intelligence) {
        this.intelligence = intelligence;
    }

    public int getCon() {
        return con;
    }

    public void setCon(int con) {
        this.con = con;
    }

    public int getAbilityPoints() {
        return abilityPoints;
    }

    public void setAbilityPoints(int abilityPoints) {
        this.abilityPoints = abilityPoints;
    }

    public int getUsedAbilityPoints() {
        return usedAbilityPoints;
    }

    public void setUsedAbilityPoints(int usedAbilityPoints) {
        this.usedAbilityPoints = usedAbilityPoints;
    }

}
