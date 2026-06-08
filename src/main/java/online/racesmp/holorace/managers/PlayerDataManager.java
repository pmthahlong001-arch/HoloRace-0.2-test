package online.racesmp.holorace.managers;

import online.racesmp.holorace.HoloRace;
import online.racesmp.holorace.models.PlayerData;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class PlayerDataManager {

    private final HoloRace plugin;
    private final Map<UUID, PlayerData> dataMap = new HashMap<>();
    private File dataFile;
    private YamlConfiguration dataCfg;

    public PlayerDataManager(HoloRace plugin) {
        this.plugin = plugin;
    }

    public void loadData() {
        dataFile = new File(plugin.getDataFolder(), "playerdata.yml");
        if (!dataFile.exists()) {
            try { dataFile.createNewFile(); } catch (IOException e) { e.printStackTrace(); }
        }
        dataCfg = YamlConfiguration.loadConfiguration(dataFile);

        dataMap.clear();
        for (String uuidStr : dataCfg.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                String name = dataCfg.getString(uuidStr + ".name", "Unknown");
                String race = dataCfg.getString(uuidStr + ".race", null);
                List<String> history = dataCfg.getStringList(uuidStr + ".history");
                int remaining = dataCfg.getInt(uuidStr + ".remaining",
                        plugin.getConfig().getInt("default-random-amount", 3));
                dataMap.put(uuid, new PlayerData(uuid, name, race, history, remaining));
            } catch (IllegalArgumentException ignored) {}
        }
        plugin.getLogger().info("Loaded " + dataMap.size() + " player data entries.");
    }

    public void saveData() {
        for (PlayerData data : dataMap.values()) {
            saveToConfig(data);
        }
        try { dataCfg.save(dataFile); } catch (IOException e) { e.printStackTrace(); }
    }

    public void savePlayer(UUID uuid) {
        PlayerData data = dataMap.get(uuid);
        if (data == null || !data.isDirty()) return;
        saveToConfig(data);
        try { dataCfg.save(dataFile); } catch (IOException e) { e.printStackTrace(); }
        data.setDirty(false);
    }

    private void saveToConfig(PlayerData data) {
        String path = data.getUuid().toString();
        dataCfg.set(path + ".name", data.getPlayerName());
        dataCfg.set(path + ".race", data.getCurrentRace());
        dataCfg.set(path + ".history", data.getHistory());
        dataCfg.set(path + ".remaining", data.getRemainingRandoms());
    }

    public boolean hasData(UUID uuid) { return dataMap.containsKey(uuid); }

    public PlayerData getData(UUID uuid) { return dataMap.get(uuid); }

    public void createData(UUID uuid, String name) {
        int defaultAmount = plugin.getConfig().getInt("default-random-amount", 3);
        PlayerData data = new PlayerData(uuid, name, defaultAmount);
        dataMap.put(uuid, data);
    }

    public void setRace(UUID uuid, String raceId) {
        PlayerData data = dataMap.get(uuid);
        if (data != null) data.setCurrentRace(raceId);
    }

    public void resetRace(UUID uuid) {
        PlayerData data = dataMap.get(uuid);
        if (data != null) {
            data.setCurrentRace(null);
        }
    }
}
