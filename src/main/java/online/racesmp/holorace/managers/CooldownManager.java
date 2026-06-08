package online.racesmp.holorace.managers;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CooldownManager {

    // Map<UUID, Map<CooldownKey, ExpireTime>>
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();

    public void setCooldown(UUID uuid, String key, long seconds) {
        cooldowns.computeIfAbsent(uuid, k -> new HashMap<>())
                .put(key, System.currentTimeMillis() + (seconds * 1000));
    }

    public boolean isOnCooldown(UUID uuid, String key) {
        Map<String, Long> map = cooldowns.get(uuid);
        if (map == null) return false;
        Long expire = map.get(key);
        if (expire == null) return false;
        if (System.currentTimeMillis() >= expire) {
            map.remove(key);
            return false;
        }
        return true;
    }

    // Trả về giây còn lại
    public long getRemaining(UUID uuid, String key) {
        Map<String, Long> map = cooldowns.get(uuid);
        if (map == null) return 0;
        Long expire = map.get(key);
        if (expire == null) return 0;
        long remaining = (expire - System.currentTimeMillis()) / 1000;
        return Math.max(0, remaining);
    }

    public void clearCooldown(UUID uuid, String key) {
        Map<String, Long> map = cooldowns.get(uuid);
        if (map != null) map.remove(key);
    }

    public void clearAll(UUID uuid) {
        cooldowns.remove(uuid);
    }

    // Format giây → "1p 30s" hoặc "45s"
    public static String formatTime(long seconds) {
        if (seconds >= 60) {
            long mins = seconds / 60;
            long secs = seconds % 60;
            return secs > 0 ? mins + "p " + secs + "s" : mins + "p";
        }
        return seconds + "s";
    }
}
