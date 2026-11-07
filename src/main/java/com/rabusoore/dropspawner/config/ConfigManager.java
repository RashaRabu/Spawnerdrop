package com.rabusoore.dropspawner.config;

import com.rabusoore.dropspawner.DropspawnerPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class ConfigManager {

    private final DropspawnerPlugin plugin;
    private File dropConfigFile;
    private FileConfiguration dropConfig;

    private final Map<EntityType, MobDropSetting> mobSettingsCache = new ConcurrentHashMap<>();
    private final Set<EntityType> mobBlacklist = new HashSet<>();
    private final Set<EntityType> dropTypeBlacklist = new HashSet<>();
    private final Set<String> worldBlacklist = new HashSet<>();

    public ConfigManager(DropspawnerPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadConfigs() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        loadDropConfig();

        cacheSettings();
    }

    private void loadDropConfig() {
        dropConfigFile = new File(plugin.getDataFolder(), "drop.yml");
        if (!dropConfigFile.exists()) {
            try {
                dropConfigFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Gagal membuat file drop.yml!", e);
            }
        }
        dropConfig = YamlConfiguration.loadConfiguration(dropConfigFile);
        generateMissingMobs();
    }

    /**
     * Memeriksa semua EntityType hidup di Paper registry dan menambahkan mob baru ke drop.yml tanpa menimpa data lama.
     */
    private void generateMissingMobs() {
        boolean modified = false;

        if (!dropConfig.contains("mobs")) {
            dropConfig.createSection("mobs");
            modified = true;
        }

        List<String> defaultBosses = List.of(
            "ENDER_DRAGON", "WITHER", "WARDEN", "ELDER_GUARDIAN", "GIANT", "ILLUSIONER"
        );

        for (EntityType type : EntityType.values()) {
            if (type == EntityType.UNKNOWN || type.getEntityClass() == null) continue;
            
            // Validasi mob yang tergolong LivingEntity dan bisa spawned di world
            if (LivingEntity.class.isAssignableFrom(type.getEntityClass()) && type.isSpawnable()) {
                String path = "mobs." + type.name();
                if (!dropConfig.contains(path)) {
                    boolean isBoss = defaultBosses.contains(type.name());
                    dropConfig.set(path + ".enabled", !isBoss);
                    dropConfig.set(path + ".chance", isBoss ? 0.0 : 3.0);
                    dropConfig.set(path + ".drop-type", type.name());
                    modified = true;
                }
            }
        }

        if (modified) {
            saveDropConfig();
        }
    }

    public void saveDropConfig() {
        try {
            dropConfig.save(dropConfigFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Gagal menyimpan file drop.yml!", e);
        }
    }

    /**
     * Memuat semua setting dari disk ke Memory Cache untuk performa tinggi tanpa I/O saat kill mob.
     */
    public void cacheSettings() {
        mobSettingsCache.clear();
        mobBlacklist.clear();
        dropTypeBlacklist.clear();
        worldBlacklist.clear();

        FileConfiguration config = plugin.getConfig();

        for (String mobName : config.getStringList("blacklist.mobs")) {
            try {
                mobBlacklist.add(EntityType.valueOf(mobName.toUpperCase()));
            } catch (IllegalArgumentException ignored) {}
        }

        for (String typeName : config.getStringList("blacklist.drop-types")) {
            try {
                dropTypeBlacklist.add(EntityType.valueOf(typeName.toUpperCase()));
            } catch (IllegalArgumentException ignored) {}
        }

        worldBlacklist.addAll(config.getStringList("blacklist.worlds"));

        if (dropConfig.isConfigurationSection("mobs")) {
            for (String key : dropConfig.getConfigurationSection("mobs").getKeys(false)) {
                try {
                    EntityType mobType = EntityType.valueOf(key.toUpperCase());
                    boolean enabled = dropConfig.getBoolean("mobs." + key + ".enabled", true);
                    double chance = dropConfig.getDouble("mobs." + key + ".chance", 0.0);
                    String dropTypeName = dropConfig.getString("mobs." + key + ".drop-type", key);

                    EntityType dropType;
                    try {
                        dropType = EntityType.valueOf(dropTypeName.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Format drop-type tidak valid: '" + dropTypeName + "' untuk mob " + key);
                        continue;
                    }

                    mobSettingsCache.put(mobType, new MobDropSetting(enabled, chance, dropType));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Nama mob tidak valid ditemukan di drop.yml: " + key);
                }
            }
        }
    }

    public MobDropSetting getMobSetting(EntityType type) {
        return mobSettingsCache.get(type);
    }

    public void setMobSetting(EntityType mobType, Boolean enabled, Double chance, EntityType dropType) {
        String path = "mobs." + mobType.name();
        MobDropSetting current = mobSettingsCache.get(mobType);

        boolean newEnabled = (enabled != null) ? enabled : (current != null && current.enabled());
        double newChance = (chance != null) ? chance : (current != null ? current.chance() : 3.0);
        EntityType newDropType = (dropType != null) ? dropType : (current != null ? current.dropType() : mobType);

        dropConfig.set(path + ".enabled", newEnabled);
        dropConfig.set(path + ".chance", newChance);
        dropConfig.set(path + ".drop-type", newDropType.name());

        mobSettingsCache.put(mobType, new MobDropSetting(newEnabled, newChance, newDropType));
        saveDropConfig();
    }

    public void addBlacklistMob(EntityType type) {
        List<String> list = plugin.getConfig().getStringList("blacklist.mobs");
        if (!list.contains(type.name())) {
            list.add(type.name());
            plugin.getConfig().set("blacklist.mobs", list);
            plugin.saveConfig();
            mobBlacklist.add(type);
        }
    }

    public void removeBlacklistMob(EntityType type) {
        List<String> list = plugin.getConfig().getStringList("blacklist.mobs");
        if (list.contains(type.name())) {
            list.remove(type.name());
            plugin.getConfig().set("blacklist.mobs", list);
            plugin.saveConfig();
            mobBlacklist.remove(type);
        }
    }

    public boolean isMobBlacklisted(EntityType type) {
        return mobBlacklist.contains(type);
    }

    public boolean isDropTypeBlacklisted(EntityType type) {
        return dropTypeBlacklist.contains(type);
    }

    public boolean isWorldDisabled(String worldName) {
        boolean isBlacklistMode = plugin.getConfig().getBoolean("blacklist.world-blacklist-mode", true);
        if (isBlacklistMode) {
            return worldBlacklist.contains(worldName);
        } else {
            return !worldBlacklist.contains(worldName);
        }
    }

    public Set<EntityType> getMobBlacklist() {
        return Collections.unmodifiableSet(mobBlacklist);
    }

    public boolean isLootingEnabled() {
        return plugin.getConfig().getBoolean("looting.enabled", true);
    }

    public double getLootingChancePerLevel() {
        return plugin.getConfig().getDouble("looting.chance-per-level", 0.5);
    }

    public double getMaxChance() {
        return plugin.getConfig().getDouble("looting.max-chance", 10.0);
    }
                       }
