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

    private long tickCounter = 0;

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
        if (index == 0)
            tickCounter++;
        var nowTick = tickCounter;
        final var holder = EntityUtils.toHolder(index, archetypeChunk);
        final var npc = holder.getComponent(NPCEntity.getComponentType());
        if (npc == null)
            return;
        final var transform = holder.getComponent(TransformComponent.getComponentType());
        if (transform == null)
            return;
        final var entityId = npc.getUuid();
        var data = LevelingCore.mobLevelRegistry.getOrCreateWithPersistence(
            entityId,
            () -> MobLevelingUtil.computeSpawnLevel(npc),
            nowTick,
            LevelingCore.mobLevelPersistence
        );
        if (data.locked)
            return;
        var recalcEveryTicks = 40;
        if (nowTick - data.lastRecalcTick < recalcEveryTicks)
            return;

        if (index == 0) {
            tickCounter++;

            if (tickCounter % 200 == 0) {
                var world = store.getExternalData().getWorld();
                world.execute(LevelingCore.mobLevelPersistence::save);
            }
        }
        store.getExternalData().getWorld().execute(() -> {
            int mobMaxLevel;
            var interalConfig = ConfigManager.loadOrCreate(LevelingCore.configPath);
            var type = interalConfig.formula.type.trim().toUpperCase(Locale.ROOT);
            if (type.equals("LINEAR")) {
                mobMaxLevel = interalConfig.formula.linear.maxLevel;
            } else if (type.equals("TABLE")) {
                var tableFormula = LevelTableLoader.loadOrCreateFromDataDir(
                    interalConfig.formula.table.file
                );
                mobMaxLevel = Math.max(1, tableFormula.getMaxLevel());
            } else if (type.equals("CUSTOM")) {
                mobMaxLevel = interalConfig.formula.custom.maxLevel;
            } else {
                mobMaxLevel = interalConfig.formula.exponential.maxLevel;
            }
            var newLevel = Math.max(
                1,
                Math.min(mobMaxLevel, MobLevelingUtil.computeDynamicLevel(config, npc, transform, store))
            );

            if (newLevel != data.level)
                data.level = newLevel;
            data.lastRecalcTick = nowTick;

            if (data.level != data.lastAppliedLevel) {
                MobLevelingUtil.applyMobScaling(config, npc, data.level, store);
                data.lastAppliedLevel = data.level;
            }
        });
    }

    @NullableDecl
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(
            NPCEntity.getComponentType(),
            TransformComponent.getComponentType()
        );
    }
}
