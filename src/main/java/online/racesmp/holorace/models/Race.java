package online.racesmp.holorace.models;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

public class Race {

    private final String id;
    private final String name;
    private final String displayName;
    private final List<String> description;
    private final String iconMaterial;
    private final String iconTexture;
    private final List<SkillConfig> skills;

    public Race(String id, ConfigurationSection section) {
        this.id = id;
        this.name = section.getString("name", id);
        this.displayName = section.getString("display-name", name);
        this.description = section.getStringList("description");
        this.iconMaterial = section.getString("icon.material", "STONE");
        this.iconTexture = section.getString("icon.texture", null);
        this.skills = new ArrayList<>();

        // Load skills
        List<?> skillList = section.getList("skills");
        if (skillList != null) {
            for (Object obj : skillList) {
                if (obj instanceof java.util.Map<?, ?> map) {
                    skills.add(new SkillConfig(map));
                }
            }
        }
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDisplayName() { return displayName; }
    public List<String> getDescription() { return description; }
    public String getIconMaterial() { return iconMaterial; }
    public String getIconTexture() { return iconTexture; }
    public List<SkillConfig> getSkills() { return skills; }

    // Inner class lưu config của từng skill trong file race
    public static class SkillConfig {
        private final String type;
        private final String trigger;
        private final java.util.Map<?, ?> raw;

        public SkillConfig(java.util.Map<?, ?> map) {
            this.raw = map;
            this.type = map.getOrDefault("type", "PASSIVE").toString().toUpperCase();
            this.trigger = map.getOrDefault("trigger", "ALWAYS").toString().toUpperCase();
        }

        public String getType() { return type; }
        public String getTrigger() { return trigger; }

        public double getDouble(String key, double def) {
            Object val = raw.get(key);
            if (val == null) return def;
            try { return Double.parseDouble(val.toString()); } catch (Exception e) { return def; }
        }

        public int getInt(String key, int def) {
            Object val = raw.get(key);
            if (val == null) return def;
            try { return Integer.parseInt(val.toString()); } catch (Exception e) { return def; }
        }

        public boolean getBoolean(String key, boolean def) {
            Object val = raw.get(key);
            if (val == null) return def;
            return Boolean.parseBoolean(val.toString());
        }

        @SuppressWarnings("unchecked")
        public List<java.util.Map<?, ?>> getEffectList() {
            Object val = raw.get("effects");
            if (val instanceof List<?> list) {
                List<java.util.Map<?, ?>> result = new ArrayList<>();
                for (Object o : list) {
                    if (o instanceof java.util.Map<?, ?> m) result.add(m);
                }
                return result;
            }
            return new ArrayList<>();
        }
    }
}
