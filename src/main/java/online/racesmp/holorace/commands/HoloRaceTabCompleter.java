package online.racesmp.holorace.commands;

import online.racesmp.holorace.HoloRace;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class HoloRaceTabCompleter implements TabCompleter {

    private final HoloRace plugin;

    public HoloRaceTabCompleter(HoloRace plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("check", "help", "chonrace"));
            if (sender.hasPermission("holorace.give")) subs.add("give");
            if (sender.hasPermission("holorace.resetrace")) subs.add("resetrace");
            return filter(subs, args[0]);
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if ((sub.equals("give") || sub.equals("resetrace")) &&
                    (sender.hasPermission("holorace.give") || sender.hasPermission("holorace.resetrace"))) {
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            return List.of("1", "3", "5", "10");
        }

        return completions;
    }

    private List<String> filter(List<String> list, String input) {
        return list.stream()
                .filter(s -> s.toLowerCase().startsWith(input.toLowerCase()))
                .collect(Collectors.toList());
    }
}
