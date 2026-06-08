package online.racesmp.holorace.listeners;

import online.racesmp.holorace.HoloRace;
import online.racesmp.holorace.managers.CooldownManager;
import online.racesmp.holorace.models.PlayerData;
import online.racesmp.holorace.models.Race;
import online.racesmp.holorace.utils.ColorUtil;
import online.racesmp.holorace.utils.ItemUtil;
import online.racesmp.holorace.utils.MessageUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class PlayerItemListener implements Listener {

    private static final String COOLDOWN_KEY = "random_race_item";

    private final HoloRace plugin;

    public PlayerItemListener(HoloRace plugin) {
        this.plugin = plugin;
    }

    // Chặn vứt item (trừ OP)
    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItemDrop().getItemStack();
        if (!ItemUtil.isRandomRaceItem(item)) return;

        boolean dropPrevention = plugin.getConfig().getBoolean("random-item.drop-prevention", true);
        if (dropPrevention && !player.hasPermission("holorace.op")) {
            event.setCancelled(true);
        }
    }

    // Chuột phải item → random race
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR &&
            event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!ItemUtil.isRandomRaceItem(item)) return;

        event.setCancelled(true);

        var pdm = plugin.getPlayerDataManager();
        var rm = plugin.getRaceManager();
        var cm = plugin.getCooldownManager();

        // Check cooldown (OP bypass)
        if (!player.hasPermission("holorace.op")) {
            if (cm.isOnCooldown(player.getUniqueId(), COOLDOWN_KEY)) {
                long remaining = cm.getRemaining(player.getUniqueId(), COOLDOWN_KEY);
                player.sendMessage(MessageUtil.format(plugin, "random-cooldown",
                        "%time%", CooldownManager.formatTime(remaining)));
                return;
            }
        }

        // Random race
        Race race = rm.randomRace();
        if (race == null) {
            player.sendMessage(MessageUtil.get(plugin, "random-no-races"));
            return;
        }

        // Remove effect race cũ
        rm.removeEffects(player);

        // Set race mới
        pdm.setRace(player.getUniqueId(), race.getId());

        // Apply effects race mới
        plugin.getServer().getRegionScheduler().runDelayed(plugin, player.getLocation(),
                task -> rm.applyPassiveEffects(player, race), 1L);

        // Giảm số lượt
        PlayerData data = pdm.getData(player.getUniqueId());
        if (data != null) data.decrementRandoms();

        // Trừ 1 item
        item.setAmount(item.getAmount() - 1);

        // Set cooldown
        long cooldown = plugin.getConfig().getLong("random-item-cooldown", 1800);
        cm.setCooldown(player.getUniqueId(), COOLDOWN_KEY, cooldown);

        player.sendMessage(MessageUtil.format(plugin, "random-success",
                "%race%", race.getDisplayName()));
    }

    // GUI click - chọn race
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String title = event.getView().getTitle();
        // Kiểm tra đây có phải menu HoloRace không
        if (!title.contains("Chọn Tộc")) return;

        event.setCancelled(true);

        int slot = event.getRawSlot();
        String raceId = plugin.getGUIManager().getClickedRace(player.getUniqueId(), slot);
        if (raceId == null) return;

        if (raceId.equals("CLOSE")) {
            player.closeInventory();
            plugin.getGUIManager().clearMenu(player.getUniqueId());
            return;
        }

        Race race = plugin.getRaceManager().getRace(raceId);
        if (race == null) return;

        // Remove effect cũ
        plugin.getRaceManager().removeEffects(player);

        // Set race mới
        plugin.getPlayerDataManager().setRace(player.getUniqueId(), raceId);

        // Apply effects
        plugin.getServer().getRegionScheduler().runDelayed(plugin, player.getLocation(),
                task -> plugin.getRaceManager().applyPassiveEffects(player, race), 1L);

        player.closeInventory();
        plugin.getGUIManager().clearMenu(player.getUniqueId());
        player.sendMessage(MessageUtil.format(plugin, "random-success",
                "%race%", race.getDisplayName()));
    }
}
