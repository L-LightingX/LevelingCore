package com.azuredoom.levelingcore.level.mobs;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntSupplier;

public final class MobLevelRegistry {

    private final ConcurrentHashMap<UUID, MobLevelData> levels = new ConcurrentHashMap<>();

    public MobLevelData getOrCreate(UUID entityId, IntSupplier initialLevelSupplier, long nowTick) {
        return levels.computeIfAbsent(entityId, id -> new MobLevelData(initialLevelSupplier.getAsInt(), nowTick));
    }

    public MobLevelData getOrCreate(UUID entityId, IntSupplier initialLevelSupplier) {
        return levels.computeIfAbsent(
            entityId,
            id -> new MobLevelData(initialLevelSupplier.getAsInt(), System.currentTimeMillis())
        );
    }

    public MobLevelData getOrCreateWithPersistence(
        UUID entityId,
        IntSupplier spawnLevelSupplier,
        long nowTick,
        MobLevelPersistence persistence
    ) {
        return levels.computeIfAbsent(entityId, id -> {
            var persistedOpt = persistence.get(id);
            if (persistedOpt.isPresent()) {
                var persisted = persistedOpt.get();
                var data = new MobLevelData(persisted.spawnLevel(), nowTick);
                data.locked = persisted.locked();
                return data;
            }
            var spawnLevel = spawnLevelSupplier.getAsInt();
            persistence.put(id, new PersistedMobLevel(spawnLevel, false));

            return new MobLevelData(spawnLevel, nowTick);
        });
    }

    public MobLevelData get(UUID entityId) {
        return levels.get(entityId);
    }

    public void set(UUID entityId, MobLevelData data) {
        levels.put(entityId, data);
    }

    public void remove(UUID entityId) {
        levels.remove(entityId);
    }

    public int size() {
        return levels.size();
    }

    public ConcurrentHashMap<UUID, MobLevelData> snapshot() {
        return new ConcurrentHashMap<>(levels);
    }

    public void loadSnapshot(ConcurrentHashMap<UUID, MobLevelData> snapshot) {
        levels.clear();
        levels.putAll(snapshot);
    }
}
