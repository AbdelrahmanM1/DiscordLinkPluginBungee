package me.abdelrahmanmoharramdev.aquixLinkCoreBungee.commands;

import me.abdelrahmanmoharramdev.aquixLinkCoreBungee.AquixLinkCoreBungee;
import me.abdelrahmanmoharramdev.aquixLinkCoreBungee.Discord.DiscordBot;
import me.abdelrahmanmoharramdev.aquixLinkCoreBungee.storage.LinkStorage;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;

import java.security.SecureRandom;

public class LinkCommand extends Command {

    private static final int CODE_LENGTH = 6;
    private static final int MAX_CODE = 1_000_000; // 6-digit max (from 000000 to 999999)
    private static final long VERIFICATION_CODE_VALIDITY_MINUTES = 5;

    private final SecureRandom random = new SecureRandom();

    public LinkCommand() {
        super("linkdiscord", null, "link");
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
            player.sendMessage(colorize("&cInternal error: link storage unavailable."));
            plugin.getLogger().severe("LinkStorage instance is null in LinkCommand.");
            return;
        }

        if (linkStorage.isPlayerLinked(player.getUniqueId())) {
            player.sendMessage(colorize("&cYou are already linked to a Discord account. &aTo unlink, use /unlinkdiscord."));
            return;
        }

        // Handle existing pending verification (check expiration and remove if expired)
        if (linkStorage.hasPendingVerification(player.getUniqueId())) {
            if (linkStorage.isVerificationExpired(player.getUniqueId())) {
                linkStorage.removePendingVerification(player.getUniqueId());
            } else {
                player.sendMessage(colorize("&cYou already have a pending verification request."));
                player.sendMessage(colorize("&7Check your Discord DM  to  complete linking."));
                return;
            }
        }

        // Validate command arguments
        if (args.length != 1) {
            player.sendMessage(colorize("&cUsage: /linkdiscord <discord_id>"));
            return;
        }

        String discordId = args[0];

        if (!isValidDiscordId(discordId)) {
            player.sendMessage(colorize("&cInvalid Discord ID. It should be a 17–20 digit number."));
            return;
        }

        if (linkStorage.isDiscordIdLinked(discordId)) {
            player.sendMessage(colorize("&cThis Discord ID is already linked to another player."));
            return;
        }

        // Generate verification code and save it
        String verificationCode = generateVerificationCode();
        linkStorage.setPendingVerification(player.getUniqueId(), discordId, verificationCode);

        // Inform player about next steps
        player.sendMessage(colorize("&7Please enter this command in the Discord verification channel:"));
        player.sendMessage(colorize("&7&l/verify " + verificationCode));

        // Send DM via Discord bot
        DiscordBot discordBot = plugin.getDiscordBot();
        if (discordBot != null) {
            discordBot.sendVerificationDM(String.valueOf(plugin.getGuildId()), discordId, player.getName(), verificationCode);
            player.sendMessage(colorize("&aA verification code has also been sent to your Discord DMs."));
        } else {
            player.sendMessage(colorize("&e⚠ Verification code generated, but the Discord bot is currently offline."));
        }

        player.sendMessage(colorize("&7Note: This code is valid for " + VERIFICATION_CODE_VALIDITY_MINUTES + " minutes."));
        player.sendMessage(colorize("&7If you didn't get a DM, ensure your Discord privacy settings allow messages from server members."));
    }

    // Helper method: colorize messages using & codes
    private static String colorize(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    // Helper method: validate Discord ID format (17 to 20 digits)
    private static boolean isValidDiscordId(String discordId) {
        return discordId != null && discordId.matches("^\\d{17,20}$");
    }

    // Helper method: generate a zero-padded 6-digit verification code
    private String generateVerificationCode() {
        int code = random.nextInt(MAX_CODE);
        return String.format("%0" + CODE_LENGTH + "d", code);
    }
}
