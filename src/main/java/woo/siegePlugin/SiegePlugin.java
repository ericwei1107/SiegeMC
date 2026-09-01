package woo.siegePlugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import woo.siegePlugin.arena.InMemoryPlacedBlockTracker;
import woo.siegePlugin.arena.PlacedBlockListener;
import woo.siegePlugin.arena.BaseClaimBoundaryListener;
import woo.siegePlugin.arena.BaseClaimInteractionListener;
import woo.siegePlugin.arena.BaseClaimPolicy;
import woo.siegePlugin.arena.BaseTerrainProtectionListener;
import woo.siegePlugin.arena.PlacedBlockTracker;
import woo.siegePlugin.capture.CaptureBanner;
import woo.siegePlugin.capture.CaptureListener;
import woo.siegePlugin.capture.CaptureService;
import woo.siegePlugin.capture.CaptureSettings;
import woo.siegePlugin.config.CanonicalConfig;
import woo.siegePlugin.command.SiegeAdminCommand;
import woo.siegePlugin.command.SiegeCommand;
import woo.siegePlugin.minecart.MinecartPlacementListener;
import woo.siegePlugin.minecart.MinecartCooldownService;
import woo.siegePlugin.minecart.MinecartDamageListener;
import woo.siegePlugin.minecart.MinecartDamageSettings;
import woo.siegePlugin.minecart.MinecartArenaProtection;
import woo.siegePlugin.minecart.MinecartSettings;
import woo.siegePlugin.minecart.MinecartSweeper;
import woo.siegePlugin.minecart.MinecartTerrainProtectionListener;
import woo.siegePlugin.minecart.MinecartWorldCompatibility;
import woo.siegePlugin.minecart.SiegeMinecartMarker;
import woo.siegePlugin.combat.CombatTagAdapter;
import woo.siegePlugin.combat.CombatTagStatus;
import woo.siegePlugin.combat.CombatTaggedCommandListener;
import woo.siegePlugin.combat.CombatTaggedInteractionListener;
import woo.siegePlugin.display.TeamDisplayListener;
import woo.siegePlugin.display.TeamDisplayService;
import woo.siegePlugin.display.TeamIdentityColors;
import woo.siegePlugin.display.SidebarService;
import woo.siegePlugin.display.SidebarSettings;
import woo.siegePlugin.display.SidebarPreferenceService;
import woo.siegePlugin.display.SidebarPreferenceListener;
import woo.siegePlugin.capture.BossBarPreferenceService;
import woo.siegePlugin.capture.BossBarPreferenceListener;
import woo.siegePlugin.title.PlayerTitleService;
import woo.siegePlugin.title.PlayerTitleListener;
import woo.siegePlugin.persistence.PlayerTitleDao;
import woo.siegePlugin.persistence.PlayerVisibilityPreferenceDao;
import woo.siegePlugin.mobility.MobilityCooldownListener;
import woo.siegePlugin.mobility.MobilityCooldownSettings;
import woo.siegePlugin.death.DeathFireworkEffects;
import woo.siegePlugin.round.RoundActivityStatus;
import woo.siegePlugin.death.SiegeDeathListener;
import woo.siegePlugin.kit.KitChoiceCatalog;
import woo.siegePlugin.kit.KitEditorListener;
import woo.siegePlugin.kit.KitCommandCooldown;
import woo.siegePlugin.kit.KitCommandSettings;
import woo.siegePlugin.kit.KitService;
import woo.siegePlugin.kit.KitSnapshot;
import woo.siegePlugin.kit.RuntimeKitOverrides;
import woo.siegePlugin.lobby.LobbyJoinItem;
import woo.siegePlugin.lobby.LobbyJoinItemListener;
import woo.siegePlugin.lobby.TutorialBookListener;
import woo.siegePlugin.economy.CurrencyService;
import woo.siegePlugin.economy.CurrencySettings;
import woo.siegePlugin.economy.ShopListener;
import woo.siegePlugin.persistence.PlayerBalanceDao;
import woo.siegePlugin.persistence.PurchaseOutboxDao;
import woo.siegePlugin.persistence.MatchScoreDao;
import woo.siegePlugin.persistence.KitSelectionDao;
import woo.siegePlugin.persistence.PlayerInventoryDao;
import woo.siegePlugin.persistence.SiegeDatabase;
import woo.siegePlugin.persistence.MatchStatsDao;
import woo.siegePlugin.persistence.RotationStateDao;
import woo.siegePlugin.persistence.WorldCleanupDao;
import woo.siegePlugin.score.ScoringService;
import woo.siegePlugin.score.ScoringSettings;
import woo.siegePlugin.state.PlayerStateTransitionListener;
import woo.siegePlugin.state.LobbySettings;
import woo.siegePlugin.state.PlayerStateTransitionService;
import woo.siegePlugin.state.PlayerStateTransitions;
import woo.siegePlugin.state.SpectatorResidencyHandler;
import woo.siegePlugin.storage.PotionStorageListener;
import woo.siegePlugin.storage.PotionStorageService;
import woo.siegePlugin.team.TeamAssignmentListener;
import woo.siegePlugin.team.TeamAssignmentService;
import woo.siegePlugin.team.TeamSpawnLocations;
import woo.siegePlugin.team.TeamSwitchService;
import woo.siegePlugin.team.TownyAdapter;
import woo.siegePlugin.round.ActiveCombatEligibility;
import woo.siegePlugin.round.ActiveRoundContext;
import woo.siegePlugin.round.ActiveRoundProvider;
import woo.siegePlugin.round.BukkitRoundAudience;
import woo.siegePlugin.round.BukkitRoundScheduler;
import woo.siegePlugin.round.NativeWorldLifecycle;
import woo.siegePlugin.round.RotationCoordinator;
import woo.siegePlugin.round.RotationJoinListener;
import woo.siegePlugin.round.RotationSettings;
import woo.siegePlugin.round.RoundRoster;
import woo.siegePlugin.map.MapManifest;
import woo.siegePlugin.map.NativeMapWorldLoader;
import woo.siegePlugin.map.RuntimeMapOverrides;
import woo.siegePlugin.map.MapCalibrationService;
import woo.siegePlugin.state.LobbySettings;
import woo.siegePlugin.map.SiegeMap;
import woo.siegePlugin.stats.CombatStatsListener;
import woo.siegePlugin.stats.MatchStatsService;
import woo.siegePlugin.stats.MatchStatsTracker;
import java.io.File;
import java.io.IOException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.logging.Level;

