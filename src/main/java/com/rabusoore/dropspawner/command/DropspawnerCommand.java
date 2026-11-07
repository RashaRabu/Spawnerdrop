package com.rabusoore.dropspawner.command;

import com.rabusoore.dropspawner.DropspawnerPlugin;
import com.rabusoore.dropspawner.config.ConfigManager;
import com.rabusoore.dropspawner.config.MobDropSetting;
import com.rabusoore.dropspawner.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;

import java.util.*;
import java.util.stream.Collectors;

public class DropspawnerCommand implements CommandExecutor, TabCompleter {

    private final DropspawnerPlugin plugin;

    public DropspawnerCommand(DropspawnerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        ConfigManager config = plugin.getConfigManager();
        String prefix = plugin.getConfig().getString("messages.prefix", "");

        if (args.length == 0) {
            MessageUtil.sendMessage(sender, prefix + "<yellow>Penggunaan: /dropspawner <reload|chance|type|enable|disable|blacklist|info></yellow>");
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "reload" -> {
                if (!sender.hasPermission("dropspawner.reload")) {
                    MessageUtil.sendMessage(sender, prefix + plugin.getConfig().getString("messages.no-permission"));
                    return true;
                }
                config.loadConfigs();
                MessageUtil.sendMessage(sender, prefix + plugin.getConfig().getString("messages.reload-success"));
            }

            case "chance" -> {
                if (!sender.hasPermission("dropspawner.admin")) {
                    MessageUtil.sendMessage(sender, prefix + plugin.getConfig().getString("messages.no-permission"));
                    return true;
                }
                if (args.length < 3) {
                    MessageUtil.sendMessage(sender, prefix + "<yellow>Penggunaan: /dropspawner chance <MOB> <persen></yellow>");
                    return true;
                }
                EntityType type = parseEntityType(args[1]);
                if (type == null) {
                    MessageUtil.sendMessage(sender, prefix + plugin.getConfig().getString("messages.invalid-mob"), Map.of("mob", args[1]));
                    return true;
                }
                try {
                    double chance = Double.parseDouble(args[2]);
                    config.setMobSetting(type, null, chance, null);
                    MessageUtil.sendMessage(sender, prefix + plugin.getConfig().getString("messages.mob-updated"), Map.of("mob", type.name()));
                } catch (NumberFormatException e) {
                    MessageUtil.sendMessage(sender, prefix + "<red>Nilai persentase harus berupa angka.</red>");
                }
            }

            case "type" -> {
                if (!sender.hasPermission("dropspawner.admin")) {
                    MessageUtil.sendMessage(sender, prefix + plugin.getConfig().getString("messages.no-permission"));
                    return true;
                }
                if (args.length < 3) {
                    MessageUtil.sendMessage(sender, prefix + "<yellow>Penggunaan: /dropspawner type <MOB> <DROP_TYPE></yellow>");
                    return true;
                }
                EntityType mobType = parseEntityType(args[1]);
                EntityType dropType = parseEntityType(args[2]);

                if (mobType == null || dropType == null) {
                    MessageUtil.sendMessage(sender, prefix + "<red>Nama mob atau drop-type tidak valid.</red>");
                    return true;
                }
                config.setMobSetting(mobType, null, null, dropType);
                MessageUtil.sendMessage(sender, prefix + plugin.getConfig().getString("messages.mob-updated"), Map.of("mob", mobType.name()));
            }

            case "enable", "disable" -> {
                if (!sender.hasPermission("dropspawner.admin")) {
                    MessageUtil.sendMessage(sender, prefix + plugin.getConfig().getString("messages.no-permission"));
                    return true;
                }
                if (args.length < 2) {
                    MessageUtil.sendMessage(sender, prefix + "<yellow>Penggunaan: /dropspawner " + sub + " <MOB></yellow>");
                    return true;
                }
                EntityType type = parseEntityType(args[1]);
                if (type == null) {
                    MessageUtil.sendMessage(sender, prefix + plugin.getConfig().getString("messages.invalid-mob"), Map.of("mob", args[1]));
                    return true;
                }
                boolean enabled = sub.equals("enable");
                config.setMobSetting(type, enabled, null, null);
                MessageUtil.sendMessage(sender, prefix + plugin.getConfig().getString("messages.mob-updated"), Map.of("mob", type.name()));
            }

            case "blacklist" -> {
                if (!sender.hasPermission("dropspawner.admin")) {
                    MessageUtil.sendMessage(sender, prefix + plugin.getConfig().getString("messages.no-permission"));
                    return true;
                }
                if (args.length < 2) {
                    MessageUtil.sendMessage(sender, prefix + "<yellow>Penggunaan: /dropspawner blacklist <add|remove|list> [MOB]</yellow>");
                    return true;
                }
                String action = args[1].toLowerCase();

                if (action.equals("list")) {
                    String listStr = config.getMobBlacklist().stream()
                            .map(EntityType::name)
                            .collect(Collectors.joining(", "));
                    MessageUtil.sendMessage(sender, prefix + "<gold>Blacklist Mobs:</gold> <white>" + (listStr.isEmpty() ? "Kosong" : listStr) + "</white>");
                    return true;
                }

                if (args.length < 3) {
                    MessageUtil.sendMessage(sender, prefix + "<yellow>Penggunaan: /dropspawner blacklist " + action + " <MOB></yellow>");
                    return true;
                }

                EntityType type = parseEntityType(args[2]);
                if (type == null) {
                    MessageUtil.sendMessage(sender, prefix + plugin.getConfig().getString("messages.invalid-mob"), Map.of("mob", args[2]));
                    return true;
                }

                if (action.equals("add")) {
                    config.addBlacklistMob(type);
                    MessageUtil.sendMessage(sender, prefix + plugin.getConfig().getString("messages.blacklist-updated"));
                } else if (action.equals("remove")) {
                    config.removeBlacklistMob(type);
                    MessageUtil.sendMessage(sender, prefix + plugin.getConfig().getString("messages.blacklist-updated"));
                }
            }

            case "info" -> {
                if (!sender.hasPermission("dropspawner.admin")) {
                    MessageUtil.sendMessage(sender, prefix + plugin.getConfig().getString("messages.no-permission"));
                    return true;
                }
                if (args.length < 2) {
                    MessageUtil.sendMessage(sender, prefix + "<yellow>Penggunaan: /dropspawner info <MOB></yellow>");
                    return true;
                }
                EntityType type = parseEntityType(args[1]);
                if (type == null) {
                    MessageUtil.sendMessage(sender, prefix + plugin.getConfig().getString("messages.invalid-mob"), Map.of("mob", args[1]));
                    return true;
                }
                MobDropSetting setting = config.getMobSetting(type);
                boolean isBlacklisted = config.isMobBlacklisted(type);

                MessageUtil.sendMessage(sender, plugin.getConfig().getString("messages.info-header"), Map.of("mob", type.name()));
                MessageUtil.sendMessage(sender, plugin.getConfig().getString("messages.info-status"), Map.of("status", isBlacklisted ? "BLACK-LISTED" : (setting != null && setting.enabled() ? "AKTIF" : "NONAKTIF")));
                MessageUtil.sendMessage(sender, plugin.getConfig().getString("messages.info-chance"), Map.of("chance", String.valueOf(setting != null ? setting.chance() : 0.0)));
                MessageUtil.sendMessage(sender, plugin.getConfig().getString("messages.info-drop-type"), Map.of("type", setting != null ? setting.dropType().name() : type.name()));
            }

            default -> MessageUtil.sendMessage(sender, prefix + "<red>Sub-command tidak dikenali.</red>");
        }

        return true;
    }

