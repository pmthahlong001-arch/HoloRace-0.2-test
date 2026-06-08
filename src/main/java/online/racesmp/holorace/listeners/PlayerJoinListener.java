package online.racesmp.holorace.listeners;

import online.racesmp.holorace.HoloRace;
import online.racesmp.holorace.models.PlayerData;
import online.racesmp.holorace.models.Race;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerJoinListener implements Listener {

    private final HoloRace plugin;

    public PlayerJoinListener(HoloRace plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        var pdm = plugin.getPlayerDataManager();
        var rm = plugin.getRaceManager();

        // Tạo data nếu chưa có
        if (!pdm.hasData(player.getUniqueId())) {
            pdm.createData(player.getUniqueId(), player.getName());
            // RaceManager xử lý auto random
            rm.handleJoin(player);
        } else {
            // Update tên nếu đổi
            PlayerData data = pdm.getData(player.getUniqueId());
            if (data != null) data.setPlayerName(player.getName());
        }

        // Apply passive effects race hiện tại - delay 1 tick
        PlayerData data = pdm.getData(player.getUniqueId());
        if (data != null && data.getCurrentRace() != null) {
            Race race = rm.getRace(data.getCurrentRace());
            plugin.getServer().getRegionScheduler().runDelayed(
                    plugin,
                    player.getLocation(),
                    task -> {
                        if (player.isOnline()) rm.applyPassiveEffects(player, race);
                    },
                    2L
            );
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.getRaceManager().removeEffects(player);
        plugin.getPlayerDataManager().savePlayer(player.getUniqueId());
        plugin.getCooldownManager().clearAll(player.getUniqueId());
        plugin.getGUIManager().clearMenu(player.getUniqueId());
    }
}
