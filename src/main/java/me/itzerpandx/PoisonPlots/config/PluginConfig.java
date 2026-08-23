package me.itzerpandx.PoisonPlots.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

public class PluginConfig {
    private final Plugin plugin;
    private FileConfiguration config;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public PluginConfig(Plugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.config = plugin.getConfig();
    }

    public void reload() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();
    }

    public long getAutoSaveInterval() {
        return config.getLong("data-autosave", 300) * 20; // Convert to ticks
    }

    public long getMaxRent() {
        return config.getLong("plot-max-rent", 3);
    }

    public long getStartRent() {
        return config.getLong("plot-start-rent", 3);
    }

    public int getDefaultMaxMembers() {
        return config.getInt("plot-default-max-members", 5);
    }

    public int getDefaultPlotRent() {
        return config.getInt("plot-default-rent", 10);
    }

    public boolean isPlotOwnershipLimitEnabled() {
        return config.getBoolean("plot-ownership-limit-enabled", true);
    }

    public Component getMessageComponent(String key) {
        String message = config.getString("messages." + key, "");
        message = message.replace("{prefix}", config.getString("messages.prefix", ""));
        return miniMessage.deserialize(message);
    }

    public Component getMessageComponent(String key, Object... replacements) {
        String message = config.getString("messages." + key, "");
        message = message.replace("{prefix}", config.getString("messages.prefix", ""));
        for (int i = 0; i < replacements.length; i += 2) {
            if (i + 1 < replacements.length) {
                message = message.replace("{" + replacements[i] + "}", String.valueOf(replacements[i + 1]));
            }
        }
        return miniMessage.deserialize(message);
    }

    public String getMessageString(String key, Object... replacements) {
        String message = config.getString("messages." + key, "");
        message = message.replace("{prefix}", config.getString("messages.prefix", ""));
        for (int i = 0; i < replacements.length; i += 2) {
            if (i + 1 < replacements.length) {
                message = message.replace("{" + replacements[i] + "}", String.valueOf(replacements[i + 1]));
            }
        }
        return message;
    }

    public String getSignLinePlain(String type, String line, Object... replacements) {
        String message = config.getString("sign." + type + "." + line, "");
        for (int i = 0; i < replacements.length; i += 2) {
            if (i + 1 < replacements.length) {
                message = message.replace("{" + replacements[i] + "}", String.valueOf(replacements[i + 1]));
            }
        }
        return message;
    }

    public java.util.List<String> getHelpMessage(String label) {
        java.util.List<String> helpLines = config.getStringList("help-message");
        java.util.List<String> processed = new java.util.ArrayList<>();
        for (String line : helpLines) {
            line = line.replace("{label}", label);
            line = line.replace("{prefix}", config.getString("messages.prefix", ""));
            processed.add(line);
        }
        return processed;
    }
}