    private EntityType parseEntityType(String input) {
        try {
            return EntityType.valueOf(input.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filterTabs(List.of("reload", "chance", "type", "enable", "disable", "blacklist", "info"), args[0]);
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("blacklist")) {
                return filterTabs(List.of("add", "remove", "list"), args[1]);
            }
            if (List.of("chance", "type", "enable", "disable", "info").contains(sub)) {
                return filterTabs(getAllMobNames(), args[1]);
            }
        }

        if (args.length == 3) {
            String sub = args[0].toLowerCase();
            if (sub.equals("type") || (sub.equals("blacklist") && (args[1].equalsIgnoreCase("add") || args[1].equalsIgnoreCase("remove")))) {
                return filterTabs(getAllMobNames(), args[2]);
            }
        }

        return Collections.emptyList();
    }

    private List<String> getAllMobNames() {
        return Arrays.stream(EntityType.values())
                .filter(type -> type.getEntityClass() != null && type.isSpawnable())
                .map(EntityType::name)
                .sorted()
                .collect(Collectors.toList());
    }

    private List<String> filterTabs(List<String> list, String input) {
        return list.stream()
                .filter(item -> item.toLowerCase().startsWith(input.toLowerCase()))
                .collect(Collectors.toList());
    }
                                        }
                                               
