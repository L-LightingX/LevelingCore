package com.azuredoom.levelingcore.systems;

import com.hypixel.hytale.component.Archetype;
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

    private static volatile int cachedMaxLevel = -1;
    private static volatile long lastCacheUpdate = 0;
    private static volatile long lastSaveTime = 0;

    private Config<GUIConfig> config;
    private final ComponentType<EntityStore, NPCEntity> npcType;
    private final ComponentType<EntityStore, TransformComponent> transformType;

    public MobLevelSystem(Config<GUIConfig> config) {
        this.config = config;
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
        // PERFORMANCE FIX: Top-Level Throttle
        // We only process this mob once every 40 ticks (approx 2 seconds).
        // This prevents the expensive 'EntityUtils.toHolder' (1.51% lag) from 
        // running 39 out of 40 times.
        long worldAge = store.getExternalData().getWorld().getAge();
        if ((worldAge + index) % 40 != 0) {
            return;
        }

        // 1. Global Periodic Save (Check happens only once every 2 seconds now, which is safe)
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
        // This line is the bottleneck. By moving it here, we've reduced its cost by 97%.
        final var holder = EntityUtils.toHolder(index, archetypeChunk);
        final var npc = holder.getComponent(this.npcType);
        final var transform = holder.getComponent(this.transformType);
        
        if (npc == null || transform == null) return;

        final var entityId = npc.getUuid();
        var data = LevelingCore.mobLevelRegistry.getOrCreateWithPersistence(
            entityId,
            () -> MobLevelingUtil.computeSpawnLevel(npc),
            0, 
            LevelingCore.mobLevelPersistence
        );

        if (data.locked) return;

        // 3. Execution logic
        store.getExternalData().getWorld().execute(() -> {
            long currentTime = System.currentTimeMillis();
            
            // Optimization: Only check the config file if 1 second has passed
            if (currentTime - lastCacheUpdate > 1000 || cachedMaxLevel == -1) {
                updateMaxLevelCache();
                lastCacheUpdate = currentTime;
            }

            var newLevel = Math.max(
                1,
                Math.min(cachedMaxLevel, MobLevelingUtil.computeDynamicLevel(config, npc, transform, store))
            );

            if (newLevel != data.level) {
                data.level = newLevel;
            }

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
        return Archetype.of(this.npcType, this.transformType);
    }
}
