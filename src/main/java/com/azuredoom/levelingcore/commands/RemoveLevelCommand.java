package com.azuredoom.levelingcore.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.Config;
import com.hypixel.hytale.server.core.util.EventTitleUtil;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import javax.annotation.Nonnull;

import com.azuredoom.levelingcore.api.LevelingCoreApi;
import com.azuredoom.levelingcore.config.GUIConfig;
import com.azuredoom.levelingcore.lang.CommandLang;
import com.azuredoom.levelingcore.ui.hud.XPBarHud; // REQUIRED IMPORT

public class RemoveLevelCommand extends AbstractPlayerCommand {

    @Nonnull
    private final RequiredArg<PlayerRef> playerArg;

    @Nonnull
    private final RequiredArg<Integer> levelArg;

    private final Config<GUIConfig> config;

    public RemoveLevelCommand(Config<GUIConfig> config) {
        super("removelevel", "Remove level from player");
        this.requirePermission("levelingcore.removelevel");
        this.config = config;
        this.playerArg = this.withRequiredArg(
            "player",
            "Player to remove level from.",
            ArgTypes.PLAYER_REF
        );
        this.levelArg = this.withRequiredArg("level", "Amount of levels to remove", ArgTypes.INTEGER);
    }

    @Override
    protected void execute(
        @NonNullDecl CommandContext commandContext,
        @NonNullDecl Store<EntityStore> store,
        @NonNullDecl Ref<EntityStore> ref,
        @NonNullDecl PlayerRef senderRef, // Renamed for clarity
        @NonNullDecl World world
    ) {
        var levelServiceOpt = LevelingCoreApi.getLevelServiceIfPresent();
        if (levelServiceOpt.isEmpty()) {
            commandContext.sendMessage(CommandLang.NOT_INITIALIZED);
            return;
        }
        
        var levelService = levelServiceOpt.get();
        PlayerRef targetRef = this.playerArg.get(commandContext);
        int levelsToRemove = this.levelArg.get(commandContext);
        var playerUUID = targetRef.getUuid();

        // 1. Modify the backend data
        levelService.removeLevel(playerUUID, levelsToRemove);
        
        // 2. Prepare messages
        var newLevel = levelService.getLevel(playerUUID);
        var removeLevelMsg = CommandLang.REMOVE_LEVEL_1.param("level", levelsToRemove)
            .param("player", targetRef.getUsername());
        var levelTotalMsg = CommandLang.REMOVE_LEVEL_2.param("player", targetRef.getUsername()).param("level", newLevel);
        
        // 3. Update Visuals
        if (config.get().isEnableLevelAndXPTitles()) {
            EventTitleUtil.showEventTitleToPlayer(targetRef, levelTotalMsg, removeLevelMsg, true);
        }
        
        // FIX: Tell the client to update the XP Bar UI immediately
        XPBarHud.updateHud(targetRef);

        commandContext.sendMessage(removeLevelMsg);
        commandContext.sendMessage(levelTotalMsg);
    }
}
