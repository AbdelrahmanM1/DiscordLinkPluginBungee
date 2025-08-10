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
        super("unlinkdiscord", null, "unlink"); // main command, no permission, alias
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof ProxiedPlayer)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return;
        }

        ProxiedPlayer player = (ProxiedPlayer) sender;
        AquixLinkCoreBungee plugin = AquixLinkCoreBungee.getInstance();
        LinkStorage linkStorage = plugin.getLinkStorage();

        if (linkStorage == null) {
            player.sendMessage(ChatColor.RED + "An internal error occurred (link storage is null).");
            plugin.getLogger().severe("LinkStorage instance is null in UnlinkCommand.");
            return;
        }

        if (!linkStorage.isPlayerLinked(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "You are not linked to any Discord account. "
                    + ChatColor.GREEN + "To link, use /linkdiscord <discord_id>.");
            return;
        }

        // Get the linked Discord ID before unlinking
        String discordId = linkStorage.getDiscordId(player.getUniqueId());

        // Unlink in storage
        linkStorage.unlinkPlayer(player.getUniqueId());
        player.sendMessage(ChatColor.GREEN + "✅ Successfully unlinked your Discord account.");

        // Notify on Discord if bot is available
        if (discordId != null) {
            DiscordBot bot = plugin.getDiscordBot();
            if (bot != null) {
                bot.getJda().retrieveUserById(discordId).queue(user -> {
                    user.openPrivateChannel().queue(channel ->
                            channel.sendMessage("🔓 Your Minecraft account has been unlinked from Discord.").queue()
                    );
                }, failure -> {
                    plugin.getLogger().warning("Failed to DM user " + discordId + " about unlinking: " + failure.getMessage());
                });
            }
        }
    }
}
