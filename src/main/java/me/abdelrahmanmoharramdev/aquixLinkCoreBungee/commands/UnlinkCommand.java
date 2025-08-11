package me.abdelrahmanmoharramdev.aquixLinkCoreBungee.commands;

import me.abdelrahmanmoharramdev.aquixLinkCoreBungee.AquixLinkCoreBungee;
import me.abdelrahmanmoharramdev.aquixLinkCoreBungee.Discord.DiscordBot;
import me.abdelrahmanmoharramdev.aquixLinkCoreBungee.storage.LinkStorage;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;

public class UnlinkCommand extends Command {

    public UnlinkCommand() {
        super("unlinkdiscord", null, "unlink");
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof ProxiedPlayer player)) {
            sender.sendMessage(colorize("&cOnly players can use this command."));
            return;
        }

        AquixLinkCoreBungee plugin = AquixLinkCoreBungee.getInstance();
        LinkStorage linkStorage = plugin.getLinkStorage();

        if (linkStorage == null) {
            player.sendMessage(colorize("&cAn internal error occurred (link storage is null)."));
            plugin.getLogger().severe("LinkStorage instance is null in UnlinkCommand.");
            return;
        }

        if (!linkStorage.isPlayerLinked(player.getUniqueId())) {
            player.sendMessage(colorize("&cYou are not linked to any Discord account. &aTo link, use /linkdiscord <discord_id>."));
            return;
        }

        String discordId = linkStorage.getDiscordId(player.getUniqueId());

        // Unlink player first to keep data consistent
        linkStorage.unlinkPlayer(player.getUniqueId());

        player.sendMessage(colorize("&a✅ Successfully unlinked your Discord account."));

        if (discordId != null) {
            DiscordBot bot = plugin.getDiscordBot();
            if (bot != null) {
                bot.getJda().retrieveUserById(discordId).queue(
                        user -> user.openPrivateChannel().queue(
                                channel -> channel.sendMessage("🔓 Your Minecraft account has been unlinked from Discord.").queue(),
                                failure -> plugin.getLogger().warning("Failed to open private channel to notify user " + discordId + ": " + failure.getMessage())
                        ),
                        failure -> plugin.getLogger().warning("Failed to retrieve Discord user " + discordId + " for unlink notification: " + failure.getMessage())
                );
            }
        }
    }

    // Utility method for colorizing messages with '&' codes
    private static String colorize(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }
}
