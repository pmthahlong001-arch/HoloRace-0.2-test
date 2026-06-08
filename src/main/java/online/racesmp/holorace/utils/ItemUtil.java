package online.racesmp.holorace.utils;

import online.racesmp.holorace.HoloRace;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class ItemUtil {

    // NBT key để nhận biết item
    private static final String ITEM_KEY = "holorace_random_item";

    public static ItemStack buildRandomRaceItem(HoloRace plugin) {
        String matName = plugin.getConfig().getString("random-item.material", "ENDER_EYE");
        Material mat;
        try { mat = Material.valueOf(matName); }
        catch (Exception e) { mat = Material.ENDER_EYE; }

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        // Gradient name: Random Race
        String gradientName = ColorUtil.gradient("✦ Random Race ✦", "#00C6FF", "#FF72FF")
                + ColorUtil.color(" &8(Chuột phải)");
        meta.setDisplayName(gradientName);

        // Lore đẹp với màu gradient
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(ColorUtil.gradient("  ◈ HoloRace Item ◈", "#00C6FF", "#FF72FF"));
        lore.add("");
        lore.add(ColorUtil.color("  &7» &fChuột phải để random tộc"));
        lore.add(ColorUtil.color("  &7» &fCooldown: &e30 phút"));
        lore.add(ColorUtil.color("  &7» &fOP: &aBypass cooldown"));
        lore.add("");
        lore.add(ColorUtil.color("  &8[ " + ColorUtil.gradient("xVioCent_", "#b8ff6a", "#00e5ff") + " &8]"));
        lore.add("");
        meta.setLore(lore);

        // Glow effect
        boolean glowing = plugin.getConfig().getBoolean("random-item.glowing", true);
        if (glowing) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

        // Persistent data để nhận biết item
        var container = meta.getPersistentDataContainer();
        container.set(
                new org.bukkit.NamespacedKey(HoloRace.getInstance(), ITEM_KEY),
                org.bukkit.persistence.PersistentDataType.BYTE,
                (byte) 1
        );

        item.setItemMeta(meta);
        return item;
    }

    public static boolean isRandomRaceItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        var container = meta.getPersistentDataContainer();
        var key = new org.bukkit.NamespacedKey(HoloRace.getInstance(), ITEM_KEY);
        return container.has(key, org.bukkit.persistence.PersistentDataType.BYTE);
    }
}
