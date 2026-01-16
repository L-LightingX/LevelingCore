package com.azuredoom.levelingcore.utils;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

/**
 * Utility class for handling player statistics operations such as modifying health, stamina, and mana. Provides methods
 * to interact with and manipulate a player's statistics using specific multipliers or reset them to default values.
 */
public class StatsUtils {

    private StatsUtils() {}

    private static @NullableDecl World getWorld(Player player) {
        if (player == null)
            return null;
        return player.getWorld();
    }

    private static @NullableDecl EntityStatMap getStatMap(Store<EntityStore> store, PlayerRef playerRef) {
        return store.getComponent(playerRef.getReference(), EntityStatMap.getComponentType());
    }

    public static void doHealthIncrease(Store<EntityStore> store, PlayerRef playerRef, float healthMultiplier) {
        var playerStatMap = StatsUtils.getStatMap(store, playerRef);
        if (playerStatMap == null)
            return;
        var healthIndex = DefaultEntityStatTypes.getHealth();
        var modifier = new StaticModifier(
            Modifier.ModifierTarget.MAX,
            StaticModifier.CalculationType.ADDITIVE,
            healthMultiplier
        );
        var modifierKey = "LevelingCore_health";
        playerStatMap.putModifier(healthIndex, modifierKey, modifier);
    }

    public static void doStaminaIncrease(Store<EntityStore> store, PlayerRef playerRef, float staminaMultiplier) {
        var playerStatMap = StatsUtils.getStatMap(store, playerRef);
        if (playerStatMap == null)
            return;
        var staminaIndex = DefaultEntityStatTypes.getStamina();
        var modifier = new StaticModifier(
            Modifier.ModifierTarget.MAX,
            StaticModifier.CalculationType.ADDITIVE,
            staminaMultiplier
        );
        var modifierKey = "LevelingCore_stamina";
        playerStatMap.putModifier(staminaIndex, modifierKey, modifier);
    }

    public static void doManaIncrease(Store<EntityStore> store, PlayerRef playerRef, float manaMultiplier) {
        var playerStatMap = StatsUtils.getStatMap(store, playerRef);
        if (playerStatMap == null)
            return;
        var manaIndex = DefaultEntityStatTypes.getMana();
        var modifier = new StaticModifier(
            Modifier.ModifierTarget.MAX,
            StaticModifier.CalculationType.ADDITIVE,
            manaMultiplier
        );
        var modifierKey = "LevelingCore_mana";
        playerStatMap.putModifier(manaIndex, modifierKey, modifier);
    }

    public static void resetStats(Store<EntityStore> store, PlayerRef playerRef) {
        var playerStatMap = StatsUtils.getStatMap(store, playerRef);
        if (playerStatMap == null)
            return;

        var healthIndex = DefaultEntityStatTypes.getHealth();
        var staminaIndex = DefaultEntityStatTypes.getStamina();
        var manaIndex = DefaultEntityStatTypes.getMana();

        playerStatMap.resetStatValue(healthIndex);
        playerStatMap.resetStatValue(staminaIndex);
        playerStatMap.resetStatValue(manaIndex);
    }
}
