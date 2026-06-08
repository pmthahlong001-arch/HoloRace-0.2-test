package online.racesmp.holorace.listeners;

import online.racesmp.holorace.HoloRace;
import online.racesmp.holorace.managers.CooldownManager;
import online.racesmp.holorace.models.PlayerData;
import online.racesmp.holorace.models.Race;
import online.racesmp.holorace.utils.MessageUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Random;

public class EntityDamageListener implements Listener {

    private static final String ADRENALINE_KEY = "adrenaline";
    private final HoloRace plugin;
    private final Random random = new Random();

    public EntityDamageListener(HoloRace plugin) {
        this.plugin = plugin;
    }

    // Tộc Người — Adrenaline khi HP < 50%
    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        var pdm = plugin.getPlayerDataManager();
        PlayerData data = pdm.getData(player.getUniqueId());
        if (data == null || data.getCurrentRace() == null) return;
        if (!data.getCurrentRace().equals("human")) return;

        // Tính HP sau khi nhận damage
        double hpAfter = player.getHealth() - event.getFinalDamage();
        double maxHp = player.getMaxHealth();
        double hpPercent = (hpAfter / maxHp) * 100;

        int threshold = plugin.getConfig().getInt("skills.human.adrenaline-hp-threshold", 50);
        if (hpPercent > threshold) return;

        // Check cooldown
        var cm = plugin.getCooldownManager();
        if (cm.isOnCooldown(player.getUniqueId(), ADRENALINE_KEY)) return;

        // Apply Adrenaline từ skill config trong race
        Race race = plugin.getRaceManager().getRace("human");
        if (race == null) return;

        for (Race.SkillConfig skill : race.getSkills()) {
            if (!skill.getType().equals("ADRENALINE")) continue;
            plugin.getRaceManager().applyEffectList(player, skill.getEffectList());
            long cooldown = skill.getInt("cooldown", 120);
            cm.setCooldown(player.getUniqueId(), ADRENALINE_KEY, cooldown);
            player.sendMessage(MessageUtil.get(plugin, "skill-adrenaline"));
            break;
        }
    }

    // Tộc Quỷ — Lifesteal khi crit
    @EventHandler
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;

        var pdm = plugin.getPlayerDataManager();
        PlayerData data = pdm.getData(attacker.getUniqueId());
        if (data == null || data.getCurrentRace() == null) return;
        if (!data.getCurrentRace().equals("demon")) return;

        // Kiểm tra có phải crit không (fall distance > 0 + không sprint)
        boolean isCrit = attacker.getFallDistance() > 0
                && !attacker.isOnGround()
                || event.isCancelled();

        // Bukkit crit detection: player không trên ground + không swim/fly + damage > base
        // Đơn giản hóa: check velocity
        isCrit = attacker.getFallDistance() > 0;

        if (!isCrit) return;

        Race race = plugin.getRaceManager().getRace("demon");
        if (race == null) return;

        for (Race.SkillConfig skill : race.getSkills()) {
            if (!skill.getType().equals("LIFESTEAL")) continue;

            int chance = skill.getInt("chance", 30);
            if (random.nextInt(100) >= chance) continue;

            double stealPercent = skill.getDouble("steal-percent", 30) / 100.0;
            boolean damageTarget = skill.getBoolean("damage-target", false);

            double targetMaxHp = event.getEntity() instanceof Player target ? target.getMaxHealth() : 20.0;
            double stealAmount = targetMaxHp * stealPercent;

            // Hút máu attacker
            double newHp = Math.min(attacker.getMaxHealth(), attacker.getHealth() + stealAmount);
            attacker.setHealth(newHp);

            // Nếu false thì hủy damage phần hút (đối phương không mất máu tương đương)
            // Theo thiết kế: đối phương không mất máu từ lifesteal
            // → không làm gì thêm với damage (damage vẫn apply bình thường từ attack)
            // Chỉ "hút" = attacker được heal

            attacker.sendMessage(MessageUtil.format(plugin, "skill-lifesteal",
                    "%amount%", String.format("%.1f", stealAmount)));
            break;
        }
    }
}
