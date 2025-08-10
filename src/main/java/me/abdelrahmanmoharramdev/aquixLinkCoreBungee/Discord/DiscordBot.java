package me.abdelrahmanmoharramdev.aquixLinkCoreBungee.Discord;

import me.abdelrahmanmoharramdev.aquixLinkCoreBungee.AquixLinkCoreBungee;
import me.abdelrahmanmoharramdev.aquixLinkCoreBungee.storage.LinkStorage;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;

import javax.security.auth.login.LoginException;
import java.awt.*;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class DiscordBot extends ListenerAdapter {

    private final AquixLinkCoreBungee plugin;
    private final JDA jda;

    // Cooldown map to store user ID -> timestamp of last command usage
    private final Map<String, Long> cooldowns = new ConcurrentHashMap<>();

    // Cooldown time in milliseconds (e.g., 30 seconds cooldown)
    private static final long COMMAND_COOLDOWN_MS = TimeUnit.SECONDS.toMillis(30);

    // Simple localization store (key -> English message, Arabic message)
    private static final Map<String, String[]> messages = Map.ofEntries(
            Map.entry("no_pending_verification", new String[]{
                    "❌ No pending verification found for your Discord account.",
                    "❌ لم يتم العثور على تحقق معلق لحسابك في ديسكورد."
            }),
            Map.entry("code_expired", new String[]{
                    "⏳ Your verification code has expired. Please request a new one in-game.",
                    "⏳ انتهت صلاحية رمز التحقق الخاص بك. يرجى طلب رمز جديد من داخل اللعبة."
            }),
            Map.entry("invalid_code", new String[]{
                    "❌ Invalid verification code.",
                    "❌ رمز التحقق غير صالح."
            }),
            Map.entry("success", new String[]{
                    "✅ Your Minecraft account has been successfully linked!",
                    "✅ تم ربط حساب ماينكرافت الخاص بك بنجاح!"
            }),
            Map.entry("cooldown", new String[]{
                    "⏳ Please wait before using the verify command again.",
                    "⏳ يرجى الانتظار قبل استخدام أمر التحقق مرة أخرى."
            })
    );

    // Default language index: 0 = English, 1 = Arabic
    private static final int DEFAULT_LANG_INDEX = 0;

    public DiscordBot(AquixLinkCoreBungee plugin, String token) throws LoginException, InterruptedException {
        this.plugin = plugin;

        // Enable MESSAGE_CONTENT intent to read message content for text commands
        this.jda = JDABuilder.createDefault(token,
                        GatewayIntent.GUILD_MESSAGES,
                        GatewayIntent.DIRECT_MESSAGES,
                        GatewayIntent.MESSAGE_CONTENT)
                .addEventListeners(this)
                .build();

        // Wait for JDA to be ready before registering commands
        this.jda.awaitReady();

        // Register slash command /verify with required 'code' string option
        jda.updateCommands()
                .addCommands(Commands.slash("verify", "Verify your Minecraft account with a code")
                        .addOption(OptionType.STRING, "code", "Verification code", true))
                .queue();
    }

    public JDA getJda() {
        return jda;
    }

    /**
     * Sends a DM to the Discord user with the verification code using an embed message.
     *
     * @param discordId  The Discord user's ID
     * @param playerName The Minecraft player name
     * @param code       The verification code
     */
    public void sendVerificationDM(String discordId, String playerName, String code) {
        jda.retrieveUserById(discordId).queue(user -> {
            user.openPrivateChannel().queue(channel -> {
                MessageEmbed embed = createVerificationEmbed(playerName, code);
                channel.sendMessageEmbeds(embed)
                        .queue(null, failure -> plugin.getLogger().warning("Failed to send verification message: " + failure.getMessage()));
            }, failure -> plugin.getLogger().warning("Failed to open private channel: " + failure.getMessage()));
        }, failure -> {
            plugin.getLogger().warning("Failed to retrieve Discord user by ID " + discordId + ": " + failure.getMessage());
        });
    }

    /**
     * Helper method to create an embed message for verification DM.
     */
    private MessageEmbed createVerificationEmbed(String playerName, String code) {
        return new EmbedBuilder()
                .setTitle("Account Verification")
                .setDescription("Hello **" + playerName + "**!\nYour verification code is:")
                .addField("Code", "`" + code + "`", false)
                .addField("Instructions", "Please reply with `!verify " + code + "` or use `/verify` command with the same code here to link your account.", false)
                .setColor(Color.CYAN)
                .setTimestamp(Instant.now())
                .build();
    }

    /**
     * Handles incoming text messages for the old-style "!verify" command in DMs.
     */
    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        // Ignore bots and non-private messages
        if (event.getAuthor().isBot() || !event.isFromType(ChannelType.PRIVATE)) return;

        String discordId = event.getAuthor().getId();

        // Check cooldown
        if (isOnCooldown(discordId)) {
            reply(event, getMessage("cooldown", DEFAULT_LANG_INDEX), true);
            return;
        }

        String content = event.getMessage().getContentRaw().trim();
        String[] args = content.split("\\s+");

        if (args.length == 2 && args[0].equalsIgnoreCase("!verify")) {
            String code = args[1];
            // Pass event.getAuthor() (User) instead of event.getChannel().getUser()
            handleVerification(event.getAuthor(), discordId, code, event);
            setCooldown(discordId);
        }
    }

    /**
     * Handles slash commands, specifically /verify.
     */
    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equalsIgnoreCase("verify")) return;

        String discordId = event.getUser().getId();

        // Check cooldown
        if (isOnCooldown(discordId)) {
            event.reply(getMessage("cooldown", DEFAULT_LANG_INDEX))
                    .setEphemeral(true).queue();
            return;
        }

        String code = event.getOption("code").getAsString();
        handleVerification(event.getUser(), discordId, code, event);
        setCooldown(discordId);
    }

    /**
     * Core verification logic shared by both command types.
     */
    private void handleVerification(User user, String discordId, String code, Object eventContext) {
        LinkStorage linkStorage = plugin.getLinkStorage();

        UUID uuid = linkStorage.getUuidByDiscordId(discordId);

        if (uuid == null || !linkStorage.hasPendingVerification(uuid)) {
            reply(eventContext, getMessage("no_pending_verification", DEFAULT_LANG_INDEX), true);
            return;
        }

        if (linkStorage.isVerificationExpired(uuid)) {
            linkStorage.removePendingVerification(uuid);
            reply(eventContext, getMessage("code_expired", DEFAULT_LANG_INDEX), true);
            return;
        }

        if (!linkStorage.isCodeValid(uuid, code)) {
            reply(eventContext, getMessage("invalid_code", DEFAULT_LANG_INDEX), true);
            return;
        }

        // Confirm and finalize verification
        linkStorage.confirmVerification(uuid);
        linkStorage.removePendingVerification(uuid);

        reply(eventContext, getMessage("success", DEFAULT_LANG_INDEX), true);
        plugin.getLogger().info("Discord verification succeeded for user ID " + discordId);
    }

    /**
     * Sends a reply to the user, handling both MessageReceivedEvent and SlashCommandInteractionEvent.
     * For slash commands, replies ephemeral messages; for DMs sends normal message.
     */
    private void reply(Object eventContext, String message, boolean useEmbed) {
        MessageEmbed embed = null;
        if (useEmbed) {
            embed = new EmbedBuilder()
                    .setDescription(message)
                    .setColor(message.startsWith("✅") ? Color.GREEN : Color.RED)
                    .build();
        }

        if (eventContext instanceof MessageReceivedEvent e) {
            if (embed != null) {
                e.getChannel().sendMessageEmbeds(embed).queue();
            } else {
                e.getChannel().sendMessage(message).queue();
            }
        } else if (eventContext instanceof SlashCommandInteractionEvent e) {
            if (embed != null) {
                e.replyEmbeds(embed).setEphemeral(true).queue();
            } else {
                e.reply(message).setEphemeral(true).queue();
            }
        }
    }

    /**
     * Simple localization method.
     * langIndex: 0 = English, 1 = Arabic
     */
    private String getMessage(String key, int langIndex) {
        String[] localized = messages.get(key);
        if (localized == null) return key; // fallback key if missing
        if (langIndex < 0 || langIndex >= localized.length) return localized[0];
        return localized[langIndex];
    }

    /**
     * Checks if a Discord user is on cooldown for verify command.
     */
    private boolean isOnCooldown(String discordId) {
        Long lastUsed = cooldowns.get(discordId);
        if (lastUsed == null) return false;
        return (System.currentTimeMillis() - lastUsed) < COMMAND_COOLDOWN_MS;
    }

    /**
     * Sets the cooldown timestamp for a Discord user.
     */
    private void setCooldown(String discordId) {
        cooldowns.put(discordId, System.currentTimeMillis());
    }
}
