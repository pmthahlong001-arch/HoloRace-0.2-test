package online.racesmp.holorace.models;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PlayerData {

    private final UUID uuid;
    private String playerName;
    private String currentRace;
    private List<String> history;
    private int remainingRandoms;
    private boolean dirty; // cần save

    public PlayerData(UUID uuid, String playerName, int defaultRandoms) {
        this.uuid = uuid;
        this.playerName = playerName;
        this.currentRace = null;
        this.history = new ArrayList<>();
        this.remainingRandoms = defaultRandoms;
        this.dirty = false;
    }

    // Load từ file
    public PlayerData(UUID uuid, String playerName, String currentRace,
                      List<String> history, int remainingRandoms) {
        this.uuid = uuid;
        this.playerName = playerName;
        this.currentRace = currentRace;
        this.history = history != null ? new ArrayList<>(history) : new ArrayList<>();
        this.remainingRandoms = remainingRandoms;
        this.dirty = false;
    }

    public UUID getUuid() { return uuid; }
    public String getPlayerName() { return playerName; }
    public void setPlayerName(String name) { this.playerName = name; dirty = true; }

    public String getCurrentRace() { return currentRace; }
    public void setCurrentRace(String raceId) {
        if (currentRace != null && !currentRace.equals(raceId)) {
            history.add(0, currentRace); // thêm vào đầu lịch sử
            if (history.size() > 10) history = history.subList(0, 10); // giới hạn 10
        }
        this.currentRace = raceId;
        dirty = true;
    }

    public List<String> getHistory() { return history; }

    public int getRemainingRandoms() { return remainingRandoms; }
    public void setRemainingRandoms(int amount) { this.remainingRandoms = amount; dirty = true; }
    public void decrementRandoms() { this.remainingRandoms = Math.max(0, remainingRandoms - 1); dirty = true; }

    public boolean isDirty() { return dirty; }
    public void setDirty(boolean dirty) { this.dirty = dirty; }
}
