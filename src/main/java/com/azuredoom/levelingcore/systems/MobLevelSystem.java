package com.azuredoom.levelingcore.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.EntityUtils;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.Config;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

import java.util.Locale;

import com.azuredoom.levelingcore.LevelingCore;
import com.azuredoom.levelingcore.config.GUIConfig;
import com.azuredoom.levelingcore.config.internal.ConfigManager;
import com.azuredoom.levelingcore.level.formulas.loader.LevelTableLoader;
import com.azuredoom.levelingcore.utils.MobLevelingUtil;

@SuppressWarnings("removal")
public class MobLevelSystem extends EntityTickingSystem<EntityStore> {

    // Cache to prevent Disk I/O every tick
    private static volatile int cachedMaxLevel = -1;
    private static volatile long lastCacheUpdate = 0;
    
    // Global throttling for save operations
    private static volatile long lastSaveTime = 0;

    private Config<GUIConfig> config;
    
    // Fixed Generic Types (Store, Component)
    private final ComponentType<EntityStore, NPCEntity> npcType;
    private final ComponentType<EntityStore, TransformComponent> transformType;

    public MobLevelSystem(Config<GUIConfig> config) {
        this.config = config;
        // Fetch types in constructor
        this.npcType = NPCEntity.getComponentType();
        this.transformType = TransformComponent.getComponentType();
    }

    @Override
    public void tick(
        float var1,
        int index,
        @NonNullDecl ArchetypeChunk<EntityStore> archetypeChunk,
        @NonNullDecl Store<EntityStore> store,
        @NonNullDecl CommandBuffer<EntityStore> commandBuffer
    ) {
        // 1. Global Periodic Save (10s) - Run at the very start of a chunk
        if (index == 0) {
            long now = System.currentTimeMillis();
            if (now - lastSaveTime > 10000) {
                lastSaveTime = now;
                var world = store.getExternalData().getWorld();
                if (world != null) {
                    world.execute(LevelingCore.mobLevelPersistence::save);
                }
            }
        }

        // 2. Retrieve Components
        final var holder = EntityUtils.toHolder(index, archetypeChunk);
        final var npc = holder.getComponent(this.npcType);
        final var transform = holder.getComponent(this.transformType);
        
        // Safety check to ensure we are only processing Mobs with positions
        if (npc == null || transform == null) return;

        // 3. Logic Throttle (2s per mob)
        final var entityId = npc.getUuid();
        var data = LevelingCore.mobLevelRegistry.getOrCreateWithPersistence(
            entityId,
            () -> MobLevelingUtil.computeSpawnLevel(npc),
            0, 
            LevelingCore.mobLevelPersistence
        );

        if (data.locked) return;

        long nowMs = System.currentTimeMillis();
        if (nowMs - data.lastRecalcTick < 2000) {
            return;
        }

        // 4. Execution logic on the World Thread
        store.getExternalData().getWorld().execute(() -> {
            long currentTime = System.currentTimeMillis();
            
            // Cache Timer (1s) - Prevents Disk I/O lag
            if (currentTime - lastCacheUpdate > 1000 || cachedMaxLevel == -1) {
                updateMaxLevelCache();
                lastCacheUpdate = currentTime;
            }

            // Calculate new level
            var newLevel = Math.max(
                1,
                Math.min(cachedMaxLevel, MobLevelingUtil.computeDynamicLevel(config, npc, transform, store))
            );

            // Update Level Data
            if (newLevel != data.level) {
                data.level = newLevel;
            }
            data.lastRecalcTick = currentTime;

            // Apply scaling
            if (data.level != data.lastAppliedLevel) {
                MobLevelingUtil.applyMobScaling(config, npc, data.level, store);
                data.lastAppliedLevel = data.level;
            }
        });
    }

    private synchronized void updateMaxLevelCache() {
        try {
            var internalConfig = ConfigManager.loadOrCreate(LevelingCore.configPath);
            var type = internalConfig.formula.type.trim().toUpperCase(Locale.ROOT);
            
            if (type.equals("LINEAR")) {
                cachedMaxLevel = internalConfig.formula.linear.maxLevel;
            } else if (type.equals("TABLE")) {
                var tableFormula = LevelTableLoader.loadOrCreateFromDataDir(
                    internalConfig.formula.table.file
                );
                cachedMaxLevel = Math.max(1, tableFormula.getMaxLevel());
            } else if (type.equals("CUSTOM")) {
                cachedMaxLevel = internalConfig.formula.custom.maxLevel;
            } else {
                cachedMaxLevel = internalConfig.formula.exponential.maxLevel;
            }
        } catch (Exception e) {
            if (cachedMaxLevel == -1) cachedMaxLevel = 100;
        }
    }

    @NullableDecl
    @Override
    public Query<EntityStore> getQuery() {
        // Guaranteed to work based on original file contents
        return Query.any();
    }
}
