package online.racesmp.holorace.hooks;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import online.racesmp.holorace.HoloRace;
import online.racesmp.holorace.models.PlayerData;
import online.racesmp.holorace.models.Race;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class PlaceholderHook extends PlaceholderExpansion {

    private final HoloRace plugin;

    public PlaceholderHook(HoloRace plugin) {
        this.plugin = plugin;
    }

    @Override public @NotNull String getIdentifier() { return "holorace"; }
    @Override public @NotNull String getAuthor() { return "xVioCent_"; }
    @Override public @NotNull String getVersion() { return plugin.getDescription().getVersion(); }
    @Override public boolean persist() { return true; }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String identifier) {
        if (player == null) return "";

        PlayerData data = plugin.getPlayerDataManager().getData(player.getUniqueId());

        return switch (identifier) {
            // %holorace_namerace%
            case "namerace" -> {
                if (data == null || data.getCurrentRace() == null) yield "Không có tộc";
                Race race = plugin.getRaceManager().getRace(data.getCurrentRace());
                yield race != null ? race.getDisplayName() : data.getCurrentRace();
            }
            // %holorace_remaining%
            case "remaining" -> {
                if (data == null) yield "0";
                yield String.valueOf(data.getRemainingRandoms());
            }
            default -> null;
        };
    }
}
