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
        if (!(sender instanceof ProxiedPlayer player)) {
            sender.sendMessage(colorize("&cOnly players can use this command."));
            return;
        }

        AquixLinkCoreBungee plugin = AquixLinkCoreBungee.getInstance();
        LinkStorage linkStorage = plugin.getLinkStorage();

        if (linkStorage == null) {
            player.sendMessage(colorize("&cInternal error: Link storage unavailable."));
            plugin.getLogger().severe("LinkStorage instance is null in VerifyLinkCommand.");
            return;
        }

        if (linkStorage.isPlayerLinked(player.getUniqueId())) {
            player.sendMessage(colorize("&cYou are already linked to a Discord account."));
            return;
        }

        if (args.length != 1) {
            player.sendMessage(colorize("&cUsage: /verifylink <code>"));
            return;
        }

        String code = args[0];

        if (!linkStorage.hasPendingVerification(player.getUniqueId())) {
            player.sendMessage(colorize("&c❌ You have no pending link request."));
            return;
        }

        if (linkStorage.isVerificationExpired(player.getUniqueId())) {
            player.sendMessage(colorize("&e⏳ Your verification code has expired. Please use /linkdiscord again."));
            linkStorage.removePendingVerification(player.getUniqueId());
            return;
        }

        if (!linkStorage.isCodeValid(player.getUniqueId(), code)) {
            player.sendMessage(colorize("&c❌ Invalid verification code."));
            return;
        }

        String discordId = linkStorage.getDiscordId(player.getUniqueId());

        // Confirm and clear pending verification
        linkStorage.confirmVerification(player.getUniqueId());
        linkStorage.removePendingVerification(player.getUniqueId());

        player.sendMessage(colorize("&a✅ Your Discord account has been successfully linked!"));

        if (discordId != null) {
            DiscordBot bot = plugin.getDiscordBot();
            if (bot != null && bot.getJda() != null) {
                bot.getJda().retrieveUserById(discordId).queue(user ->
                                user.openPrivateChannel().queue(channel ->
                                                channel.sendMessage("✅ Your Minecraft account `" + player.getName() + "` has been successfully linked to this Discord account!").queue(),
                                        failure -> plugin.getLogger().warning("Failed to open DM channel for user " + discordId + ": " + failure.getMessage())
                                ),
                        failure -> plugin.getLogger().warning("Failed to retrieve Discord user " + discordId + ": " + failure.getMessage())
                );
            }
        }

        plugin.getLogger().info("Player " + player.getName() + " successfully linked to Discord ID " + discordId);
    }

    private static String colorize(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }
}
