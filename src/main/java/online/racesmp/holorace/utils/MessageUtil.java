package online.racesmp.holorace.utils;

import online.racesmp.holorace.HoloRace;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

public class MessageUtil {

    private static YamlConfiguration langCfg;

    public static void reload(HoloRace plugin) {
        File langFile = new File(plugin.getDataFolder(), "lang.yml");
        langCfg = YamlConfiguration.loadConfiguration(langFile);
    }

    public static String get(HoloRace plugin, String key) {
        if (langCfg == null) reload(plugin);
        String prefix = ColorUtil.color(plugin.getConfig().getString("prefix", "&8[&bHoloRace&8]&r"));
        String msg = langCfg.getString("messages." + key, "&cMissing message: " + key);
        return ColorUtil.color(msg.replace("%prefix%", prefix));
    }

    public static String format(HoloRace plugin, String key, String... replacements) {
        String msg = get(plugin, key);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            msg = msg.replace(replacements[i], replacements[i + 1]);
        }
        return msg;
    }
}
