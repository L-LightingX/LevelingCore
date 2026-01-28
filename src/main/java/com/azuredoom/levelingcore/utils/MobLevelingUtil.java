package com.azuredoom.levelingcore.utils;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.Config;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

import java.util.Random;
import java.util.logging.Level;

import com.azuredoom.levelingcore.LevelingCore;
import com.azuredoom.levelingcore.api.LevelingCoreApi;
import com.azuredoom.levelingcore.config.GUIConfig;
import com.azuredoom.levelingcore.level.mobs.CoreLevelMode;
import com.azuredoom.levelingcore.level.mobs.MobLevelRegistry;

@SuppressWarnings("removal")
public class MobLevelingUtil {

    private static final MobLevelRegistry registry = LevelingCore.mobLevelRegistry;
    private static final String MODIFIER_KEY = "LevelingCore_mob_health";

    public MobLevelingUtil() {}

    public static int computeDynamicLevel(
        Config<GUIConfig> config,
        NPCEntity npc,
        TransformComponent transform,
        Store<EntityStore> store
    ) {
        String modeStr = config.get().getLevelMode();
        if (modeStr == null) {
            return computeNearbyPlayersMeanLevel(transform, store);
        }

        CoreLevelMode mode = CoreLevelMode.fromString(modeStr).orElse(null);
        if (mode == null) {
            return computeNearbyPlayersMeanLevel(transform, store);
        }

        return switch (mode) {
            case SPAWN_ONLY -> registry.get(npc.getUuid()).level;
            case NEARBY_PLAYERS_MEAN -> computeNearbyPlayersMeanLevel(transform, store);
            case BIOME -> computeBiomeLevel(store);
            case ZONE -> computeZoneLevel(store);
            case INSTANCE -> computeInstanceLevel(store);
        };
    }

    public static void applyMobScaling(Config<GUIConfig> config, NPCEntity npc, int level, Store<EntityStore> store) {
        // ANIMATION FIX: Do not scale dead mobs. This prevents the reset-to-idle glitch.
        if (npc.getHealth() <= 0) return;

        float healthMult = 1F + ((float) level - 1F) * config.get().getMobHealthMultiplier();
        var stats = store.getComponent(npc.getReference(), EntityStatMap.getComponentType());
        if (stats == null) return;

        var healthIndex = DefaultEntityStatTypes.getHealth();
        var modifier = new StaticModifier(
            Modifier.ModifierTarget.MAX,
            StaticModifier.CalculationType.ADDITIVE,
            healthMult
        );
        
        stats.putModifier(healthIndex, MODIFIER_KEY, modifier);
        stats.maximizeStatValue(EntityStatMap.Predictable.SELF, DefaultEntityStatTypes.getHealth());
        stats.update();
    }

    public static int computeSpawnLevel(NPCEntity npc) {
        var uuid = npc.getUuid();
        var seed = uuid.getMostSignificantBits() ^ uuid.getLeastSignificantBits();
        var rng = new Random(seed);
        return 1 + rng.nextInt(10);
    }

    public static int computeInstanceLevel(Store<EntityStore> store) {
        var world = store.getExternalData().getWorld();
        var instanceName = world.getName();
        if (instanceName == null || instanceName.isBlank()) return 1;
        return LevelingCore.mobInstanceMapping.getOrDefault(instanceName.toLowerCase(), 1);
    }

    public static int computeZoneLevel(Store<EntityStore> store) {
        var world = store.getExternalData().getWorld();
        var players = world.getPlayers();
        
        // CRASH FIX: Safety check for empty servers
        if (players.isEmpty()) return 1;
        
        var worldMapTracker = players.iterator().next().getWorldMapTracker();
        var currentZone = worldMapTracker.getCurrentZone();
        if (currentZone == null) return 1;

        return LevelingCore.mobZoneMapping.getOrDefault(currentZone.zoneName().toLowerCase(), 1);
    }

    public static int computeBiomeLevel(Store<EntityStore> store) {
        var world = store.getExternalData().getWorld();
        var players = world.getPlayers();

        // CRASH FIX: Safety check for empty servers
        if (players.isEmpty()) return 1;

        var worldMapTracker = players.iterator().next().getWorldMapTracker();
        var currentBiome = worldMapTracker.getCurrentBiomeName();
        if (currentBiome == null) return 6;

        return LevelingCore.mobBiomeMapping.getOrDefault(currentBiome.toLowerCase(), 1);
    }

    public static int computeNearbyPlayersMeanLevel(TransformComponent transform, Store<EntityStore> store) {
        var world = store.getExternalData().getWorld();
        var players = world.getPlayers();
        if (players.isEmpty()) return 5;

        var lvlOpt = LevelingCoreApi.getLevelServiceIfPresent();
        if (lvlOpt.isEmpty()) return 5;
        var lvlService = lvlOpt.get();

        var mobPos = transform.getPosition();
        var sum = 0;
        var count = 0;
        final float radiusSq = 1600f; // 40 * 40

        for (var p : players) {
            var pRef = p.getPlayerRef();
            if (pRef == null || pRef.getTransform() == null) continue;
            
            var pPos = pRef.getTransform().getPosition();
            if (pPos.distanceSquaredTo(mobPos) <= radiusSq) {
                sum += lvlService.getLevel(pRef.getUuid());
                count++;
            }
        }

        if (count == 0) return 5;
        return (int) Math.round((double) sum / (double) count);
    }
}
