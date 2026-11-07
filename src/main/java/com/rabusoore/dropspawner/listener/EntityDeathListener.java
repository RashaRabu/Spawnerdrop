package com.rabusoore.dropspawner.listener;

import com.rabusoore.dropspawner.DropspawnerPlugin;
import com.rabusoore.dropspawner.config.ConfigManager;
import com.rabusoore.dropspawner.config.MobDropSetting;
import com.rabusoore.dropspawner.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class EntityDeathListener implements Listener {

    private final DropspawnerPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

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

        // 9. Format Nama Mob (contoh: ZOMBIE -> Zombie)
        String mobNameFormatted = formatMobName(setting.dropType().name());

        // 10. Buat ItemStack Spawner Vanilla yang Kompatibel dengan SmartSpawner
        ItemStack spawnerItem = new ItemStack(Material.SPAWNER, 1);
        BlockStateMeta meta = (BlockStateMeta) spawnerItem.getItemMeta();
        if (meta != null) {
            // Set Tipe Mob di CreatureSpawner (Kompatibilitas Utama SmartSpawner)
            if (meta.getBlockState() instanceof CreatureSpawner spawnerState) {
                spawnerState.setSpawnedType(setting.dropType());
                meta.setBlockState(spawnerState);
            }

            // Set Custom Display Name jika diaktifkan
            if (plugin.getConfig().getBoolean("item-settings.custom-name.enabled", true)) {
                String nameFormat = plugin.getConfig().getString("item-settings.custom-name.format", "<yellow>{mob} Spawner</yellow>");
                nameFormat = nameFormat.replace("{mob}", mobNameFormatted);
                meta.displayName(miniMessage.deserialize(nameFormat));
            }

            // Set Custom Lore jika diaktifkan
            if (plugin.getConfig().getBoolean("item-settings.custom-lore.enabled", true)) {
                List<String> rawLore = plugin.getConfig().getStringList("item-settings.custom-lore.lines");
                List<Component> loreComponents = new ArrayList<>();
                for (String line : rawLore) {
                    loreComponents.add(miniMessage.deserialize(line.replace("{mob}", mobNameFormatted)));
                }
                meta.lore(loreComponents);
            }

            spawnerItem.setItemMeta(meta);
        }

        // 11. Jatuhkan Item di lokasi mob
        Location dropLoc = entity.getLocation();
        Item droppedItem = dropLoc.getWorld().dropItem(dropLoc, spawnerItem);

        int pickupDelay = plugin.getConfig().getInt("item-settings.pickup-delay-ticks", 20);
        droppedItem.setPickupDelay(pickupDelay);

        int ownerSeconds = plugin.getConfig().getInt("item-settings.owner-only-seconds", 10);
        if (ownerSeconds > 0) {
            droppedItem.setOwner(killer.getUniqueId());
        }

        if (plugin.getConfig().getBoolean("item-settings.glow", true)) {
            droppedItem.setGlowing(true);
        }

        // 12. Efek Suara & Partikel
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

        // 13. Kirim pesan ke pembunuh
        String prefix = plugin.getConfig().getString("messages.prefix", "");
        String msg = plugin.getConfig().getString("messages.spawner-dropped", "");
        MessageUtil.sendMessage(killer, prefix + msg, Map.of("mob", mobNameFormatted));
    }

    private String formatMobName(String rawName) {
        String[] words = rawName.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(" ");
            }
        }
        return sb.toString().trim();
    }
}
