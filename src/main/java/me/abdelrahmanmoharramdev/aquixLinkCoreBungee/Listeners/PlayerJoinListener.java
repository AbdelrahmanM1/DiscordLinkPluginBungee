package me.abdelrahmanmoharramdev.aquixLinkCoreBungee.Listeners;

import me.abdelrahmanmoharramdev.aquixLinkCoreBungee.AquixLinkCoreBungee;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

public class PlayerJoinListener implements Listener {

    private static final String SEPARATOR_RED = ChatColor.RED + "============================================";
    private static final String SEPARATOR_GREEN = ChatColor.GREEN + "============================================";

    @EventHandler
    public void onPlayerJoin(PostLoginEvent event) {
        ProxiedPlayer player = event.getPlayer();
        boolean isLinked = AquixLinkCoreBungee.getInstance()
                .getLinkStorage()
                .isPlayerLinked(player.getUniqueId());

        if (!isLinked) {
            send(player,
                    SEPARATOR_RED,
                    ChatColor.GOLD + "" + ChatColor.BOLD + "Welcome to AquixMC, " + player.getName() + "!",
                    ChatColor.YELLOW + "It looks like your Minecraft account is " + ChatColor.RED + "NOT linked "
                            + ChatColor.YELLOW + "with your Discord account.",
                    ChatColor.GRAY + "Linking your account allows you to verify your identity, get special ranks, and access exclusive Discord channels.",
                    "",
                    ChatColor.AQUA + "To link your account, simply type: " + ChatColor.GREEN + "/linkdiscord",
                    ChatColor.GRAY + "After typing the command, follow the instructions shown to complete the process.",
                    "",
                    ChatColor.DARK_GREEN + "Enjoy your stay at " + ChatColor.BOLD + "AquixMC" + ChatColor.DARK_GREEN + "!",
                    SEPARATOR_RED
            );
        } else {
            send(player,
                    SEPARATOR_GREEN,
                    ChatColor.DARK_GREEN + "" + ChatColor.BOLD + "Welcome back to AquixMC, " + player.getName() + "!",
                    ChatColor.YELLOW + "Your Minecraft account is already linked with your Discord.",
                    ChatColor.GRAY + "You now have full access to our Discord-linked features.",
                    SEPARATOR_GREEN
            );
        }
    }

    /**
     * Sends multiple lines to a player.
     */
    private void send(ProxiedPlayer player, String... messages) {
        for (String msg : messages) {
            player.sendMessage(new TextComponent(msg));
        }
    }
}
