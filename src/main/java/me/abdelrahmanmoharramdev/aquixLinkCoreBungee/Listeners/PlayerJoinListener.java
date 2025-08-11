package me.abdelrahmanmoharramdev.aquixLinkCoreBungee.Listeners;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

import me.abdelrahmanmoharramdev.aquixLinkCoreBungee.AquixLinkCoreBungee;

public class PlayerJoinListener implements Listener {

    private static final String SEPARATOR = ChatColor.RED + "============================================";

    @EventHandler
    public void onPostLogin(PostLoginEvent event) {
        ProxiedPlayer player = event.getPlayer();
        AquixLinkCoreBungee plugin = AquixLinkCoreBungee.getInstance();

        boolean isLinked = plugin.getLinkStorage().isPlayerLinked(player.getUniqueId());

        if (!isLinked) {
            sendMessage(player,
                    SEPARATOR,
                    ChatColor.GOLD.toString() + ChatColor.BOLD + "Welcome to AquixMC, " + player.getName() + "!",
                    ChatColor.YELLOW + "It looks like your Minecraft account is " + ChatColor.RED + "NOT linked " + ChatColor.YELLOW + "with your Discord account.",
                    ChatColor.GRAY + "Linking your account allows you to verify your identity, Sync Your Roles.",
                    " ",
                    ChatColor.AQUA + "To link your account, simply type: " + ChatColor.GREEN + "/linkdiscord",
                    ChatColor.GRAY + "After typing the command, follow the instructions shown to complete the process.",
                    " ",
                    ChatColor.DARK_GREEN.toString() + ChatColor.BOLD + "Enjoy your stay at AquixMC!",
                    SEPARATOR
            );
        } else {
            sendMessage(player,
                    SEPARATOR,
                    ChatColor.DARK_GREEN.toString() + ChatColor.BOLD + "Welcome back to AquixMC, " + player.getName() + "!",
                    ChatColor.YELLOW + "Your Minecraft account is already linked with your Discord.",
                    ChatColor.GRAY + "You now have full access to our Discord-linked features.",
                    SEPARATOR
            );
        }
    }

    private void sendMessage(ProxiedPlayer player, String... messages) {
        for (String msg : messages) {
            player.sendMessage(new TextComponent(msg));
        }
    }
}
