package com.azuredoom.levelingcore.utils;

import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.plugin.PluginManager;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.util.Config;

import java.util.logging.Level;

import com.azuredoom.levelingcore.LevelingCore;
import com.azuredoom.levelingcore.api.LevelingCoreApi;
import com.azuredoom.levelingcore.compat.MultipleHudCompat;
import com.azuredoom.levelingcore.config.GUIConfig;
import com.azuredoom.levelingcore.hud.XPBarHud;
import com.azuredoom.levelingcore.level.LevelServiceImpl;

public class HudPlayerReady {

    private HudPlayerReady() {}

    public static void ready(PlayerReadyEvent event, Config<GUIConfig> config) {
        var player = event.getPlayer();
        var ref = event.getPlayerRef();
        var store = ref.getStore();
        var world = store.getExternalData().getWorld();

        world.execute(() -> {
            LevelingCoreApi.getLevelServiceIfPresent().ifPresent(levelService1 -> {
                var playerRef = store.getComponent(ref, PlayerRef.getComponentType());
                if (playerRef == null)
                    return;
                var xpHud = new XPBarHud(playerRef, levelService1, config);
                if (PluginManager.get().getPlugin(new PluginIdentifier("Buuz135", "MultipleHUD")) != null) {
                    MultipleHudCompat.showHud(player, playerRef, xpHud);
                } else {
                    player.sendMessage(
                        Message.raw(
                            "LevelingCore Error: MultipleHUD not found, XP HUD will not work correctly with other mods adding custom UI"
                        )
                    );
                    LevelingCore.LOGGER.at(Level.WARNING)
                        .log("MultipleHUD not found, XP HUD will not work correctly with other mods adding custom UI");
                    player.getHudManager().setCustomHud(playerRef, xpHud);
                }
            });
        });
    }
}
