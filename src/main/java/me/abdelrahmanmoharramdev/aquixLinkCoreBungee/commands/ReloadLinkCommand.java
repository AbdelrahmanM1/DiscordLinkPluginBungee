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
            // Reload plugin config.yml
            plugin.reloadConfig();

            sender.sendMessage(new TextComponent("§aConfig reloaded from file."));

            // Restart Discord bot if running
            if (plugin.getDiscordBot() != null && plugin.getDiscordBot().getJda() != null) {
                plugin.getDiscordBot().getJda().shutdownNow();
                plugin.getLogger().info("Discord bot stopped.");
            }

            // Get new token and verificationChannelId from config
            String token = plugin.getConfig().getString("discord-token", "");
            long verificationChannelId = plugin.getConfig().getLong("discord-verification-channel-id", 0L);

            if (!token.isEmpty() && verificationChannelId != 0L) {
                plugin.getProxy().getScheduler().runAsync(plugin, () -> {
                    try {
                        plugin.setDiscordBot(new DiscordBot(plugin, token, verificationChannelId));
                        plugin.getLogger().info("✅ Discord bot started successfully after reload.");
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.SEVERE, "Failed to start Discord bot after reload: " + e.getMessage(), e);
                    }
                });
            } else {
                sender.sendMessage(new TextComponent("§cDiscord token or verification channel ID missing or invalid in config.yml."));
            }

            sender.sendMessage(new TextComponent("§aAquixLinkCoreBungee reloaded successfully."));
        } catch (Exception e) {
            sender.sendMessage(new TextComponent("§cFailed to reload plugin: " + e.getMessage()));
            plugin.getLogger().log(Level.SEVERE, "Error reloading plugin", e);
        }
    }
}
