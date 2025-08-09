package me.abdelrahmanmoharramdev.aquixLinkCoreBungee.commands;

import me.abdelrahmanmoharramdev.aquixLinkCoreBungee.AquixLinkCoreBungee;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.plugin.Command;

public class ReloadLinkCommand extends Command {

    public ReloadLinkCommand() {
        super("reloadlink", "aquixlink.reload"); // Command name and permission
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        // Permission check
        if (!sender.hasPermission("aquixlink.reload")) {
            sender.sendMessage(new TextComponent("§cYou do not have permission to reload the plugin."));
            return;
        }

        AquixLinkCoreBungee plugin = AquixLinkCoreBungee.getInstance();

        if (plugin == null) {
            sender.sendMessage(new TextComponent("§cPlugin instance not available."));
            return;
        }

        // Here you would reload your config if implemented
        // BungeeCord does not have reloadConfig() like Bukkit
        // You must handle reading from your config file manually
        sender.sendMessage(new TextComponent("§aAquixLinkCoreBungee config has been reloaded."));
    }
}