public final class SiegePlugin extends JavaPlugin {

    private static final String DISCORD_INVITE = "https://discord.gg/pQ5zjqTcg";

    private TownyAdapter townyAdapter;
    private TeamAssignmentService teamAssignmentService;
    private TeamSwitchService teamSwitchService;
    private TeamDisplayService teamDisplayService;
    private SidebarService sidebarService;
    private CaptureService captureService;
    private ScoringService scoringService;
    private RoundActivityStatus phaseStatus;
    private PlacedBlockTracker placedBlockTracker;
    private MinecartSweeper minecartSweeper;
    private SiegeMinecartMarker siegeMinecartMarker;
    private MinecartCooldownService minecartCooldownService;
    private MinecartArenaProtection minecartArenaProtection;
    private MinecartDamageListener minecartDamageListener;
    private CurrencyService currencyService;
    private KitService kitService;
    private KitEditorListener kitEditorListener;
    private SiegeDatabase database;
    private PlayerStateTransitionService playerStateTransitionService;
    private PlayerStateTransitions playerStateTransitions;
    private PotionStorageService potionStorageService;
    private ActiveRoundProvider activeRounds;
    private RoundRoster roundRoster;
    private ActiveCombatEligibility eligibility;
    private BaseClaimPolicy baseClaimPolicy;
    private RotationCoordinator rotationCoordinator;
    private MatchStatsTracker matchStatsTracker;
    private MatchStatsService matchStatsService;
    private TeamSpawnLocations teamSpawnLocations;
    private MapManifest mapManifest;
    private RuntimeKitOverrides runtimeKitOverrides;
    private RuntimeMapOverrides runtimeMapOverrides;
    private MapCalibrationService mapCalibrationService;
    private CombatTagStatus combatTagStatus;
    private PlayerTitleService playerTitleService;
    private SidebarPreferenceService sidebarPreferences;
    private BossBarPreferenceService bossBarPreferences;
    private MobilityCooldownListener mobilityCooldowns;

