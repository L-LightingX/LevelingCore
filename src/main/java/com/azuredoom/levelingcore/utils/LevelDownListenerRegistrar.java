package com.azuredoom.levelingcore.utils;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.Config;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.azuredoom.levelingcore.LevelingCore;
import com.azuredoom.levelingcore.api.LevelingCoreApi;
import com.azuredoom.levelingcore.config.GUIConfig;
import com.azuredoom.levelingcore.lang.CommandLang;

@SuppressWarnings("removal")
public class LevelDownListenerRegistrar {

    private static final Set<UUID> REGISTERED =
        Collections.newSetFromMap(new ConcurrentHashMap<>());

    public static void ensureRegistered(
        Store<EntityStore> store,
        Player player,
        PlayerRef playerRef,
        Config<GUIConfig> config
    ) {
        // OPTIMIZATION: Check config before marking as registered
        if (!config.get().isEnableStatLeveling()) {
            return;
        }

        LevelingCoreApi.getLevelServiceIfPresent().ifPresent(levelService1 -> {
            UUID id = playerRef.getUuid();
            
            // OPTIMIZATION: Only mark as registered if the API is present and ready
            if (!REGISTERED.add(id)) {
                return;
            }

            LevelUpRewardsUtil.clear(id);
            
            var world = player.getWorld();
            var world_store = world.getEntityStore();
            final int leveldown_sound_index = SoundEvent.getAssetMap().getIndex(config.get().getLevelDownSound());

            store.getExternalData()
                .getWorld()
                .execute(() -> levelService1.registerLevelDownListener(((playerId, oldLevel, newLevel) -> {
                    
                    // CRITICAL FIX: Ensure the event is for this specific player
                    // Without this, one player leveling down resets everyone's stats.
                    if (!playerId.equals(id)) {
                        return;
                    }

                    StatsUtils.resetStats(store, player);
                    StatsUtils.applyAllStats(store, player, newLevel, config);

                    world.execute(() -> {
                        if (player.getReference() == null) return;

                        var transform = world_store.getStore()
                            .getComponent(playerRef.getReference(), EntityModule.get().getTransformComponentType());
                        
                        if (transform != null) {
                            SoundUtil.playSoundEvent3dToPlayer(
                                player.getReference(),
                                leveldown_sound_index,
                                SoundCategory.UI,
                                transform.getPosition(),
                                world_store.getStore()
                            );
                        }
                    });

                    if (!config.get().isDisableStatPointGainOnLevelUp()) {
                        int pointsPerLevel;
                        if (config.get().isUseStatsPerLevelMapping()) {
                            pointsPerLevel = LevelingCore.apMap.getOrDefault(newLevel, 5);
                        } else {
                            pointsPerLevel = config.get().getStatsPerLevel();
                        }
                        var totalFromLeveling = Math.max(0, newLevel * pointsPerLevel);

                        levelService1.setAbilityPoints(
                            playerId,
                            levelService1.getLevel(playerId) == 1 ? pointsPerLevel : totalFromLeveling
                        );
                        levelService1.setUsedAbilityPoints(playerId, 0);
                        levelService1.setStr(playerId, 0);
                        levelService1.setAgi(playerId, 0);
                        levelService1.setPer(playerId, 0);
                        levelService1.setVit(playerId, 0);
                        levelService1.setInt(playerId, 0);
                        
                        playerRef.sendMessage(
                            CommandLang.ABILITY_POINTS.param("ability_points", totalFromLeveling)
                                .param("player_name", playerRef.getUsername())
                        );
                    }

                    // Reset the alternate state
                    LevelUpListenerRegistrar.clear(id);
                })));
        });
    }

    public static void clear(UUID playerId) {
        REGISTERED.remove(playerId);
    }
}
