package com.rabusoore.dropspawner.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;

import java.util.Map;

public class MessageUtil {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    /**
     * Mengirim pesan terformat dengan MiniMessage atau Legacy Color Code ke CommandSender.
     */
    public static void sendMessage(CommandSender sender, String message, Map<String, String> placeholders) {
        if (message == null || message.isEmpty()) return;

        String formatted = message;
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                formatted = formatted.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }

        Component component;
        if (formatted.contains("<") && formatted.contains(">")) {
            component = MINI_MESSAGE.deserialize(formatted);
        } else {
            component = LegacyComponentSerializer.legacyAmpersand().deserialize(formatted);
        }

        sender.sendMessage(component);
    }

    public static void sendMessage(CommandSender sender, String message) {
        sendMessage(sender, message, null);
    }
}