    @Override
    public void onEnable() {
        // Ensures plugins/SiegeMC/config.yml exists, copying the bundled
        // default from resources/config.yml if it's missing. Does NOT
        // overwrite an existing file.
        saveDefaultConfig();
        saveResource("maps.yml", false);

        runtimeKitOverrides = new RuntimeKitOverrides(getDataFolder());
        runtimeMapOverrides = new RuntimeMapOverrides(getDataFolder());
        List<String> problems = new ArrayList<>();
        try {
            runtimeKitOverrides.applyTo(getConfig());
        } catch (IOException exception) {
            problems.add(exception.getMessage());
        }
        problems.addAll(TownyAdapter.provisionSpectatorTown(getConfig()));
        problems.addAll(validateStartup());

        if (!problems.isEmpty()) {
            getLogger().severe("SiegeMC failed to start due to invalid configuration:");
            for (String problem : problems) {
                getLogger().severe(" - " + problem);
            }
            getLogger().severe("Fix config.yml and restart the server. Plugin will now disable.");
            getServer().getPluginManager().disablePlugin(this);
            return; // stop here — nothing below this line should assume config is valid
        }

        this.townyAdapter = TownyAdapter.fromConfig(getConfig());
        this.potionStorageService = new PotionStorageService(this, townyAdapter);
        potionStorageService.warnLegacyRecords(
                mapManifest.rotationPool().stream().map(SiegeMap::id).collect(java.util.stream.Collectors.toSet())
        );
        this.teamAssignmentService = new TeamAssignmentService(townyAdapter);
        TeamIdentityColors identityColors = TeamIdentityColors.fromConfig(getConfig());
        this.teamDisplayService = new TeamDisplayService(
                getServer(),
                townyAdapter,
                identityColors
        );
        this.sidebarService = new SidebarService(
                getServer(),
                teamDisplayService,
                SidebarSettings.fromConfig(getConfig()),
                identityColors
        );
        teamDisplayService.setScoreboardReadyHandler(sidebarService::initializePlayer);
        this.activeRounds = new ActiveRoundProvider();
        this.roundRoster = new RoundRoster();
        this.eligibility = new ActiveCombatEligibility(activeRounds, roundRoster);
        this.phaseStatus = activeRounds;
        this.baseClaimPolicy = new BaseClaimPolicy(activeRounds, eligibility);
        this.captureService = new CaptureService(
                this,
                townyAdapter,
                sidebarService,
                CaptureBanner.fromConfig(getConfig(), getServer(), getLogger()),
                CaptureSettings.fromConfig(getConfig()),
                phaseStatus
        );
        Plugin combatTag = Objects.requireNonNull(getServer().getPluginManager().getPlugin("CombatTag"));
        combatTagStatus = CombatTagAdapter.fromPlugin(combatTag);
        this.teamSwitchService = new TeamSwitchService(
                townyAdapter,
                combatTagStatus,
                captureService,
                teamSpawnLocations = TeamSpawnLocations.fromConfig(getConfig(), getServer())
        );
        initializePlayerStateTransitions(combatTagStatus);
        playerStateTransitionService.setSpectatorStateChangeHandler(teamDisplayService::handleTeamSwitch);
        playerStateTransitionService.setLobbyItemHandler(LobbyJoinItem::giveTo);
        playerStateTransitionService.setRoundActiveSupplier(activeRounds::isActive);
        this.scoringService = new ScoringService(
                this,
                new MatchScoreDao(database),
                captureService,
                sidebarService,
                phaseStatus,
                ScoringSettings.fromConfig(getConfig())
        );
        MinecartSettings minecartSettings = MinecartSettings.fromConfig(getConfig());
        this.siegeMinecartMarker = new SiegeMinecartMarker(this);
        this.minecartCooldownService = new MinecartCooldownService(minecartSettings.tntPlacementCooldown());
        this.currencyService = new CurrencyService(
                this,
                new PlayerBalanceDao(database),
                new PurchaseOutboxDao(database),
                CurrencySettings.fromConfig(getConfig()),
                siegeMinecartMarker
        );
        currencyService.setRoundActiveSupplier(activeRounds::isActive);
        currencyService.setDeliveryEligibility(eligibility::isEligibleFighter);
        scoringService.setBannerControlRewardHandler(currencyService::awardBannerControlTicks);
        initializeRoundServices();
        registerCommands();
        registerListeners();
        teamDisplayService.initializeOnlinePlayers();
        getServer().getOnlinePlayers().forEach(player -> {
            playerTitleService.load(player);
            sidebarPreferences.load(player);
            bossBarPreferences.load(player);
        });
        currencyService.start();
        kitService.loadOnlinePlayers();
        captureService.start();
        scoringService.start();
        matchStatsService.start();
        minecartSweeper.start();
        rotationCoordinator.start();

        getLogger().info("SiegeMC enabled — configuration and Towny integration validated successfully.");
    }

