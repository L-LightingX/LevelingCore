package com.azuredoom.levelingcore.interactions;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.RootDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.Config;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import java.util.Collections;
import java.util.Set;
import javax.annotation.Nullable;

import com.azuredoom.levelingcore.LevelingCore;
import com.azuredoom.levelingcore.api.LevelingCoreApi;
import com.azuredoom.levelingcore.config.GUIConfig;

public class ItemLevelRestriction extends DamageEventSystem {

    private final Config<GUIConfig> config;

    public ItemLevelRestriction(Config<GUIConfig> config) {
        this.config = config;
    }

    @Override
    public void handle(
        int index,
        @NonNullDecl ArchetypeChunk<EntityStore> archetypeChunk,
        @NonNullDecl Store<EntityStore> store,
        @NonNullDecl CommandBuffer<EntityStore> commandBuffer,
        @NonNullDecl Damage damage
    ) {
        var ref = archetypeChunk.getReferenceTo(index);
        var player = store.getComponent(ref, Player.getComponentType());
        var playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        var itemHand = player.getInventory().getItemInHand();
        var map = LevelingCore.itemLevelMapping;

        commandBuffer.run(storeRef -> {
            if (playerRef != null && damage.getSource() instanceof Damage.EntitySource entitySource) {
                var attackerRef = entitySource.getRef();
                if (attackerRef.isValid()) {
                    var attackerPlayerComponent = commandBuffer.getComponent(attackerRef, Player.getComponentType());
                    if (attackerPlayerComponent != null) {
                        if (!damage.isCancelled()) {
                            var attackerPlayerRef = commandBuffer.getComponent(
                                attackerRef,
                                PlayerRef.getComponentType()
                            );
                            if (attackerPlayerRef != null) {
                                var itemId = itemHand.getItemId();
                                if (itemId.isBlank())
                                    return;

                                var requiredLevel = map.get(itemId);
                                if (requiredLevel == null)
                                    return;
                                LevelingCoreApi.getLevelServiceIfPresent().ifPresent(levelService -> {
                                    int playerLevel = levelService.getLevel(attackerPlayerRef.getUuid());

                                    if (playerLevel < requiredLevel) {
                                        player.sendMessage(
                                            Message.raw(
                                                "You need level " + requiredLevel + " to use this item. You are level "
                                                    + playerLevel + "."
                                            )
                                        );
                                        damage.setCancelled(true);
                                    }
                                });
                            }
                        }
                    }
                }
            }
        });
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return PlayerRef.getComponentType();
    }

    @NonNullDecl
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return Collections.singleton(RootDependency.first());
    }
}
