package me.abdelrahmanmoharramdev.aquixLinkCoreBungee.commands;

import me.abdelrahmanmoharramdev.aquixLinkCoreBungee.AquixLinkCoreBungee;
import me.abdelrahmanmoharramdev.aquixLinkCoreBungee.storage.LinkStorage;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;

import java.security.SecureRandom;

public class LinkCommand extends Command {

    private static final int CODE_LENGTH = 6;
    private static final int MAX_CODE = 1_000_000; // 6-digit code max
    private final SecureRandom random = new SecureRandom();

    public LinkCommand() {
        super("linkdiscord", null, "link"); // command name, permission (null = none), aliases
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof ProxiedPlayer)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return;
        }

        ProxiedPlayer player = (ProxiedPlayer) sender;
        LinkStorage linkStorage = AquixLinkCoreBungee.getInstance().getLinkStorage();

        if (linkStorage == null) {
            player.sendMessage(ChatColor.RED + "Internal error: link storage unavailable.");
            AquixLinkCoreBungee.getInstance().getLogger().severe("LinkStorage instance is null in LinkCommand.");
            return;
        }

        // Already linked
        if (linkStorage.isPlayerLinked(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "You are already linked to a Discord account. " +
                    ChatColor.GREEN + "To unlink, use /unlinkdiscord.");
            return;
        }

        // Pending verification exists? Check if expired
        if (linkStorage.hasPendingVerification(player.getUniqueId())) {
            if (linkStorage.isVerificationExpired(player.getUniqueId())) {
                // Remove expired pending verification so player can create a new one
                linkStorage.removePendingVerification(player.getUniqueId());
            } else {
                player.sendMessage(ChatColor.RED + "You already have a pending verification request.");
                player.sendMessage(ChatColor.GRAY + "Use " + ChatColor.YELLOW + "/verifylink <code>" + ChatColor.GRAY + " to complete linking.");
                return;
            }
        }

        // Validate command usage
        if (args.length != 1) {
            player.sendMessage(ChatColor.RED + "Usage: /linkdiscord <discord_id>");
            return;
        }

        String discordId = args[0];

        // Validate Discord ID format
        if (!discordId.matches("^\\d{17,20}$")) {
            player.sendMessage(ChatColor.RED + "Invalid Discord ID. It should be a 17–20 digit number.");
            return;
        }

        // Check if Discord ID is already linked to another player
        if (linkStorage.isDiscordIdLinked(discordId)) {
            player.sendMessage(ChatColor.RED + "This Discord ID is already linked to another player.");
            return;
        }

        // Generate and store a 6-digit verification code
        String code = String.format("%0" + CODE_LENGTH + "d", random.nextInt(MAX_CODE));
        linkStorage.setPendingVerification(player.getUniqueId(), discordId, code);

        // Send success messages
        player.sendMessage(ChatColor.GREEN + "✅ A verification code has been generated.");
        player.sendMessage(ChatColor.GRAY + "To complete the process, type:");
        player.sendMessage(ChatColor.YELLOW + "/verifylink " + code);
        player.sendMessage(ChatColor.GRAY + "Note: This code is valid for 5 minutes.");
    }
}