    @Override
    public void onDisable() {
        if (captureService != null) {
            captureService.stop();
        }
        if (scoringService != null) {
            scoringService.stop();
        }
        if (rotationCoordinator != null) {
            rotationCoordinator.stop();
        }
        if (matchStatsService != null) {
            matchStatsService.stop();
        }
        if (minecartSweeper != null) {
            minecartSweeper.stop();
        }
        if (currencyService != null) {
            currencyService.shutdown();
        }
        if (kitEditorListener != null) {
            kitEditorListener.shutdown();
        }
        if (kitService != null) {
            kitService.shutdown();
        }
        if (playerStateTransitionService != null) {
            playerStateTransitionService.shutdown();
        }
        if (potionStorageService != null) {
            potionStorageService.shutdown();
        }
        if (database != null) {
            try {
                database.close();
            } catch (RuntimeException exception) {
                getLogger().log(Level.SEVERE, "Could not flush the SiegeMC database during shutdown.", exception);
            }
        }
    }

    public PlayerStateTransitions getPlayerStateTransitions() {
        return Objects.requireNonNull(
                playerStateTransitions,
                "Player state transitions are unavailable while SiegeMC is disabled"
        );
    }

    /**
     * Checks every config value the plugin actually depends on.
     * Returns a list of human-readable problems — empty list means config is valid.
     */
    private List<String> validateStartup() {
        List<String> problems = new ArrayList<>();
        FileConfiguration config = getConfig();

        String redTown = config.getString("teams.red.town");
        if (redTown == null || redTown.isBlank()) {
            problems.add("teams.red.town is missing or empty");
        }

        String blueTown = config.getString("teams.blue.town");
        if (blueTown == null || blueTown.isBlank()) {
            problems.add("teams.blue.town is missing or empty");
        }

        if (redTown != null && redTown.equalsIgnoreCase(blueTown)) {
            problems.add("teams.red.town and teams.blue.town must be different towns");
        }

        problems.addAll(CaptureSettings.findConfigurationProblems(config, getServer()));
        problems.addAll(ScoringSettings.findConfigurationProblems(config));
        problems.addAll(RotationSettings.findConfigurationProblems(config));
        problems.addAll(MinecartSettings.findConfigurationProblems(config));
        problems.addAll(MinecartDamageSettings.findConfigurationProblems(config));
        problems.addAll(MinecartWorldCompatibility.findProblems(config, getServer()));
        problems.addAll(CurrencySettings.findConfigurationProblems(config));
        problems.addAll(KitCommandSettings.findConfigurationProblems(config));
        problems.addAll(LobbySettings.findConfigurationProblems(config, getServer()));
        problems.addAll(CanonicalConfig.findConfigurationProblems(config));

        Plugin towny = getServer().getPluginManager().getPlugin("Towny");
        if (towny == null || !towny.isEnabled()) {
            problems.add("Towny is missing or not enabled");
        } else {
            problems.addAll(TownyAdapter.findConfigurationProblems(config));
        }

        problems.addAll(TeamSpawnLocations.findConfigurationProblems(config, getServer()));
        problems.addAll(TeamIdentityColors.findConfigurationProblems(config));
        problems.addAll(SidebarSettings.findConfigurationProblems(config));
        problems.addAll(MobilityCooldownSettings.findConfigurationProblems(config));

        File mapsFile = new File(getDataFolder(), "maps.yml");
        problems.addAll(runtimeMapOverrides.findConfigurationProblems(mapsFile));
        try {
            this.mapManifest = runtimeMapOverrides.loadManifest(mapsFile);
        } catch (IllegalArgumentException exception) {
            problems.add("maps.yml could not be loaded: " + exception.getMessage());
        }

        Plugin combatTag = getServer().getPluginManager().getPlugin("CombatTag");
        if (combatTag == null || !combatTag.isEnabled()) {
            problems.add("CombatTag is missing or not enabled");
        } else {
            problems.addAll(CombatTagAdapter.findIntegrationProblems(combatTag));
        }

        return problems;
    }

