package online.racesmp.holorace.utils;

import net.md_5.bungee.api.ChatColor;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ColorUtil {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    public static String color(String text) {
        if (text == null) return "";
        // Parse hex &#RRGGBB
        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer,
                    ChatColor.of("#" + matcher.group(1)).toString());
        }
        matcher.appendTail(buffer);
        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }

    public static List<String> colorList(List<String> list) {
        List<String> result = new ArrayList<>();
        for (String s : list) result.add(color(s));
        return result;
    }

    /**
     * Tạo gradient text từ màu hex start → end
     * Dùng cho item name gradient đẹp
     */
    public static String gradient(String text, String hexStart, String hexEnd) {
        if (text == null || text.isEmpty()) return "";
        Color start = Color.decode(hexStart);
        Color end = Color.decode(hexEnd);
        int length = text.length();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            float ratio = (float) i / Math.max(length - 1, 1);
            int r = (int) (start.getRed() + ratio * (end.getRed() - start.getRed()));
            int g = (int) (start.getGreen() + ratio * (end.getGreen() - start.getGreen()));
            int b = (int) (start.getBlue() + ratio * (end.getBlue() - start.getBlue()));
            String hex = String.format("#%02X%02X%02X", r, g, b);
            sb.append(ChatColor.of(hex)).append(text.charAt(i));
        }
        return sb.toString();
    }
}
