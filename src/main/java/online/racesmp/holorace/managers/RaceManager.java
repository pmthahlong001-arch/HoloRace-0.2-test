package online.racesmp.holorace.managers;

import online.racesmp.holorace.HoloRace;
import online.racesmp.holorace.models.PlayerData;
import online.racesmp.holorace.models.Race;
import online.racesmp.holorace.utils.MessageUtil;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.util.*;

public class RaceManager {

    private final HoloRace plugin;
    private final Map<String, Race> races = new LinkedHashMap<>();
    private final Map<String, Integer> weights = new HashMap<>();

    public RaceManager(HoloRace plugin) {
        this.plugin = plugin;
    }

    public void loadRaces() {
        races.clear();
        weights.clear();

        File racesFolder = new File(plugin.getDataFolder(), "races");
        if (!racesFolder.exists()) racesFolder.mkdirs();

        File[] files = racesFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return;

        for (File file : files) {
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            String id = cfg.getString("id", file.getName().replace(".yml", ""));
            Race race = new Race(id, cfg);
            races.put(id, race);
            if (plugin.isDebugMode()) plugin.getLogger().info("Loaded race: " + id);
        }

        // Load weights
        File weightFile = new File(plugin.getDataFolder(), "weight.yml");
        if (weightFile.exists()) {
            YamlConfiguration wCfg = YamlConfiguration.loadConfiguration(weightFile);
            ConfigurationSection section = wCfg.getConfigurationSection("weights");
            if (section != null) {
                for (String key : section.getKeys(false)) {
                    weights.put(key, section.getInt(key, 0));
                }
            }
        }

        plugin.getLogger().info("Loaded " + races.size() + " races, " + weights.size() + " weighted.");
    }

    public Race getRace(String id) {
        return races.get(id);
    }

    public Collection<Race> getAllRaces() {
        return races.values();
    }

    public Map<String, Race> getRaceMap() {
        return races;
    }

    // Chỉ trả về race có trong weight (player random được)
    public Race randomRace() {
        if (weights.isEmpty()) return null;
        int total = weights.values().stream().mapToInt(Integer::intValue).sum();
        if (total <= 0) return null;

        int roll = new Random().nextInt(total);
        int cumulative = 0;
        for (Map.Entry<String, Integer> entry : weights.entrySet()) {
            cumulative += entry.getValue();
            if (roll < cumulative) {
                return races.get(entry.getKey());
            }
        }
        return null;
    }

    public boolean isWeighted(String raceId) {
        return weights.containsKey(raceId);
    }

    // Xử lý khi player join - auto random
    public void handleJoin(Player player) {
        boolean autoRandom = plugin.getConfig().getBoolean("auto-random-on-join", true);
        if (!autoRandom) return;

        PlayerDataManager pdm = plugin.getPlayerDataManager();
        PlayerData data = pdm.getData(player.getUniqueId());
        if (data == null || data.getCurrentRace() != null) return;

        Race race = randomRace();
        if (race == null) return;

        pdm.setRace(player.getUniqueId(), race.getId());
        
        // Đã fix: Ép kiểu Object thu được từ format về chuỗi String để loại bỏ hoàn toàn lỗi capture ? của Maven
        Object rawMessage = MessageUtil.format(plugin, "auto-random-race");
        if (rawMessage != null) {
            player.sendMessage(rawMessage.toString().replace("%race%", race.getDisplayName()));
        }
    }

    // Apply passive effects khi login/assign race
    public void applyPassiveEffects(Player player, Race race) {
        if (race == null) return;
        for (Race.SkillConfig skill : race.getSkills()) {
            String type = skill.getType();
            String trigger = skill.getTrigger();

            if (type.equals("PASSIVE") && trigger.equals("ALWAYS")) {
                applyEffectList(player, skill.getEffectList());
            }
            if (type.equals("PASSIVE") && trigger.equals("ON_LAND")) {
                if (!player.isInWater()) applyEffectList(player, skill.getEffectList());
            }
            if (type.equals("SCALE")) {
                double scale = skill.getDouble("value", 1.0);
                applyScale(player, scale);
            }
        }
    }

    public void applyEffectList(Player player, List<java.util.Map<?, ?>> effects) {
        if (effects == null) return;
        for (java.util.Map<?, ?> eff : effects) {
            if (eff == null) continue;
            String effectName = eff.getOrDefault("effect", "").toString();
            int amplifier = 0;
            int duration = -1;
            
            try {
                amplifier = Integer.parseInt(eff.getOrDefault("amplifier", "0").toString());
                duration = Integer.parseInt(eff.getOrDefault("duration", "-1").toString());
            } catch (NumberFormatException ignored) {}

            PotionEffectType type = PotionEffectType.getByName(effectName);
            if (type == null) continue;

            if (duration == -1) {
                player.addPotionEffect(new PotionEffect(type, PotionEffect.INFINITE_DURATION, amplifier, true, false, false));
            } else {
                player.addPotionEffect(new PotionEffect(type, duration, amplifier, true, false, false));
            }
        }
    }

    public void removeEffects(Player player) {
        // Remove tất cả potion effects do race gây ra
        for (PotionEffect effect : new ArrayList<>(player.getActivePotionEffects())) {
            player.removePotionEffect(effect.getType());
        }
        // Reset scale
        applyScale(player, 1.0);
    }

    public void applyScale(Player player, double scale) {
        try {
            var attr = player.getAttribute(Attribute.SCALE);
            if (attr != null) attr.setBaseValue(scale);
        } catch (Exception ignored) {}
    }
}