    private void registerCommands() {
        PluginCommand siegeCommand = Objects.requireNonNull(
                getCommand("siege"),
                "The siege command is missing from plugin.yml"
        );
        NativeMapWorldLoader calibrationLoader = new NativeMapWorldLoader(this);
        mapCalibrationService = new MapCalibrationService(this, calibrationLoader, runtimeMapOverrides,
                new File(getDataFolder(), "maps.yml"), LobbySettings.fromConfig(getConfig(), getServer()), potionStorageService);
        SiegeCommand commandHandler = new SiegeCommand(
                townyAdapter,
                teamSwitchService,
                teamDisplayService,
                playerStateTransitionService,
                new SiegeAdminCommand(
                        this,
                        captureService,
                        scoringService,
                        kitService,
                        getLogger(),
                        potionStorageService,
                        rotationCoordinator,
                        runtimeKitOverrides,
                        runtimeMapOverrides,
                        mapCalibrationService,
                        playerTitleService
                ),
                currencyService,
                kitEditorListener,
                getLogger(),
                rotationCoordinator,
                sidebarPreferences,
                bossBarPreferences
        );
        siegeCommand.setExecutor(commandHandler);
        siegeCommand.setTabCompleter(commandHandler);
        registerHelpCommand("commands", false);
        registerHelpCommand("admincommands", true);
        registerDiscordCommand();
    }

    private void registerDiscordCommand() {
        PluginCommand command = Objects.requireNonNull(getCommand("discord"), "Missing /discord command");
        command.setExecutor((sender, ignored, label, args) -> {
            sender.sendMessage(Component.text("Join SiegeMC on Discord: ", NamedTextColor.GOLD)
                    .append(Component.text(DISCORD_INVITE, NamedTextColor.AQUA)
                            .clickEvent(ClickEvent.openUrl(DISCORD_INVITE))));
            return true;
        });
    }

