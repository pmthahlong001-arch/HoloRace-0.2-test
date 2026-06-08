package online.racesmp.holorace.managers;

import online.racesmp.holorace.HoloRace;
import online.racesmp.holorace.models.Race;
import online.racesmp.holorace.utils.ColorUtil;
import online.racesmp.holorace.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.*;

public class GUIManager {

    private final HoloRace plugin;
    // Map<Inventory title hash, Map<slot, raceId>>
    private final Map<UUID, Map<Integer, String>> openMenuSlots = new HashMap<>();

    public GUIManager(HoloRace plugin) {
        this.plugin = plugin;
    }

    public void openRaceMenu(Player player) {
        File menuFile = new File(plugin.getDataFolder(), "menu.yml");
        YamlConfiguration menuCfg = YamlConfiguration.loadConfiguration(menuFile);

        String title = ColorUtil.color(menuCfg.getString("menu.title", "✦ Chọn Tộc ✦"));
        int rows = menuCfg.getInt("menu.rows", 3);
        Inventory inv = Bukkit.createInventory(null, rows * 9, title);

        // Fill background
        String bgMat = menuCfg.getString("menu.background.material", "BLACK_STAINED_GLASS_PANE");
        String bgName = menuCfg.getString("menu.background.name", " ");
        List<Integer> bgSlots = menuCfg.getIntegerList("menu.background.slots");
        ItemStack bg = buildSimpleItem(bgMat, bgName, null);
        for (int slot : bgSlots) {
            if (slot >= 0 && slot < rows * 9) inv.setItem(slot, bg);
        }

        // Race items
        List<Integer> raceSlots = menuCfg.getIntegerList("menu.race-slots");
        List<Race> allRaces = new ArrayList<>(plugin.getRaceManager().getAllRaces());
        Map<Integer, String> slotMap = new HashMap<>();

        String currentRace = null;
        var data = plugin.getPlayerDataManager().getData(player.getUniqueId());
        if (data != null) currentRace = data.getCurrentRace();

        for (int i = 0; i < allRaces.size() && i < raceSlots.size(); i++) {
            Race race = allRaces.get(i);
            int slot = raceSlots.get(i);
            ItemStack item = buildRaceItem(race, race.getId().equals(currentRace));
            inv.setItem(slot, item);
            slotMap.put(slot, race.getId());
        }

        // Close button
        int closeSlot = menuCfg.getInt("menu.close-button.slot", 22);
        String closeMat = menuCfg.getString("menu.close-button.material", "BARRIER");
        String closeName = menuCfg.getString("menu.close-button.name", "&cĐóng");
        List<String> closeLore = menuCfg.getStringList("menu.close-button.lore");
        inv.setItem(closeSlot, buildSimpleItem(closeMat, closeName, closeLore));
        slotMap.put(closeSlot, "CLOSE");

        openMenuSlots.put(player.getUniqueId(), slotMap);
        player.openInventory(inv);
    }

    private ItemStack buildRaceItem(Race race, boolean selected) {
        Material mat;
        try { mat = Material.valueOf(race.getIconMaterial()); }
        catch (Exception e) { mat = Material.STONE; }

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName(ColorUtil.color(race.getDisplayName()));
        List<String> lore = new ArrayList<>();
        for (String line : race.getDescription()) {
            lore.add(ColorUtil.color(line));
        }
        lore.add("");
        if (selected) {
            lore.add(ColorUtil.color("&a✔ Đây là tộc hiện tại của bạn"));
        } else {
            lore.add(ColorUtil.color("&eClick để chọn tộc này"));
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildSimpleItem(String materialName, String name, List<String> lore) {
        Material mat;
        try { mat = Material.valueOf(materialName); }
        catch (Exception e) { mat = Material.STONE; }
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName(ColorUtil.color(name));
        if (lore != null && !lore.isEmpty()) {
            List<String> colored = new ArrayList<>();
            for (String l : lore) colored.add(ColorUtil.color(l));
            meta.setLore(colored);
        }
        item.setItemMeta(meta);
        return item;
    }

    public String getClickedRace(UUID uuid, int slot) {
        Map<Integer, String> map = openMenuSlots.get(uuid);
        if (map == null) return null;
        return map.get(slot);
    }

    public void clearMenu(UUID uuid) {
        openMenuSlots.remove(uuid);
    }
}
