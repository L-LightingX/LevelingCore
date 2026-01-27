package com.azuredoom.levelingcore;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.util.Config;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.logging.*;
import javax.annotation.Nonnull;

import com.azuredoom.levelingcore.api.LevelingCoreApi;
import com.azuredoom.levelingcore.commands.*;
import com.azuredoom.levelingcore.config.GUIConfig;
import com.azuredoom.levelingcore.config.internal.ConfigBootstrap;
import com.azuredoom.levelingcore.exceptions.LevelingCoreException;
import com.azuredoom.levelingcore.interaction.SkillPointResetInteraction;
import com.azuredoom.levelingcore.level.LevelServiceImpl;
import com.azuredoom.levelingcore.level.itemlevellock.ItemToLevelMapping;
import com.azuredoom.levelingcore.level.mobs.MobLevelPersistence;
import com.azuredoom.levelingcore.level.mobs.MobLevelRegistry;
import com.azuredoom.levelingcore.level.mobs.mapping.MobBiomeMapping;
import com.azuredoom.levelingcore.level.mobs.mapping.MobInstanceMapping;
import com.azuredoom.levelingcore.level.mobs.mapping.MobZoneMapping;
import com.azuredoom.levelingcore.level.rewards.LevelRewards;
import com.azuredoom.levelingcore.level.rewards.RewardEntry;
import com.azuredoom.levelingcore.level.stats.StatsPerLevelMapping;
import com.azuredoom.levelingcore.level.xp.XPValues;
import com.azuredoom.levelingcore.systems.*;
import com.azuredoom.levelingcore.ui.hud.XPBarHud;
import com.azuredoom.levelingcore.utils.HudPlayerReady;
import com.azuredoom.levelingcore.utils.LevelDownListenerRegistrar;
import com.azuredoom.levelingcore.utils.LevelUpListenerRegistrar;
import com.hypixel.hytale.server.core.universe.PlayerRef;

@SuppressWarnings("removal")
public class LevelingCore extends JavaPlugin {

    public static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public static final Path configDataPath = Paths.get("./mods/levelingcore_LevelingCore/data/config/");

    public static final Path configPath = Paths.get("./mods/levelingcore_LevelingCore/data/config/");

    public static final ConfigBootstrap.Bootstrap bootstrap = ConfigBootstrap.bootstrap(configPath);

    public static LevelServiceImpl levelingService;

    private static LevelingCore INSTANCE;

    private final Config<GUIConfig> config;

    public static final Map<String, Integer> xpMapping = XPValues.loadOrCreate(LevelingCore.configPath);

    public static final Map<Integer, List<RewardEntry>> levelRewardMapping = LevelRewards.loadOrCreate(
        LevelingCore.configPath
    );

    public static final Map<String, Integer> itemLevelMapping = ItemToLevelMapping.loadOrCreate(
        LevelingCore.configPath
    );

    public static final Map<Integer, Integer> apMap = StatsPerLevelMapping.loadOrCreate(LevelingCore.configPath);

    public static final Map<String, Integer> mobInstanceMapping = MobInstanceMapping.loadOrCreate(
        LevelingCore.configPath
    );

    public static final Map<String, Integer> mobZoneMapping = MobZoneMapping.loadOrCreate(LevelingCore.configPath);

    public static final Map<String, Integer> mobBiomeMapping = MobBiomeMapping.loadOrCreate(LevelingCore.configPath);

    public static final MobLevelRegistry mobLevelRegistry = new MobLevelRegistry();

    public static final MobLevelPersistence mobLevelPersistence = new MobLevelPersistence();

    public LevelingCore(@Nonnull JavaPluginInit init) {
        super(init);
        INSTANCE = this;
        config = this.withConfig("levelingcore", GUIConfig.CODEC);
    }

