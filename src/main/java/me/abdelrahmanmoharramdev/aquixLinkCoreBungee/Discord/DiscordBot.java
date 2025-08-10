package me.abdelrahmanmoharramdev.aquixLinkCoreBungee.Discord;

import me.abdelrahmanmoharramdev.aquixLinkCoreBungee.AquixLinkCoreBungee;
import me.abdelrahmanmoharramdev.aquixLinkCoreBungee.storage.LinkStorage;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import javax.security.auth.login.LoginException;
import java.util.UUID;

public class DiscordBot extends ListenerAdapter {

    private final AquixLinkCoreBungee plugin;
    private final JDA jda;

    public DiscordBot(AquixLinkCoreBungee plugin, String token) throws LoginException {
        this.plugin = plugin;
        this.jda = JDABuilder.createDefault(token)
                .addEventListeners(this)
                .build();
    }

    public JDA getJda() {
        return jda;
    }

    /**
     * Sends a DM to the Discord user with the verification code.
     *
     * @param discordId  The Discord user's ID
     * @param playerName The Minecraft player name
     * @param code       The verification code
     */
    public void sendVerificationDM(String discordId, String playerName, String code) {
        jda.retrieveUserById(discordId).queue(user -> {
            user.openPrivateChannel().queue(channel -> {
                channel.sendMessage("Hello **" + playerName + "**! Your verification code is: `"
                                + code + "`\nPlease reply with `!verify " + code + "` here to link your account.")
                        .queue(null, failure -> plugin.getLogger().warning("Failed to send verification message: " + failure.getMessage()));
            }, failure -> plugin.getLogger().warning("Failed to open private channel: " + failure.getMessage()));
        }, failure -> {
            plugin.getLogger().warning("Failed to retrieve Discord user by ID " + discordId + ": " + failure.getMessage());
        });
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        // Ignore bots and non-private messages
        if (event.getAuthor().isBot() || !event.isFromType(ChannelType.PRIVATE)) return;

        String content = event.getMessage().getContentRaw().trim();
        String[] args = content.split("\\s+");

        if (args.length == 2 && args[0].equalsIgnoreCase("!verify")) {
            String code = args[1];
            String discordId = event.getAuthor().getId();

            LinkStorage linkStorage = plugin.getLinkStorage();

            // Get UUID associated with Discord ID in pending verifications
            UUID uuid = linkStorage.getUuidByDiscordId(discordId);

            if (uuid == null || !linkStorage.hasPendingVerification(uuid)) {
                event.getChannel().sendMessage("❌ No pending verification found for your Discord account.").queue();
                return;
            }

            if (linkStorage.isVerificationExpired(uuid)) {
                linkStorage.removePendingVerification(uuid);
                event.getChannel().sendMessage("⏳ Your verification code has expired. Please request a new one in-game.").queue();
                return;
            }

            if (!linkStorage.isCodeValid(uuid, code)) {
                event.getChannel().sendMessage("❌ Invalid verification code.").queue();
                return;
            }

            // Confirm and finalize verification
            linkStorage.confirmVerification(uuid);
            linkStorage.removePendingVerification(uuid);

            event.getChannel().sendMessage("✅ Your Minecraft account has been successfully linked!").queue();
            plugin.getLogger().info("Discord verification succeeded for user ID " + discordId);
        }
    }
}
