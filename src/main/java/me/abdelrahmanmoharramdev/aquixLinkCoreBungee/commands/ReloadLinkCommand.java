package me.abdelrahmanmoharramdev.aquixLinkCoreBungee.commands;

import me.abdelrahmanmoharramdev.aquixLinkCoreBungee.AquixLinkCoreBungee;
import me.abdelrahmanmoharramdev.aquixLinkCoreBungee.Discord.DiscordBot;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.plugin.Command;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.logging.Level;

public class ReloadLinkCommand extends Command {

    public ReloadLinkCommand() {
        super("reloadlink", "aquixlink.reload"); // Command name and permission
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("aquixlink.reload")) {
            sender.sendMessage(new TextComponent("§cYou do not have permission to reload the plugin."));
            return;
        }

        AquixLinkCoreBungee plugin = AquixLinkCoreBungee.getInstance();

        if (plugin == null) {
            sender.sendMessage(new TextComponent("§cPlugin instance not available."));
            return;
        }

        try {
            // Reload config.yml manually
            File configFile = new File(plugin.getDataFolder(), "config.yml");
            if (!configFile.exists()) {
                Files.copy(plugin.getResourceAsStream("config.yml"), configFile.toPath());
                sender.sendMessage(new TextComponent("§eDefault config.yml created."));
            }

            plugin.getProxy().getConfigurationAdapter().load(); // Reloads proxy config
            sender.sendMessage(new TextComponent("§aConfig reloaded from file."));

            // Restart Discord bot if needed
            if (plugin.getDiscordBot() != null) {
                plugin.getDiscordBot().getJda().shutdownNow();
                plugin.getLogger().info("Discord bot stopped.");
            }

            String token = plugin.getProxy().getConfigurationAdapter().getString("discord-bot-token", "");
            if (token != null && !token.isEmpty()) {
                plugin.getProxy().getScheduler().runAsync(plugin, () -> {
                    try {
                        plugin.getLogger().info("Starting Discord bot after reload...");
                        plugin.setDiscordBot(new DiscordBot(plugin, token));
                        plugin.getLogger().info("✅ Discord bot started successfully after reload.");
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.SEVERE, "Failed to start Discord bot after reload: " + e.getMessage(), e);
                    }
                });
            }

            sender.sendMessage(new TextComponent("§aAquixLinkCoreBungee reloaded successfully."));
        } catch (IOException e) {
            sender.sendMessage(new TextComponent("§cFailed to reload config: " + e.getMessage()));
            plugin.getLogger().log(Level.SEVERE, "Error reloading config", e);
        }
    }
}
