package com.rabusoore.dropspawner;

import com.rabusoore.dropspawner.command.DropspawnerCommand;
import com.rabusoore.dropspawner.config.ConfigManager;
import com.rabusoore.dropspawner.listener.EntityDeathListener;
import org.bukkit.plugin.java.JavaPlugin;

public final class DropspawnerPlugin extends JavaPlugin {

    private ConfigManager configManager;

    @Override
    public void onEnable() {
        // Inisialisasi Config Manager dan muat file konfigurasi
        this.configManager = new ConfigManager(this);
        this.configManager.loadConfigs();

        // Registrasi Listener Event
        getServer().getPluginManager().registerEvents(new EntityDeathListener(this), this);

        // Registrasi Executor & TabCompleter Command
        DropspawnerCommand commandExecutor = new DropspawnerCommand(this);
        if (getCommand("dropspawner") != null) {
            getCommand("dropspawner").setExecutor(commandExecutor);
            getCommand("dropspawner").setTabCompleter(commandExecutor);
        }

        getLogger().info("Dropspawner v" + getPluginMeta().getVersion() + " oleh Rabusoore berhasil diaktifkan!");
    }

    @Override
    public void onDisable() {
        getLogger().info("Dropspawner berhasil dinonaktifkan.");
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }
}