    private void registerHelpCommand(String name, boolean admin) {
        PluginCommand command = Objects.requireNonNull(getCommand(name), "Missing /" + name + " command");
        command.setExecutor((sender, ignored, label, args) -> {
            if (admin && !sender.hasPermission(SiegeAdminCommand.PERMISSION)) { sender.sendMessage("You do not have permission to view administrator commands."); return true; }
            if (admin) {
                sender.sendMessage("/siege admin map calibrate <map> — open a private disposable setup copy.");
                sender.sendMessage("/siege admin map setspawn <red|blue> — save the team spawn at your position.");
                sender.sendMessage("/siege admin map corner <1|2> — save an arena-bounds corner at your position.");
                sender.sendMessage("/siege admin map setbanner [radius] — save the banner position and capture radius.");
                sender.sendMessage("/siege admin map baseclaim <red|blue> — stage your current native chunk as team base territory.");
                sender.sendMessage("/siege admin map baseunclaim <red|blue> | baselist — remove or inspect staged base chunks.");
                sender.sendMessage("/siege admin map return — teleport back to your active calibration copy.");
                sender.sendMessage("/siege admin map finish | abort — save this calibration template, or discard setup from anywhere.");
                sender.sendMessage("/siege admin supply claim <red|blue> — tag the targeted calibration double chest as a team refill supply.");
                sender.sendMessage("/siege admin supply unclaim — remove the targeted calibration chest's supply tag.");
                sender.sendMessage("/siege admin supply list | info — inspect supplies discovered in this map copy.");
                sender.sendMessage("/siege admin rotation status|validate|retry|force|end — inspect, validate, select, or end a round.");
                sender.sendMessage("/siege admin testscore — set your active team's score to 9,990 for an end-of-siege test.");
                sender.sendMessage("/siege admin savekit confirm — make your inventory the global default kit.");
                sender.sendMessage("/siege admin setbanner — move the banner in an active real siege.");
                sender.sendMessage("/siege admin title <player> <role> — set a native Tab-list title.");
            } else {
                sender.sendMessage("/siege join — opt into the active or next siege.");
                sender.sendMessage("/siege lobby — return to the lobby and leave the battlefield.");
                sender.sendMessage("/siege team — show your current Red or Blue team.");
                sender.sendMessage("/siege switch <red|blue> — switch teams when the active siege permits it.");
                sender.sendMessage("/siege kit — equip or customize your personal siege kit.");
                sender.sendMessage("/siege shop — open the battle shop during an active siege.");
                sender.sendMessage("/siege spectate | rejoin — watch the active siege, then return to combat.");
                sender.sendMessage("/siege sidebar | bossbar — toggle your saved HUD preferences.");
                sender.sendMessage("/discord — open the SiegeMC Discord invite.");
            }
            return true;
        });
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(
                new TeamAssignmentListener(this, teamDisplayService::handleJoin),
                this
        );
        getServer().getPluginManager().registerEvents(new PlayerTitleListener(playerTitleService), this);
        getServer().getPluginManager().registerEvents(new SidebarPreferenceListener(sidebarPreferences), this);
        getServer().getPluginManager().registerEvents(new BossBarPreferenceListener(bossBarPreferences), this);
        getServer().getPluginManager().registerEvents(new RotationJoinListener(this, rotationCoordinator), this);
        getServer().getPluginManager().registerEvents(
                new TeamDisplayListener(teamDisplayService),
                this
        );
        getServer().getPluginManager().registerEvents(
                new PlayerStateTransitionListener(playerStateTransitionService, eligibility, teamSpawnLocations),
                this
        );
        getServer().getPluginManager().registerEvents(
                new LobbyJoinItemListener(this, playerStateTransitionService), this
        );
        getServer().getPluginManager().registerEvents(new TutorialBookListener(this), this);
        getServer().getPluginManager().registerEvents(
                new CaptureListener(captureService),
                this
        );
        getServer().getPluginManager().registerEvents(
                new BaseClaimInteractionListener(baseClaimPolicy, combatTagStatus), this
        );
        getServer().getPluginManager().registerEvents(new CombatTaggedInteractionListener(combatTagStatus), this);
        getServer().getPluginManager().registerEvents(new CombatTaggedCommandListener(combatTagStatus), this);
        getServer().getPluginManager().registerEvents(new BaseClaimBoundaryListener(baseClaimPolicy), this);
        getServer().getPluginManager().registerEvents(
                new PlacedBlockListener(
                        placedBlockTracker,
                        townyAdapter,
                        activeRounds,
                        eligibility
                ),
                this
        );
        getServer().getPluginManager().registerEvents(new BaseTerrainProtectionListener(baseClaimPolicy), this);
        getServer().getPluginManager().registerEvents(
                new SiegeDeathListener(
                        townyAdapter,
                        scoringService,
                        currencyService,
                        eligibility,
                        playerStateTransitionService,
                        matchStatsTracker
                ),
                this
        );
        getServer().getPluginManager().registerEvents(new DeathFireworkEffects(this), this);
        this.mobilityCooldowns = new MobilityCooldownListener(
                eligibility::isEligibleFighter, MobilityCooldownSettings.fromConfig(getConfig())
        );
        getServer().getPluginManager().registerEvents(mobilityCooldowns, this);
        getServer().getPluginManager().registerEvents(
                new CombatStatsListener(townyAdapter, siegeMinecartMarker, eligibility, matchStatsTracker),
                this
        );
        getServer().getPluginManager().registerEvents(
                new ShopListener(currencyService),
                this
        );
        getServer().getPluginManager().registerEvents(
                new PotionStorageListener(potionStorageService, combatTagStatus), this
        );
        getServer().getPluginManager().registerEvents(kitEditorListener, this);
        getServer().getPluginManager().registerEvents(
                new MinecartPlacementListener(
                        siegeMinecartMarker,
                        minecartCooldownService,
                        minecartArenaProtection,
                        MinecartSettings.fromConfig(getConfig()),
                        eligibility::isEligibleFighter
                ),
                this
        );
        getServer().getPluginManager().registerEvents(
                new MinecartTerrainProtectionListener(minecartArenaProtection),
                this
        );
        CaptureSettings captureSettings = CaptureSettings.fromConfig(getConfig());
        this.minecartDamageListener = new MinecartDamageListener(
                        this,
                        siegeMinecartMarker,
                        townyAdapter,
                        captureService.banner(),
                        captureSettings.radiusBlocks(),
                        MinecartDamageSettings.fromConfig(getConfig())
                );
        getServer().getPluginManager().registerEvents(minecartDamageListener, this);
    }