    @Override
    protected void setup() {
        INSTANCE = this;
        this.config.save();
        LOGGER.at(Level.INFO).log("Leveling Core initializing");
        levelingService = bootstrap.service();
        this.registerAllCommands();
        this.registerAllSystems();
        this.getCodecRegistry(Interaction.CODEC)
            .register("SkillPointResetInteraction", SkillPointResetInteraction.class, SkillPointResetInteraction.CODEC);
        
        // Adds the UI to the player and ensures AP stats are applied
        this.getEventRegistry()
            .registerGlobal(
                PlayerReadyEvent.class,
                (playerReadyEvent -> {
                    var player = playerReadyEvent.getPlayer();
                    
                    // FIX: The event returns a generic Ref<EntityStore>, we need to fetch the Component
                    var entityRef = playerReadyEvent.getPlayerRef();
                    var store = entityRef.getStore();
                    var playerRef = store.getComponent(entityRef, PlayerRef.getComponentType());

                    if (player != null && playerRef != null) {
                        // Register listeners immediately (Zero Latency, Zero Tick Cost)
                        LevelUpListenerRegistrar.ensureRegistered(store, player, playerRef, config);
                        LevelDownListenerRegistrar.ensureRegistered(store, player, playerRef, config);

                        LevelingCoreApi.getLevelServiceIfPresent().ifPresent(levelService -> {
                            var uuid = player.getUuid();
                            var level = levelService.getLevel(uuid);
                            int expectedTotal;
                            if (config.get().isUseStatsPerLevelMapping()) {
                                var mapping = LevelingCore.apMap;
                                expectedTotal = mapping.getOrDefault(level, 5);
                            } else {
                                expectedTotal = config.get().getStatsPerLevel();
                            }
                            var used = levelService.getUsedAbilityPoints(uuid);
                            var currentTotal = levelService.getAvailableAbilityPoints(uuid) + used;
                            var targetTotal = Math.max(0, level * expectedTotal);

                            if (currentTotal != targetTotal) {
                                levelService.setAbilityPoints(uuid, targetTotal);
                            }
                        });
                    }
                    HudPlayerReady.ready(playerReadyEvent, config);
                })
            );
        this.getEntityStoreRegistry().registerSystem(new PlayerDamageFilter(config));
        this.getEntityStoreRegistry().registerSystem(new MobDamageFilter(config));
        // Cleans up various weak hash maps and UI on player disconnect
        this.getEventRegistry()
            .registerGlobal(PlayerDisconnectEvent.class, (event) -> {
                XPBarHud.removeHud(event.getPlayerRef());
                LevelUpListenerRegistrar.clear(event.getPlayerRef().getUuid());
                LevelDownListenerRegistrar.clear(event.getPlayerRef().getUuid());
            });
        LevelingCore.mobLevelPersistence.load();
    }

    @Override
    protected void shutdown() {
        LevelingCore.mobLevelPersistence.save();
        super.shutdown();
        LOGGER.at(Level.INFO).log("Leveling Core shutting down");
        try {
            LevelingCore.bootstrap.closeable().close();
        } catch (Exception e) {
            throw new LevelingCoreException("Failed to close resources", e);
        }
    }

    public static LevelServiceImpl getLevelService() {
        return levelingService;
    }

    public static LevelingCore getInstance() {
        return INSTANCE;
    }

    public void registerAllCommands() {
        getCommandRegistry().registerCommand(new AddLevelCommand(config));
        getCommandRegistry().registerCommand(new AddXpCommand(config));
        getCommandRegistry().registerCommand(new SetLevelCommand(config));
        getCommandRegistry().registerCommand(new RemoveLevelCommand(config));
        getCommandRegistry().registerCommand(new RemoveXpCommand(config));
        getCommandRegistry().registerCommand(new ShowStatsCommand(config));
    }

    public void registerAllSystems() {
        getEntityStoreRegistry().registerSystem(new MobLevelSystem(config));
        // Ticking Systems removed as they are now handled by PlayerReadyEvent
        getEntityStoreRegistry().registerSystem(new GainXPEventSystem(config));
        getEntityStoreRegistry().registerSystem(new LossXPEventSystem(config));
    }
}
