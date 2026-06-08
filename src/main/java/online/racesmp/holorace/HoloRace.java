package online.racesmp.holorace;

import online.racesmp.holorace.commands.HoloRaceCommand;
import online.racesmp.holorace.commands.HoloRaceTabCompleter;
import online.racesmp.holorace.hooks.PlaceholderHook;
import online.racesmp.holorace.listeners.*;
import online.racesmp.holorace.managers.*;
import online.racesmp.holorace.utils.MessageUtil;
import org.bukkit.plugin.java.JavaPlugin;

public class HoloRace extends JavaPlugin {

    private static HoloRace instance;
    private RaceManager raceManager;
    private PlayerDataManager playerDataManager;
    private CooldownManager cooldownManager;
    private GUIManager guiManager;

    @Override
    public void onEnable() {
        instance = this;

        // Save default resources
        saveDefaultConfig();
        saveResource("menu.yml", false);
        saveResource("weight.yml", false);
        saveResource("lang.yml", false);
        // Tộc cũ
        saveResource("races/human.yml", false);
        saveResource("races/fish.yml", false);
        saveResource("races/demon.yml", false);
        saveResource("races/giant.yml", false);
        // Tộc mới
        saveResource("races/beast.yml", false);
        saveResource("races/celestial.yml", false);
        saveResource("races/dragon.yml", false);
        saveResource("races/ghost.yml", false);

        // Init managers
        this.raceManager = new RaceManager(this);
        this.playerDataManager = new PlayerDataManager(this);
        this.cooldownManager = new CooldownManager();
        this.guiManager = new GUIManager(this);

        // Load data
        raceManager.loadRaces();
        playerDataManager.loadData();
        MessageUtil.reload(this);

        // Register command
        var cmd = getCommand("holorace");
        if (cmd != null) {
            cmd.setExecutor(new HoloRaceCommand(this));
            cmd.setTabCompleter(new HoloRaceTabCompleter(this));
        }

        // Register listeners
        var pm = getServer().getPluginManager();
        pm.registerEvents(new PlayerJoinListener(this), this);
        pm.registerEvents(new PlayerItemListener(this), this);
        pm.registerEvents(new RaceSkillListener(this), this);
        pm.registerEvents(new EntityDamageListener(this), this);
        pm.registerEvents(new NewRaceSkillListener(this), this);  // 4 tộc mới

        // Hook PlaceholderAPI
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new PlaceholderHook(this).register();
            getLogger().info("Hooked into PlaceholderAPI!");
        }

        getLogger().info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        getLogger().info("  HoloRace v" + getDescription().getVersion() + " Enabled!");
        getLogger().info("  8 Races: Human, Fish, Demon, Giant,");
        getLogger().info("           Beast, Celestial, Dragon, Ghost");
        getLogger().info("  Dev For xVioCent_");
        getLogger().info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    @Override
    public void onDisable() {
        if (playerDataManager != null) playerDataManager.saveData();
        getLogger().info("HoloRace disabled. Data saved.");
    }

    public boolean isDebugMode() {
        return getConfig().getBoolean("debug", false);
    }

    public static HoloRace getInstance() { return instance; }
    public RaceManager getRaceManager() { return raceManager; }
    public PlayerDataManager getPlayerDataManager() { return playerDataManager; }
    public CooldownManager getCooldownManager() { return cooldownManager; }
    public GUIManager getGUIManager() { return guiManager; }
}