    private void initializeRoundServices() {
        this.minecartArenaProtection = new MinecartArenaProtection();
        this.placedBlockTracker = new InMemoryPlacedBlockTracker();
        this.minecartSweeper = new MinecartSweeper(
                this,
                MinecartSettings.fromConfig(getConfig()).stationaryCleanupThreshold()
        );
        this.matchStatsTracker = new MatchStatsTracker();
        this.matchStatsService = new MatchStatsService(
                this,
                new MatchStatsDao(database),
                matchStatsTracker,
                captureService,
                phaseStatus,
                () -> scoringService.currentScores() == null ? null : scoringService.currentScores().matchId()
        );
        scoringService.setFinalStatsSupplier(matchStatsTracker::snapshot);

        NativeMapWorldLoader loader = new NativeMapWorldLoader(this);
        this.rotationCoordinator = new RotationCoordinator(
                getLogger(),
                new BukkitRoundScheduler(this),
                new BukkitRoundAudience(getServer(), playerStateTransitionService),
                new NativeWorldLifecycle(
                        loader,
                        (world, mapId) -> potionStorageService.verifySupplyChests(world, mapId),
                        this::trackedGeneratedWorldNames
                ),
                activeRounds,
                roundRoster,
                new RotationStateDao(database),
                new MatchScoreDao(database),
                new MatchStatsDao(database),
                new WorldCleanupDao(database),
                () -> runtimeMapOverrides.loadManifest(new File(getDataFolder(), "maps.yml")),
                potionStorageService::findMapProblems,
                scoringService,
                matchStatsTracker,
                RotationSettings.fromConfig(getConfig()),
                ScoringSettings.fromConfig(getConfig()).winningScore(),
                this::rebindRoundServices,
                new java.util.Random()
        );
        scoringService.setMatchCompletedHandler(rotationCoordinator::onMatchCompleted);
        playerStateTransitionService.setLobbyReturnHandler(rotationCoordinator::markReturnedToLobby);
        teamSwitchService.setTeamSwitchHandler(rotationCoordinator::recordTeamSwitch);
        teamSwitchService.setFighterHeadcount(team -> roundRoster.battlefieldFighterCount(
                team, playerId -> getServer().getPlayer(playerId) != null
        ));
        captureService.setBattlefieldFighterCheck(eligibility::isEligibleFighter);
        potionStorageService.setBattlefieldFighterCheck(eligibility::isEligibleFighter);
    }

