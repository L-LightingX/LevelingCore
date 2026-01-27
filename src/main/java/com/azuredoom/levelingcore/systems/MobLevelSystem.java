package com.azuredoom.levelingcore.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
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

    public MobLevelSystem(Config<GUIConfig> config) {
        this.config = config;
    }

    @Override
    public void tick(
        float var1,
        int index,
        @NonNullDecl ArchetypeChunk<EntityStore> archetypeChunk,
        @NonNullDecl Store<EntityStore> store,
        @NonNullDecl CommandBuffer<EntityStore> commandBuffer
    ) {
        if (index == 0) {
            long now = System.currentTimeMillis();
            // Save every 10 seconds (10000ms)
            if (now - lastSaveTime > 10000) {
                lastSaveTime = now;
                var world = store.getExternalData().getWorld();
                if (world != null) {
                    world.execute(LevelingCore.mobLevelPersistence::save);
                }
            }
        }

        final var holder = EntityUtils.toHolder(index, archetypeChunk);
        final var npc = holder.getComponent(NPCEntity.getComponentType());
        if (npc == null) return;
        
        final var transform = holder.getComponent(TransformComponent.getComponentType());
        if (transform == null) return;
        

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

        store.getExternalData().getWorld().execute(() -> {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastCacheUpdate > 10000 || cachedMaxLevel == -1) {
                updateMaxLevelCache();
                lastCacheUpdate = currentTime;
            }

            var newLevel = Math.max(
                1,
                Math.min(cachedMaxLevel, MobLevelingUtil.computeDynamicLevel(config, npc, transform, store))
            );

            // Update Data
            if (newLevel != data.level) {
                data.level = newLevel;
            }
            data.lastRecalcTick = currentTime;

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
            e.printStackTrace();
        }
    }

    @NullableDecl
    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }
}
