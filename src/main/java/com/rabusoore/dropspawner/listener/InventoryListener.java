package com.rabusoore.dropspawner.listener;

import com.rabusoore.dropspawner.DropspawnerPlugin;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.persistence.PersistentDataType;

public class InventoryListener implements Listener {

    private final DropspawnerPlugin plugin;
    private final NamespacedKey spawnerKey;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public InventoryListener(DropspawnerPlugin plugin) {
        this.plugin = plugin;
        this.spawnerKey = new NamespacedKey(plugin, "spawner_mob");
    }

    // Fix saat player join server (memperbaiki spawner di inventory yang tereset saat relog)
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerJoin(PlayerJoinEvent event) {
        repairPlayerInventory(event.getPlayer());
    }

    // Fix saat player mengambil item spawner dari tanah
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            repairSpawnerItem(event.getItem().getItemStack());
        }
    }

    // Fix saat player mengeklik item di dalam inventory
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            repairSpawnerItem(event.getCurrentItem());
            repairSpawnerItem(event.getCursor());
        }
    }

    private void repairPlayerInventory(Player player) {
        PlayerInventory inv = player.getInventory();
        for (ItemStack item : inv.getContents()) {
            repairSpawnerItem(item);
        }
    }

    /**
     * Memeriksa dan memulihkan BlockStateMeta spawner jika tipe mob-nya hilang setelah relog.
     */
    private void repairSpawnerItem(ItemStack item) {
        if (item == null || item.getType() != Material.SPAWNER) return;
        if (!item.hasItemMeta()) return;

        BlockStateMeta meta = (BlockStateMeta) item.getItemMeta();
        if (meta == null) return;

        // Cek apakah item punya data PDC spawner_mob
        if (!meta.getPersistentDataContainer().has(spawnerKey, PersistentDataType.STRING)) return;

        String mobName = meta.getPersistentDataContainer().get(spawnerKey, PersistentDataType.STRING);
        if (mobName == null) return;

        try {
            EntityType type = EntityType.valueOf(mobName);

            if (meta.getBlockState() instanceof CreatureSpawner spawnerState) {
                // Jika tipe mob di BlockState tereset atau beda dari PDC, pulihkan!
                if (spawnerState.getSpawnedType() != type) {
                    spawnerState.setSpawnedType(type);
                    meta.setBlockState(spawnerState);
                    item.setItemMeta(meta);
                }
            }
        } catch (IllegalArgumentException ignored) {}
    }
}
