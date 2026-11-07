package com.rabusoore.dropspawner.listener;

import com.rabusoore.dropspawner.DropspawnerPlugin;
import com.rabusoore.dropspawner.config.ConfigManager;
import com.rabusoore.dropspawner.config.MobDropSetting;
import com.rabusoore.dropspawner.util.MessageUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class EntityDeathListener implements Listener {

    private final DropspawnerPlugin plugin;

    public EntityDeathListener(DropspawnerPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();

        // 1. Validasi pembunuh harus pemain langsung
        if (killer == null) return;

        // 2. Bypass permission
        if (killer.hasPermission("dropspawner.bypass")) return;

        ConfigManager config = plugin.getConfigManager();

        // 3. Validasi World
        if (config.isWorldDisabled(entity.getWorld().getName())) return;

        // 4. Validasi Mob Blacklist
        if (config.isMobBlacklisted(entity.getType())) return;

        // 5. Ambil data konfigurasi mob
        MobDropSetting setting = config.getMobSetting(entity.getType());
        if (setting == null || !setting.enabled()) return;

        // 6. Hitung persentase drop (Termasuk statistik Looting)
        double finalChance = setting.chance();
        if (config.isLootingEnabled()) {
            ItemStack weapon = killer.getInventory().getItemInMainHand();
            int lootingLevel = weapon.getEnchantmentLevel(Enchantment.LOOTING);
            if (lootingLevel > 0) {
                finalChance += (lootingLevel * config.getLootingChancePerLevel());
            }
        }
        finalChance = Math.min(finalChance, config.getMaxChance());

        // 7. Pengocokan RNG
        double roll = ThreadLocalRandom.current().nextDouble(100.0);
        if (roll >= finalChance) return;

        // 8. Cek tipe drop spawner
        if (config.isDropTypeBlacklisted(setting.dropType())) return;

        // 9. Buat ItemStack Spawner Vanilla via Paper API
        ItemStack spawnerItem = new ItemStack(Material.SPAWNER, 1);
        BlockStateMeta meta = (BlockStateMeta) spawnerItem.getItemMeta();
        if (meta != null) {
            if (meta.getBlockState() instanceof CreatureSpawner spawnerState) {
                spawnerState.setSpawnedType(setting.dropType());
                meta.setBlockState(spawnerState);
                spawnerItem.setItemMeta(meta);
            }
        }

        // 10. Jatuhkan Item langsung ke tanah di lokasi mob
        Location dropLoc = entity.getLocation();
        Item droppedItem = dropLoc.getWorld().dropItem(dropLoc, spawnerItem);

        // Kustomisasi atribut item drop
        int pickupDelay = plugin.getConfig().getInt("item-settings.pickup-delay-ticks", 20);
        droppedItem.setPickupDelay(pickupDelay);

        int ownerSeconds = plugin.getConfig().getInt("item-settings.owner-only-seconds", 10);
        if (ownerSeconds > 0) {
            droppedItem.setOwner(killer.getUniqueId());
        }

        if (plugin.getConfig().getBoolean("item-settings.glow", true)) {
            droppedItem.setGlowing(true);
        }

        // 11. Efek Suara & Partikel
        if (plugin.getConfig().getBoolean("item-settings.sound.enabled", true)) {
            try {
                Sound sound = Sound.valueOf(plugin.getConfig().getString("item-settings.sound.type", "ENTITY_ITEM_PICKUP"));
                float volume = (float) plugin.getConfig().getDouble("item-settings.sound.volume", 1.0);
                float pitch = (float) plugin.getConfig().getDouble("item-settings.sound.pitch", 1.2);
                dropLoc.getWorld().playSound(dropLoc, sound, volume, pitch);
            } catch (IllegalArgumentException ignored) {}
        }

        if (plugin.getConfig().getBoolean("item-settings.particle.enabled", true)) {
            try {
                Particle particle = Particle.valueOf(plugin.getConfig().getString("item-settings.particle.type", "VILLAGER_HAPPY"));
                int count = plugin.getConfig().getInt("item-settings.particle.count", 15);
                dropLoc.getWorld().spawnParticle(particle, dropLoc.add(0, 0.5, 0), count, 0.3, 0.3, 0.3, 0.05);
            } catch (IllegalArgumentException ignored) {}
        }

        // 12. Kirim pesan ke pemain pembunuh
        String prefix = plugin.getConfig().getString("messages.prefix", "");
        String msg = plugin.getConfig().getString("messages.spawner-dropped", "");
        MessageUtil.sendMessage(killer, prefix + msg, Map.of("mob", setting.dropType().name()));
    }
                                                     }
