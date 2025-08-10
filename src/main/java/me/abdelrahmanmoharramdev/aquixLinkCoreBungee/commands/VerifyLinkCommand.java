package me.abdelrahmanmoharramdev.aquixLinkCoreBungee.commands;

import me.abdelrahmanmoharramdev.aquixLinkCoreBungee.AquixLinkCoreBungee;
import me.abdelrahmanmoharramdev.aquixLinkCoreBungee.Discord.DiscordBot;
import me.abdelrahmanmoharramdev.aquixLinkCoreBungee.storage.LinkStorage;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;

public class VerifyLinkCommand extends Command {

    public VerifyLinkCommand() {
        super("verifylink");
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
            player.sendMessage(ChatColor.RED + "Internal error: Link storage unavailable.");
            plugin.getLogger().severe("LinkStorage instance is null in VerifyLinkCommand.");
            return;
        }

        if (linkStorage.isPlayerLinked(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "You are already linked to a Discord account.");
            return;
        }

        if (args.length != 1) {
            player.sendMessage(ChatColor.RED + "Usage: /verifylink <code>");
            return;
        }

        String code = args[0];

        if (!linkStorage.hasPendingVerification(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "❌ You have no pending link request.");
            return;
        }

        if (linkStorage.isVerificationExpired(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "⏳ Your verification code has expired. Please use /linkdiscord again.");
            linkStorage.removePendingVerification(player.getUniqueId());
            return;
        }

        if (!linkStorage.isCodeValid(player.getUniqueId(), code)) {
            player.sendMessage(ChatColor.RED + "❌ Invalid verification code.");
            return;
        }

        // Get Discord ID before confirming
        String discordId = linkStorage.getDiscordId(player.getUniqueId());

        // Confirm linking
        linkStorage.confirmVerification(player.getUniqueId());
        linkStorage.removePendingVerification(player.getUniqueId());

        player.sendMessage(ChatColor.GREEN + "✅ Your Discord account has been successfully linked!");

        // DM the user via Discord if bot available
        if (discordId != null) {
            DiscordBot bot = plugin.getDiscordBot();
            if (bot != null) {
                bot.getJda().retrieveUserById(discordId).queue(user ->
                        user.openPrivateChannel().queue(channel ->
                                channel.sendMessage("✅ Your Minecraft account `" + player.getName() +
                                        "` has been successfully linked to this Discord account!").queue()
                        )
                );
            }
        }

        plugin.getLogger().info("Player " + player.getName() + " successfully linked to Discord ID " + discordId);
    }
}