    /**
     * Generated world folders the plugin still accounts for. Anything else on
     * disk is reported for manual review rather than deleted automatically.
     */
    private java.util.Set<String> trackedGeneratedWorldNames() {
        java.util.Set<String> tracked = new java.util.HashSet<>();
        activeRounds.current().ifPresent(context -> tracked.add(context.world().getName()));
        getServer().getWorlds().forEach(world -> {
            if (world.getName().startsWith("siege-active-")) {
                tracked.add(world.getName());
            }
        });
        return tracked;
    }

    private void initializePlayerStateTransitions(CombatTagStatus combatTagStatus) {
        this.database = new SiegeDatabase(getDataFolder().toPath().resolve("siege.db"));
        KitSnapshot kitSnapshot = KitSnapshot.fromConfig(getConfig());
        KitChoiceCatalog.LoadResult structuralCatalog = KitChoiceCatalog.load(getConfig(), kitSnapshot);
        for (String problem : structuralCatalog.problems()) {
            getLogger().warning("Disabled kit editor group: " + problem);
        }
        KitChoiceCatalog.LoadResult choiceCatalog = structuralCatalog.catalog().validateRuntime(kitSnapshot);
        for (String problem : choiceCatalog.problems()) {
            getLogger().warning("Disabled kit editor group: " + problem);
        }
        this.kitService = new KitService(
                this,
                kitSnapshot,
                choiceCatalog.catalog(),
                new KitSelectionDao(database)
        );
        this.kitEditorListener = new KitEditorListener(
                kitService,
                new KitCommandCooldown(KitCommandSettings.fromConfig(getConfig()).cooldown())
        );
        this.playerStateTransitionService = new PlayerStateTransitionService(
                this,
                new PlayerInventoryDao(database),
                kitService,
                SpectatorResidencyHandler.forTowny(townyAdapter),
                LobbySettings.fromConfig(getConfig(), getServer()),
                townyAdapter,
                teamAssignmentService,
                teamSpawnLocations,
                combatTagStatus,
                captureService
        );
        this.playerStateTransitions = new PlayerStateTransitions(getServer());
        this.playerTitleService = new PlayerTitleService(this, new PlayerTitleDao(database));
        this.sidebarPreferences = new SidebarPreferenceService(
                this, sidebarService, new PlayerVisibilityPreferenceDao(database, "sidebar_preferences")
        );
        this.bossBarPreferences = new BossBarPreferenceService(
                this, captureService, new PlayerVisibilityPreferenceDao(database, "bossbar_preferences")
        );

        database.initialized().whenComplete((ignored, failure) -> {
            if (failure == null) {
                getLogger().info("SiegeMC SQLite persistence initialized.");
                return;
            }

            Throwable cause = failure;
            while (cause instanceof CompletionException && cause.getCause() != null) {
                cause = cause.getCause();
            }
            Throwable databaseFailure = cause;
            getLogger().log(Level.SEVERE, "SiegeMC SQLite persistence failed to initialize.", databaseFailure);
            if (!isEnabled()) {
                return;
            }
            getServer().getScheduler().runTask(this, () -> {
                if (isEnabled()) {
                    getLogger().severe("Disabling SiegeMC because durable inventory storage is unavailable.");
                    getServer().getPluginManager().disablePlugin(this);
                }
            });
        });
    }

    private void rebindRoundServices(ActiveRoundContext context) {
        sidebarService.updateRound(context.map().displayName(), context.scoreLimit());
        captureService.rebind(context.capturePoint(), context.map().captureRadius());
        teamSpawnLocations.rebind(context.spawns());
        minecartArenaProtection.rebind(context.world().getName(), context.bounds());
        minecartSweeper.rebind(context.world().getName());
        minecartCooldownService.clearAll();
        if (mobilityCooldowns != null) {
            mobilityCooldowns.clearAll();
        }
        getServer().getOnlinePlayers().forEach(player -> player.setCooldown(org.bukkit.Material.TNT_MINECART, 0));
        minecartDamageListener.rebind(captureService.banner(), context.map().captureRadius());
        placedBlockTracker.clearAll();
        potionStorageService.activateMap(context.map().id(), context.world().getName(), context.bounds());
    }
}
