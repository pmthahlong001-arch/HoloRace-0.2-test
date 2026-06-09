package online.racesmp.holorace.commands;

import online.racesmp.holorace.HoloRace;
import online.racesmp.holorace.utils.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class HoloRaceCommand implements CommandExecutor {

    private final HoloRace plugin;

    public HoloRaceCommand(HoloRace plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("holorace.admin")) {
                sender.sendMessage("§cBạn không có quyền thực hiện lệnh này!");
                return true;
            }

            plugin.reloadConfig();
            plugin.getRaceManager().loadRaces();
            plugin.getPlayerDataManager().loadData();
            MessageUtil.reload(plugin);

            sender.sendMessage("§a[HoloRace] Đã reload toàn bộ cấu hình hệ thống thành công!");
            return true;
        }

        if (sender instanceof Player) {
            Player player = (Player) sender;
            
            // =====================================================================
            // SỬA LỖI DÒNG 38: HÀM MỞ MENU
            // Bạn hãy ĐỂ LẠI 1 TRONG 3 DÒNG dưới đây sao cho đúng với file GUIManager.java của bạn nhé!
            // =====================================================================
            
            plugin.getGUIManager().openMenu(player); // <-- Giữ dòng này nếu trong GUIManager ghi: public void openMenu(Player player)
            
            // plugin.getGUIManager().openGUI(player);  // <-- Bỏ dấu // ở đầu dòng này nếu trong GUIManager ghi: public void openGUI(Player player)
            
            // plugin.getGUIManager().open(player);     // <-- Bỏ dấu // ở đầu dòng này nếu trong GUIManager ghi: public void open(Player player)
            
        } else {
            sender.sendMessage("§cKhung lệnh này chỉ có thể thực hiện bởi người chơi trong game!");
        }
        return true;
    }
}
