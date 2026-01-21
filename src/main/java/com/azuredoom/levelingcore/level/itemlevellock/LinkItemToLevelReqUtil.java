package com.azuredoom.levelingcore.level.itemlevellock;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.event.events.player.PlayerInteractEvent;
import com.hypixel.hytale.server.core.util.Config;

import com.azuredoom.levelingcore.LevelingCore;
import com.azuredoom.levelingcore.api.LevelingCoreApi;
import com.azuredoom.levelingcore.config.GUIConfig;

public class LinkItemToLevelReqUtil {

    private LinkItemToLevelReqUtil() {}

    public static void linkItemToLevelRequirement(PlayerInteractEvent event, Config<GUIConfig> config) {
        var player = event.getPlayer();
        var ref = event.getPlayerRef();
        var store = ref.getStore();
        var world = store.getExternalData().getWorld();
        var itemHand = event.getItemInHand();
        if (itemHand == null)
            return;
        var map = ItemToLevelMapping.loadOrCreate(LevelingCore.configPath);

        world.execute(() -> {
            var itemId = itemHand.getItemId();
            if (itemId.isBlank())
                return;

            var requiredLevel = map.get(itemId);
            if (requiredLevel == null)
                return;

            LevelingCoreApi.getLevelServiceIfPresent().ifPresent(levelService -> {
                var playerLevel = levelService.getLevel(player.getUuid());

                if (playerLevel < requiredLevel) {
                    player.sendMessage(
                        Message.raw(
                            "You need level " + requiredLevel + " to use this item. You are level " + playerLevel + "."
                        )
                    );
                    event.setCancelled(true);
                }
            });
        });
    }
}
