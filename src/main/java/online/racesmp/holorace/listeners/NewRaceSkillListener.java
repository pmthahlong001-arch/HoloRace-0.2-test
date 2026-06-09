package online.racesmp.holorace.listeners;

import online.racesmp.holorace.HoloRace;
import online.racesmp.holorace.managers.CooldownManager;
import online.racesmp.holorace.models.PlayerData;
import online.racesmp.holorace.models.Race;
import online.racesmp.holorace.utils.MessageUtil;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.lang.reflect.Method;
import java.util.*;

public class NewRaceSkillListener implements Listener {

    private final HoloRace plugin;
    private final Random random = new Random();

    // Tộc Thú - combo tracker
    private final Map<UUID, Integer> comboCount = new HashMap<>();
    private final Map<UUID, Long> lastHitTime = new HashMap<>();

    // Tộc Ma - tàng hình & backstab
    private final Map<UUID, Long> veilActiveUntil = new HashMap<>();
    private final Map<UUID, Long> backstabWindowUntil = new HashMap<>();

    // Tộc Rồng - breath task tracking
    private final Map<UUID, Boolean> breathActive = new HashMap<>();

    public NewRaceSkillListener(HoloRace plugin) {
        this.plugin = plugin;
        startPassiveTick();
    }

    // ─────────────────────────────────────────────────────────
    //  TICK TASK — Celestial Shield HP check
    // ─────────────────────────────────────────────────────────
    private void startPassiveTick() {
        plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, task -> {
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                String race = getRaceId(p);
                if (race == null) continue;
                switch (race) {
                    case "celestial" -> tickCelestialShield(p);
                }
            }
        }, 10L, 10L);
    }

    private void tickCelestialShield(Player p) {
        double hpPercent = (p.getHealth() / p.getMaxHealth()) * 100;
        if (hpPercent > 20) return;
        var cm = plugin.getCooldownManager();
        if (cm.isOnCooldown(p.getUniqueId(), "celestial_shield")) return;

        Race race = plugin.getRaceManager().getRace("celestial");
        if (race == null) return;
        for (Race.SkillConfig skill : race.getSkills()) {
            if (!skill.getType().equals("CELESTIAL_SHIELD")) continue;
            int hearts = skill.getInt("absorption-hearts", 8);
            int dur = skill.getInt("shield-duration", 200);
            p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, dur, hearts / 4, true, false, true));
            long cd = skill.getInt("cooldown", 45);
            cm.setCooldown(p.getUniqueId(), "celestial_shield", cd);
            p.sendMessage(MessageUtil.get(plugin, "skill-celestial-shield"));
            p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.2f);
            p.spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.05);
            break;
        }
    }

    // ─────────────────────────────────────────────────────────
    //  DAMAGE EVENTS — Rage, Haunt, Flame Body, Lifesteal, Combo
    // ─────────────────────────────────────────────────────────
    @EventHandler(priority = EventPriority.NORMAL)
    public void onDamageReceived(EntityDamageByEntityEvent event) {
        // --- Tộc Ma: thoát tàng hình khi nhận damage ---
        if (event.getEntity() instanceof Player victim) {
            if (veilActiveUntil.containsKey(victim.getUniqueId())) {
                cancelVeil(victim);
            }

            String raceId = getRaceId(victim);
            if (raceId == null) return;

            Race race = plugin.getRaceManager().getRace(raceId);
            if (race == null) return;

            switch (raceId) {
                case "beast" -> handleBeastRage(victim, race);
                case "ghost" -> handleGhostHaunt(victim, race, event.getDamager());
                case "dragon" -> handleDragonFlameBody(victim, race, event.getDamager());
            }
        }

        // --- Lifesteal + Combo + Backstab khi tấn công ---
        if (event.getDamager() instanceof Player attacker) {
            String raceId = getRaceId(attacker);
            if (raceId == null) return;
            Race race = plugin.getRaceManager().getRace(raceId);
            if (race == null) return;

            switch (raceId) {
                case "ghost" -> handleGhostLifesteal(attacker, race, event);
                case "beast" -> handleBeastCombo(attacker, race, event);
            }

            // Tộc Ma backstab
            if (raceId.equals("ghost")) {
                Long bsWindow = backstabWindowUntil.get(attacker.getUniqueId());
                if (bsWindow != null && System.currentTimeMillis() < bsWindow) {
                    Race ghostRace = plugin.getRaceManager().getRace("ghost");
                    if (ghostRace != null) {
                        for (Race.SkillConfig s : ghostRace.getSkills()) {
                            if (!s.getType().equals("GHOST_VEIL")) continue;
                            double mult = s.getDouble("backstab-multiplier", 2.5);
                            event.setDamage(event.getDamage() * mult);
                            backstabWindowUntil.remove(attacker.getUniqueId());
                            attacker.sendMessage(MessageUtil.get(plugin, "skill-ghost-backstab"));
                            attacker.playSound(attacker.getLocation(), Sound.ENTITY_CREEPER_DEATH, 1f, 1.5f);
                            attacker.spawnParticle(Particle.CRIT, attacker.getLocation().add(0,1,0), 15, 0.2, 0.2, 0.2, 0.1);
                            break;
                        }
                    }
                }
            }
        }
    }

    private void handleBeastRage(Player player, Race race) {
        var cm = plugin.getCooldownManager();
        if (cm.isOnCooldown(player.getUniqueId(), "beast_rage")) return;

        for (Race.SkillConfig skill : race.getSkills()) {
            if (!skill.getType().equals("BEAST_RAGE")) continue;
            int chance = skill.getInt("chance", 20);
            if (random.nextInt(100) >= chance) return;

            plugin.getRaceManager().applyEffectList(player, skill.getEffectList());
            long cd = skill.getInt("cooldown", 15);
            cm.setCooldown(player.getUniqueId(), "beast_rage", cd);
            player.sendMessage(MessageUtil.get(plugin, "skill-beast-rage"));
            player.playSound(player.getLocation(), Sound.ENTITY_WOLF_GROWL, 1f, 0.8f);
            player.spawnParticle(Particle.FLAME, player.getLocation().add(0,1,0), 15, 0.3, 0.3, 0.3, 0.05);
            break;
        }
    }

    private void handleBeastCombo(Player attacker, Race race, EntityDamageByEntityEvent event) {
        for (Race.SkillConfig skill : race.getSkills()) {
            if (!skill.getType().equals("BEAST_COMBO")) continue;

            int window = skill.getInt("combo-window", 80);
            long now = System.currentTimeMillis();
            long windowMs = (window * 50L);

            Long last = lastHitTime.get(attacker.getUniqueId());
            if (last == null || (now - last) > windowMs) {
                comboCount.put(attacker.getUniqueId(), 1);
            } else {
                int count = comboCount.getOrDefault(attacker.getUniqueId(), 0) + 1;
                comboCount.put(attacker.getUniqueId(), count);

                int needed = skill.getInt("combo-count", 3);
                if (count >= needed) {
                    double mult = skill.getDouble("damage-multiplier", 2.0);
                    event.setDamage(event.getDamage() * mult);

                    if (event.getEntity() instanceof LivingEntity target) {
                        double kv = skill.getDouble("knockup-velocity", 1.2);
                        Vector up = new Vector(0, kv, 0);
                        plugin.getServer().getRegionScheduler().run(plugin, target.getLocation(), t ->
                                target.setVelocity(up));
                    }
                    comboCount.put(attacker.getUniqueId(), 0);
                    attacker.sendMessage(MessageUtil.get(plugin, "skill-beast-combo"));
                    attacker.playSound(attacker.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.7f);
                    attacker.spawnParticle(Particle.CRIT, attacker.getLocation().add(0,1,0), 20, 0.5, 0.5, 0.5, 0.1);
                }
            }
            lastHitTime.put(attacker.getUniqueId(), now);
            break;
        }
    }

    private void handleGhostHaunt(Player victim, Race race, Entity attacker) {
        if (!(attacker instanceof LivingEntity living)) return;
        var cm = plugin.getCooldownManager();
        if (cm.isOnCooldown(victim.getUniqueId(), "ghost_haunt")) return;

        for (Race.SkillConfig skill : race.getSkills()) {
            if (!skill.getType().equals("GHOST_HAUNT")) continue;
            int chance = skill.getInt("chance", 25);
            if (random.nextInt(100) >= chance) return;

            for (Object effObj : skill.getEffectList()) {
                if (!(effObj instanceof Map<?, ?> eff)) continue;
                
                Object rawEffect = eff.get("effect");
                Object rawAmplifier = eff.get("amplifier");
                Object rawDuration = eff.get("duration");

                String name = rawEffect != null ? rawEffect.toString() : "";
                int amp = 0;
                int dur = 20;

                try {
                    if (rawAmplifier != null) amp = Integer.parseInt(rawAmplifier.toString());
                    if (rawDuration != null) dur = Integer.parseInt(rawDuration.toString());
                } catch (NumberFormatException ignored) {}

                PotionEffectType type = PotionEffectType.getByName(name);
                if (type != null) living.addPotionEffect(new PotionEffect(type, dur, amp, true, false, false));
            }
            long cd = skill.getInt("cooldown", 10);
            cm.setCooldown(victim.getUniqueId(), "ghost_haunt", cd);
            victim.sendMessage(MessageUtil.get(plugin, "skill-ghost-haunt"));
            victim.playSound(victim.getLocation(), Sound.ENTITY_PHANTOM_BITE, 1f, 0.6f);
            victim.spawnParticle(Particle.ASH, victim.getLocation().add(0,1,0), 20, 0.5, 0.5, 0.5, 0.05);
            break;
        }
    }

    private void handleGhostLifesteal(Player attacker, Race race, EntityDamageByEntityEvent event) {
        for (Race.SkillConfig skill : race.getSkills()) {
            if (!skill.getType().equals("GHOST_LIFESTEAL")) continue;
            double heal = skill.getDouble("heal-per-hit", 2.0);
            double newHp = Math.min(attacker.getMaxHealth(), attacker.getHealth() + heal);
            plugin.getServer().getRegionScheduler().run(plugin, attacker.getLocation(), t ->
                    attacker.setHealth(newHp));
            break;
        }
    }

    private void handleDragonFlameBody(Player victim, Race race, Entity attacker) {
        if (!(attacker instanceof LivingEntity living)) return;
        var cm = plugin.getCooldownManager();
        if (cm.isOnCooldown(victim.getUniqueId(), "dragon_flame_body")) return;

        for (Race.SkillConfig skill : race.getSkills()) {
            if (!skill.getType().equals("DRAGON_FLAME_BODY")) continue;
            int chance = skill.getInt("chance", 30);
            if (random.nextInt(100) >= chance) return;

            int ticks = skill.getInt("ignite-ticks", 80);
            living.setFireTicks(ticks);
            long cd = skill.getInt("cooldown", 5);
            cm.setCooldown(victim.getUniqueId(), "dragon_flame_body", cd);
            victim.sendMessage(MessageUtil.get(plugin, "skill-dragon-flamebody"));
            victim.playSound(victim.getLocation(), Sound.ENTITY_BLAZE_HURT, 1f, 1f);
            victim.spawnParticle(Particle.FLAME, victim.getLocation().add(0,1,0), 15, 0.3, 0.3, 0.3, 0.05);
            break;
        }
    }

    // ─────────────────────────────────────────────────────────
    //  SHIFT + RIGHT_CLICK — Active Skills
    // ─────────────────────────────────────────────────────────
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!event.getPlayer().isSneaking()) return;

        Player player = event.getPlayer();
        if (plugin.getPlayerDataManager() == null) return;

        String raceId = getRaceId(player);
        if (raceId == null) return;
        Race race = plugin.getRaceManager().getRace(raceId);
        if (race == null) return;

        switch (raceId) {
            case "beast" -> useBeastLeap(player, race, event);
            case "celestial" -> useCelestialStep(player, race, event);
            case "dragon" -> useDragonBreath(player, race, event);
            case "ghost" -> useGhostVeil(player, race, event);
        }
    }

    private void useBeastLeap(Player player, Race race, PlayerInteractEvent event) {
        for (Race.SkillConfig skill : race.getSkills()) {
            if (!skill.getType().equals("BEAST_LEAP")) continue;
            var cm = plugin.getCooldownManager();
            if (cm.isOnCooldown(player.getUniqueId(), "beast_leap")) {
                long rem = cm.getRemaining(player.getUniqueId(), "beast_leap");
                player.sendMessage(MessageUtil.format(plugin, "skill-cooldown", "%time%", CooldownManager.formatTime(rem)));
                return;
            }
            event.setCancelled(true);

            double range = skill.getDouble("range", 10);
            LivingEntity target = findNearestEnemy(player, range);
            if (target == null) {
                player.sendMessage(MessageUtil.get(plugin, "skill-no-target"));
                return;
            }

            Vector dir = target.getLocation().toVector().subtract(player.getLocation().toVector()).normalize().multiply(2.5);
            dir.setY(0.5);

            plugin.getServer().getRegionScheduler().run(plugin, player.getLocation(), t -> player.setVelocity(dir));

            plugin.getServer().getRegionScheduler().runDelayed(plugin, player.getLocation(), t -> {
                if (player.getLocation().distanceSquared(target.getLocation()) < 9) {
                    double dmg = skill.getDouble("damage", 8.0);
                    int slowAmp = skill.getInt("slow-amplifier", 2);
                    int slowDur = skill.getInt("slow-duration", 60);
                    target.damage(dmg, player);
                    target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, slowDur, slowAmp, true, false, false));
                    target.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0,1,0), 20, 0.3, 0.3, 0.3, 0.1);
                    target.getWorld().playSound(target.getLocation(), Sound.ENTITY_WOLF_HURT, 1f, 0.8f);
                }
            }, 10L);

            long cd = skill.getInt("cooldown", 12);
            cm.setCooldown(player.getUniqueId(), "beast_leap", cd);
            player.sendMessage(MessageUtil.get(plugin, "skill-beast-leap"));
            player.playSound(player.getLocation(), Sound.ENTITY_WOLF_GROWL, 1f, 1.2f);
            break;
        }
    }

    private void useCelestialStep(Player player, Race race, PlayerInteractEvent event) {
        for (Race.SkillConfig skill : race.getSkills()) {
            if (!skill.getType().equals("CELESTIAL_STEP")) continue;
            var cm = plugin.getCooldownManager();
            if (cm.isOnCooldown(player.getUniqueId(), "celestial_step")) {
                long rem = cm.getRemaining(player.getUniqueId(), "celestial_step");
                player.sendMessage(MessageUtil.format(plugin, "skill-cooldown", "%time%", CooldownManager.formatTime(rem)));
                return;
            }
            event.setCancelled(true);

            double maxRange = skill.getDouble("max-range", 15);
            Location target = getTargetLocation(player, maxRange);
            Location from = player.getLocation().clone();

            plugin.getServer().getRegionScheduler().run(plugin, player.getLocation(), t -> {
                player.teleport(target);
                player.playSound(target, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.2f);
                player.spawnParticle(Particle.PORTAL, from.add(0,1,0), 30, 0.3, 0.5, 0.3, 0.1);
                player.spawnParticle(Particle.PORTAL, target.clone().add(0,1,0), 30, 0.3, 0.5, 0.3, 0.1);
            });

            double auraRadius = skill.getDouble("aura-radius", 4);
            double auraHeal = skill.getDouble("aura-heal", 6.0);
            int auraDur = skill.getInt("aura-duration", 60);

            new BukkitRunnable() {
                int ticks = 0;
                @Override public void run() {
                    if (ticks >= auraDur) { cancel(); return; }
                    for (Entity e : target.getWorld().getNearbyEntities(target, auraRadius, auraRadius, auraRadius)) {
                        if (!(e instanceof Player ally) || ally.equals(player)) continue;
                        double newHp = Math.min(ally.getMaxHealth(), ally.getHealth() + (auraHeal / (auraDur / 20.0)));
                        ally.setHealth(newHp);
                    }
                    if (ticks == 0) {
                        double selfHp = Math.min(player.getMaxHealth(), player.getHealth() + auraHeal);
                        player.setHealth(selfHp);
                    }
                    target.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, target.clone().add(0,1,0), 5, auraRadius/2, 0.5, auraRadius/2, 0.01);
                    ticks += 20;
                }
            }.runTaskTimer(plugin, 0L, 20L);

            long cd = skill.getInt("cooldown", 10);
            cm.setCooldown(player.getUniqueId(), "celestial_step", cd);
            player.sendMessage(MessageUtil.get(plugin, "skill-celestial-step"));
            break;
        }
    }

    private void useDragonBreath(Player player, Race race, PlayerInteractEvent event) {
        for (Race.SkillConfig skill : race.getSkills()) {
            if (!skill.getType().equals("DRAGON_BREATH")) continue;
            var cm = plugin.getCooldownManager();
            if (cm.isOnCooldown(player.getUniqueId(), "dragon_breath")) {
                long rem = cm.getRemaining(player.getUniqueId(), "dragon_breath");
                player.sendMessage(MessageUtil.format(plugin, "skill-cooldown", "%time%", CooldownManager.formatTime(rem)));
                return;
            }
            if (breathActive.getOrDefault(player.getUniqueId(), false)) return;
            event.setCancelled(true);

            double range = skill.getDouble("range", 5);
            double dmgPerInterval = skill.getDouble("damage-per-interval", 4.0);
            int igniteTicks = skill.getInt("ignite-ticks", 100);
            int slowAmp = skill.getInt("slow-amplifier", 1);
            int slowDur = skill.getInt("slow-duration", 60);
            int duration = skill.getInt("duration", 60);
            int tickInterval = skill.getInt("tick-interval", 10);

            breathActive.put(player.getUniqueId(), true);
            player.sendMessage(MessageUtil.get(plugin, "skill-dragon-breath"));
            player.playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 0.5f);

            new BukkitRunnable() {
                int elapsed = 0;
                @Override public void run() {
                    if (elapsed >= duration || !player.isOnline()) {
                        breathActive.put(player.getUniqueId(), false);
                        cancel();
                        return;
                    }
                    Location eye = player.getEyeLocation();
                    Vector dir = player.getEyeLocation().getDirection().normalize();
                    for (double d = 1; d <= range; d += 0.5) {
                        Location loc = eye.clone().add(dir.clone().multiply(d));
                        loc.getWorld().spawnParticle(Particle.FLAME, loc, 3, 0.15, 0.15, 0.15, 0.01);
                    }
                    for (Entity e : player.getNearbyEntities(range + 1, range + 1, range + 1)) {
                        if (!(e instanceof LivingEntity target) || target.equals(player)) continue;
                        Location tLoc = target.getLocation();
                        Vector toTarget = tLoc.toVector().subtract(eye.toVector()).normalize();
                        if (toTarget.dot(dir) < 0.6) continue;
                        if (eye.distance(tLoc) > range + 1) continue;

                        target.damage(dmgPerInterval, player);
                        target.setFireTicks(igniteTicks);
                        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, slowDur, slowAmp, true, false, false));
                    }
                    elapsed += tickInterval;
                }
            }.runTaskTimer(plugin, 0L, tickInterval);

            long cd = skill.getInt("cooldown", 18);
            cm.setCooldown(player.getUniqueId(), "dragon_breath", cd);
            break;
        }
    }

    private void useGhostVeil(Player player, Race race, PlayerInteractEvent event) {
        for (Race.SkillConfig skill : race.getSkills()) {
            if (!skill.getType().equals("GHOST_VEIL")) continue;
            var cm = plugin.getCooldownManager();
            if (cm.isOnCooldown(player.getUniqueId(), "ghost_veil")) {
                long rem = cm.getRemaining(player.getUniqueId(), "ghost_veil");
                player.sendMessage(MessageUtil.format(plugin, "skill-cooldown", "%time%", CooldownManager.formatTime(rem)));
                return;
            }
            event.setCancelled(true);

            int duration = skill.getInt("duration", 120);
            int speedAmp = skill.getInt("speed-amplifier", 2);
            int backstabWindow = skill.getInt("backstab-window", 40);

            player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, duration, 0, true, false, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration, speedAmp, true, false, false));

            long expireMs = System.currentTimeMillis() + (duration * 50L);
            veilActiveUntil.put(player.getUniqueId(), expireMs);

            long cd = skill.getInt("cooldown", 22);
            cm.setCooldown(player.getUniqueId(), "ghost_veil", cd);
            player.sendMessage(MessageUtil.get(plugin, "skill-ghost-veil"));
            player.playSound(player.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 1f, 1.5f);
            player.spawnParticle(Particle.ASH, player.getLocation().add(0,1,0), 20, 0.3, 0.5, 0.3, 0.02);

            plugin.getServer().getRegionScheduler().runDelayed(plugin, player.getLocation(), t -> {
                if (veilActiveUntil.containsKey(player.getUniqueId())) {
                    veilActiveUntil.remove(player.getUniqueId());
                    long bsExpire = System.currentTimeMillis() + (backstabWindow * 50L);
                    backstabWindowUntil.put(player.getUniqueId(), bsExpire);
                    player.removePotionEffect(PotionEffectType.INVISIBILITY);
                }
            }, duration);
            break;
        }
    }

    private void cancelVeil(Player player) {
        veilActiveUntil.remove(player.getUniqueId());
        player.removePotionEffect(PotionEffectType.INVISIBILITY);
        Race ghostRace = plugin.getRaceManager().getRace("ghost");
        if (ghostRace != null) {
            for (Race.SkillConfig s : ghostRace.getSkills()) {
                if (!s.getType().equals("GHOST_VEIL")) continue;
                int bsWindow = s.getInt("backstab-window", 40);
                long bsExpire = System.currentTimeMillis() + (bsWindow * 50L);
                backstabWindowUntil.put(player.getUniqueId(), bsExpire);
                break;
            }
        }
    }

    // ─────────────────────────────────────────────────────────
    //  SNEAK + DROP (Q) — Passive Triggers
    // ─────────────────────────────────────────────────────────
    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (!player.isSneaking()) return;

        String raceId = getRaceId(player);
        if (raceId == null) return;
        Race race = plugin.getRaceManager().getRace(raceId);
        if (race == null) return;

        switch (raceId) {
            case "celestial" -> {
                event.setCancelled(true);
                useCelestialCurse(player, race);
            }
            case "dragon" -> {
                event.setCancelled(true);
                useDragonWingSlam(player, race);
            }
            case "ghost" -> {
                event.setCancelled(true);
                useGhostSiphon(player, race);
            }
        }
    }

    private void useCelestialCurse(Player player, Race race) {
        for (Race.SkillConfig skill : race.getSkills()) {
            if (!skill.getType().equals("CELESTIAL_CURSE")) continue;
            var cm = plugin.getCooldownManager();
            if (cm.isOnCooldown(player.getUniqueId(), "celestial_curse")) {
                long rem = cm.getRemaining(player.getUniqueId(), "celestial_curse");
                player.sendMessage(MessageUtil.format(plugin, "skill-cooldown", "%time%", CooldownManager.formatTime(rem)));
                return;
            }

            double range = skill.getDouble("range", 8);
            LivingEntity target = findNearestEnemy(player, range);
            if (target == null) {
                player.sendMessage(MessageUtil.get(plugin, "skill-no-target"));
                return;
            }

            for (Object effObj : skill.getEffectList()) {
                if (!(effObj instanceof Map<?, ?> eff)) continue;
                
                Object rawEffect = eff.get("effect");
                Object rawAmplifier = eff.get("amplifier");
                Object rawDuration = eff.get("duration");

                String name = rawEffect != null ? rawEffect.toString() : "";
                int amp = 0;
                int dur = 20;

                try {
                    if (rawAmplifier != null) amp = Integer.parseInt(rawAmplifier.toString());
                    if (rawDuration != null) dur = Integer.parseInt(rawDuration.toString());
                } catch (NumberFormatException ignored) {}

                PotionEffectType type = PotionEffectType.getByName(name);
                if (type != null) target.addPotionEffect(new PotionEffect(type, dur, amp, true, false, false));
            }

            long cd = skill.getInt("cooldown", 20);
            cm.setCooldown(player.getUniqueId(), "celestial_curse", cd);
            player.sendMessage(MessageUtil.get(plugin, "skill-celestial-curse"));
            player.playSound(player.getLocation(), Sound.ENTITY_WITCH_CELEBRATE, 1f, 0.8f);
            target.getWorld().spawnParticle(Particle.WITCH, target.getLocation().add(0,1,0), 25, 0.3, 0.5, 0.3, 0.02);
            break;
        }
    }

    private void useDragonWingSlam(Player player, Race race) {
        for (Race.SkillConfig skill : race.getSkills()) {
            if (!skill.getType().equals("DRAGON_WING_SLAM")) continue;
            var cm = plugin.getCooldownManager();
            if (cm.isOnCooldown(player.getUniqueId(), "dragon_wing_slam")) {
                long rem = cm.getRemaining(player.getUniqueId(), "dragon_wing_slam");
                player.sendMessage(MessageUtil.format(plugin, "skill-cooldown", "%time%", CooldownManager.formatTime(rem)));
                return;
            }

            double radius = skill.getDouble("radius", 4);
            double damage = skill.getDouble("damage", 6.0);
            double knockup = skill.getDouble("knockup-velocity", 1.5);
            int igniteTicks = skill.getInt("ignite-ticks", 60);

            for (Entity e : player.getNearbyEntities(radius, radius, radius)) {
                if (!(e instanceof LivingEntity target) || target.equals(player)) continue;
                target.damage(damage, player);
                target.setFireTicks(igniteTicks);
                Vector up = new Vector(
                        (random.nextDouble() - 0.5) * 0.4,
                        knockup,
                        (random.nextDouble() - 0.5) * 0.4);
                plugin.getServer().getRegionScheduler().run(plugin, target.getLocation(), t -> target.setVelocity(up));
            }

            long cd = skill.getInt("cooldown", 14);
            cm.setCooldown(player.getUniqueId(), "dragon_wing_slam", cd);
            player.sendMessage(MessageUtil.get(plugin, "skill-dragon-wingslam"));
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1f, 0.5f);
            player.getWorld().spawnParticle(Particle.EXPLOSION, player.getLocation().add(0,1,0), 5, radius/2, 0.5, radius/2, 0.1);
            player.getWorld().spawnParticle(Particle.FLAME, player.getLocation().add(0,1,0), 30, radius/2, 0.5, radius/2, 0.05);
            break;
        }
    }

    private void useGhostSiphon(Player player, Race race) {
        for (Race.SkillConfig skill : race.getSkills()) {
            if (!skill.getType().equals("GHOST_SIPHON")) continue;
            var cm = plugin.getCooldownManager();
            if (cm.isOnCooldown(player.getUniqueId(), "ghost_siphon")) {
                long rem = cm.getRemaining(player.getUniqueId(), "ghost_siphon");
                player.sendMessage(MessageUtil.format(plugin, "skill-cooldown", "%time%", CooldownManager.formatTime(rem)));
                return;
            }

            double radius = skill.getDouble("radius", 5.0);
            double damage = skill.getDouble("damage-per-hit", 3.0);
            double healFactor = skill.getDouble("heal-factor", 0.5);

            double totalHeal = 0;
            for (Entity e : player.getNearbyEntities(radius, radius, radius)) {
                if (!(e instanceof LivingEntity target) || target.equals(player)) continue;
                target.damage(damage, player);
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1));
                totalHeal += (damage * healFactor);
                target.getWorld().spawnParticle(Particle.SOUL, target.getLocation().add(0,1,0), 5, 0.2, 0.2, 0.2, 0.02);
            }

            if (totalHeal > 0) {
                double finalHeal = totalHeal;
                plugin.getServer().getRegionScheduler().run(plugin, player.getLocation(), t -> {
                    double nextHp = Math.min(player.getMaxHealth(), player.getHealth() + finalHeal);
                    player.setHealth(nextHp);
                });
                player.playSound(player.getLocation(), Sound.ENTITY_EVOKER_FANGS_ATTACK, 1f, 0.7f);
                player.spawnParticle(Particle.SOUL_FIRE_FLAME, player.getLocation().add(0,1,0), 20, radius/2, 0.3, radius/2, 0.05);
            }

            long cd = skill.getInt("cooldown", 16);
            cm.setCooldown(player.getUniqueId(), "ghost_siphon", cd);
            player.sendMessage(MessageUtil.get(plugin, "skill-ghost-siphon"));
            break;
        }
    }

    // ─────────────────────────────────────────────────────────
    //  SUPPORT UTILS & REFLECTION PLAYERDATA CHECK
    // ─────────────────────────────────────────────────────────
    private String getRaceId(Player player) {
        var pdm = plugin.getPlayerDataManager();
        if (pdm == null) return null;
        try {
            Method getMethod = null;
            for (Method m : pdm.getClass().getDeclaredMethods()) {
                if (m.getParameterCount() == 1 && m.getParameterTypes()[0].equals(UUID.class) 
                    && m.getReturnType().equals(PlayerData.class)) {
                    getMethod = m;
                    break;
                }
            }
            if (getMethod != null) {
                getMethod.setAccessible(true);
                PlayerData data = (PlayerData) getMethod.invoke(pdm, player.getUniqueId());
                return data != null ? data.getRaceId() : null;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private LivingEntity findNearestEnemy(Player player, double range) {
        LivingEntity nearest = null;
        double bestDist = range * range;
        for (Entity e : player.getNearbyEntities(range, range, range)) {
            if (!(e instanceof LivingEntity living) || living.equals(player)) continue;
            double d = player.getLocation().distanceSquared(living.getLocation());
            if (d < bestDist) {
                bestDist = d;
                nearest = living;
            }
        }
        return nearest;
    }

    private Location getTargetLocation(Player player, double maxRange) {
        Location target = player.getTargetBlockOnLine(null, (int) maxRange).getLocation();
        target.setYaw(player.getLocation().getYaw());
        target.setPitch(player.getLocation().getPitch());
        return target;
    }
}
