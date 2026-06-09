package online.racesmp.holorace.listeners;

import online.racesmp.holorace.HoloRace;
import online.racesmp.holorace.models.PlayerData;
import online.racesmp.holorace.models.Race;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class RaceSkillListener implements Listener {

    private final HoloRace plugin;
    // Track trạng thái trước để không apply lặp
    private final Map<UUID, Boolean> wasInWater = new HashMap<>();
    private final Map<UUID, Boolean> wasNight = new HashMap<>();

    public RaceSkillListener(HoloRace plugin) {
        this.plugin = plugin;
        startTickTask();
    }

    private void startTickTask() {
        // Dùng global scheduler vì tick không cần region cụ thể
        plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, task -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                tickPlayer(player);
            }
        }, 20L, 20L); // mỗi 1 giây
    }

    private void tickPlayer(Player player) {
        var pdm = plugin.getPlayerDataManager();
        PlayerData data = pdm.getData(player.getUniqueId());
        if (data == null || data.getCurrentRace() == null) return;

        Race race = plugin.getRaceManager().getRace(data.getCurrentRace());
        if (race == null) return;

        boolean inWater = player.isInWater();
        boolean isNight = player.getWorld().getTime() >= 13000 && player.getWorld().getTime() <= 23000;

        boolean prevWater = wasInWater.getOrDefault(player.getUniqueId(), false);
        boolean prevNight = wasNight.getOrDefault(player.getUniqueId(), false);

        boolean waterChanged = inWater != prevWater;
        boolean nightChanged = isNight != prevNight;

        wasInWater.put(player.getUniqueId(), inWater);
        wasNight.put(player.getUniqueId(), isNight);

        for (Race.SkillConfig skill : race.getSkills()) {
            String type = skill.getType();
            String trigger = skill.getTrigger();

            if (!type.equals("PASSIVE")) continue;

            switch (trigger) {
                case "IN_WATER" -> {
                    if (waterChanged) {
                        if (inWater) {
                            // Vào nước: remove ON_LAND, apply IN_WATER
                            removeEffectsForTrigger(player, race, "ON_LAND");
                            plugin.getRaceManager().applyEffectList(player, skill.getEffectList());
                        } else {
                            // Ra khỏi nước: remove IN_WATER, apply ON_LAND
                            removeEffectsForTrigger(player, race, "IN_WATER");
                            applyTrigger(player, race, "ON_LAND");
                        }
                    }
                }
                case "ON_LAND" -> {
                    if (waterChanged && !inWater) {
                        plugin.getRaceManager().applyEffectList(player, skill.getEffectList());
                    }
                }
                case "NIGHT" -> {
                    if (nightChanged) {
                        if (isNight) {
                            plugin.getRaceManager().applyEffectList(player, skill.getEffectList());
                        } else {
                            // Ban ngày: remove night effects
                            removeEffectsForTrigger(player, race, "NIGHT");
                        }
                    }
                }
            }
        }
    }

    private void removeEffectsForTrigger(Player player, Race race, String trigger) {
        for (Race.SkillConfig skill : race.getSkills()) {
            if (!skill.getType().equals("PASSIVE") || !skill.getTrigger().equals(trigger)) continue;
            for (var eff : skill.getEffectList()) {
                String effectName = eff.getOrDefault("effect", "").toString();
                PotionEffectType type = PotionEffectType.getByName(effectName);
                
                // Giải pháp: Kiểm tra hiệu ứng có tồn tại trên người chơi không rồi xoá trực tiếp theo Type
                if (type != null && player.hasPotionEffect(type)) {
                    player.removePotionEffect(type);
                }
            }
        }
    }

    private void applyTrigger(Player player, Race race, String trigger) {
        for (Race.SkillConfig skill : race.getSkills()) {
            if (!skill.getType().equals("PASSIVE") || !skill.getTrigger().equals(trigger)) continue;
            plugin.getRaceManager().applyEffectList(player, skill.getEffectList());
        }
    }

    public void cleanup(UUID uuid) {
        wasInWater.remove(uuid);
        wasNight.remove(uuid);
    }
}
