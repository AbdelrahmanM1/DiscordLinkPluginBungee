package me.abdelrahmanmoharramdev.aquixLinkCoreBungee.Listeners;

import me.abdelrahmanmoharramdev.aquixLinkCoreBungee.AquixLinkCoreBungee;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.api.chat.TextComponent;

public class PlayerJoinListener implements Listener {

    @EventHandler
    public void onPlayerJoin(PostLoginEvent event) {
        ProxiedPlayer player = event.getPlayer();

        boolean isLinked = AquixLinkCoreBungee.getInstance()
                .getLinkStorage()
                .isPlayerLinked(player.getUniqueId());

        if (!isLinked) {
            player.sendMessage(new TextComponent(ChatColor.RED + "============================================"));
            player.sendMessage(new TextComponent(ChatColor.GOLD + "" + ChatColor.BOLD + "Welcome to AquixMC, " + player.getName() + "!"));
            player.sendMessage(new TextComponent(ChatColor.YELLOW + "It looks like your Minecraft account is " + ChatColor.RED + "NOT linked " + ChatColor.YELLOW + "with your Discord account."));
            player.sendMessage(new TextComponent(ChatColor.GRAY + "Linking your account allows you to verify your identity, get special ranks, and access exclusive Discord channels."));
            player.sendMessage(new TextComponent(""));
            player.sendMessage(new TextComponent(ChatColor.AQUA + "To link your account, simply type: " + ChatColor.GREEN + "/linkdiscord"));
            player.sendMessage(new TextComponent(ChatColor.GRAY + "After typing the command, follow the instructions shown to complete the process."));
            player.sendMessage(new TextComponent(""));
            player.sendMessage(new TextComponent(ChatColor.DARK_GREEN + "Enjoy your stay at " + ChatColor.BOLD + "AquixMC" + ChatColor.DARK_GREEN + "!"));
            player.sendMessage(new TextComponent(ChatColor.RED + "============================================"));
        } else {
            player.sendMessage(new TextComponent(ChatColor.GREEN + "============================================"));
            player.sendMessage(new TextComponent(ChatColor.DARK_GREEN + "" + ChatColor.BOLD + "Welcome back to AquixMC, " + player.getName() + "!"));
            player.sendMessage(new TextComponent(ChatColor.YELLOW + "Your Minecraft account is already linked with your Discord."));
            player.sendMessage(new TextComponent(ChatColor.GRAY + "You now have full access to our Discord-linked features."));
            player.sendMessage(new TextComponent(ChatColor.GREEN + "============================================"));
        }
    }
}
