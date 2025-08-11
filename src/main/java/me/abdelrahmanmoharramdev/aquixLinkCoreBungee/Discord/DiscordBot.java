package me.abdelrahmanmoharramdev.aquixLinkCoreBungee.Discord;

import me.abdelrahmanmoharramdev.aquixLinkCoreBungee.AquixLinkCoreBungee;
import me.abdelrahmanmoharramdev.aquixLinkCoreBungee.storage.LinkStorage;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.TextComponent;

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
    private final long verificationChannelId;

    private final Map<String, Long> cooldowns = new ConcurrentHashMap<>();
    private static final long COMMAND_COOLDOWN_MS = TimeUnit.SECONDS.toMillis(30);

    // Localization messages
    private static final Map<String, String[]> MESSAGES = Map.ofEntries(
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
            }),
            Map.entry("prompt_verification", new String[]{
                    "ℹ️ Please verify yourself by using the `/verify <code>` command in this channel.",
                    "ℹ️ يرجى التحقق من نفسك باستخدام أمر `/verify <code>` في هذه القناة."
            }),
            Map.entry("already_linked_minecraft", new String[]{
                    "⚠️ This Minecraft account is already linked to a Discord account.",
                    "⚠️ هذا الحساب ماينكرافت مرتبط بالفعل بحساب ديسكورد."
            }),
            Map.entry("already_linked_discord", new String[]{
                    "⚠️ This Discord account is already linked to a Minecraft account.",
                    "⚠️ هذا الحساب ديسكورد مرتبط بالفعل بحساب ماينكرافت."
            })
    );

    private static final int DEFAULT_LANG_INDEX = 0;

    public DiscordBot(AquixLinkCoreBungee plugin, String token, long verificationChannelId) throws LoginException, InterruptedException {
        this.plugin = plugin;
        this.verificationChannelId = verificationChannelId;

        this.jda = JDABuilder.createDefault(token,
                        GatewayIntent.GUILD_MESSAGES,
                        GatewayIntent.GUILD_MESSAGE_REACTIONS,
                        GatewayIntent.MESSAGE_CONTENT)
                .addEventListeners(this)
                .build();

        this.jda.awaitReady();

        // Register /verify command with required code option
        jda.updateCommands()
                .addCommands(Commands.slash("verify", "Verify your Minecraft account with a code")
                        .addOption(OptionType.STRING, "code", "Verification code", true))
                .queue();

        sendPromptMessage();
    }

    public JDA getJda() {
        return jda;
    }

    private void sendPromptMessage() {
        TextChannel channel = jda.getTextChannelById(verificationChannelId);
        if (channel == null) {
            plugin.getLogger().warning("Verification channel not found: " + verificationChannelId);
            return;
        }

        MessageEmbed embed = new EmbedBuilder()
                .setTitle("Minecraft Account Verification")
                .setDescription(getMessage("prompt_verification", DEFAULT_LANG_INDEX))
                .setColor(Color.BLUE)
                .setTimestamp(Instant.now())
                .build();

        channel.sendMessageEmbeds(embed).queue();
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!"verify".equalsIgnoreCase(event.getName())) return;

        long channelId = event.getChannel().getIdLong();
        boolean isVerificationChannel = channelId == verificationChannelId;
        boolean isPrivateChannel = event.getChannel().getType() == ChannelType.PRIVATE;

        if (!isVerificationChannel && !isPrivateChannel) {
            event.reply("❌ Please use this command only in the designated verification channel or via DM.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        String discordId = event.getUser().getId();

        if (isOnCooldown(discordId)) {
            event.reply(getMessage("cooldown", DEFAULT_LANG_INDEX))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        String code = event.getOption("code").getAsString();

        handleVerification(event, discordId, code);

        setCooldown(discordId);
    }

    public void sendVerificationDM(String guildId, String discordId, String playerName, String code) {
        Guild guild = jda.getGuildById(guildId);
        if (guild == null) {
            plugin.getLogger().warning("Could not find Guild with ID: " + guildId);
            return;
        }

        guild.retrieveMemberById(discordId).queue(member -> {
            member.getUser().openPrivateChannel().queue(channel -> {
                MessageEmbed embed = createVerificationEmbed(playerName, code);
                channel.sendMessageEmbeds(embed)
                        .queue(
                                success -> plugin.getLogger().info("Successfully sent verification DM to " + member.getEffectiveName()),
                                failure -> plugin.getLogger().warning("Failed to send DM to " + member.getEffectiveName() + ". Error: " + failure.getMessage())
                        );
            }, failure -> plugin.getLogger().warning("Failed to open private channel with " + member.getEffectiveName() + ". Error: " + failure.getMessage()));
        }, failure -> {
            plugin.getLogger().warning("Failed to retrieve member with ID " + discordId + " from guild " + guild.getName() + ". Error: " + failure.getMessage());
        });
    }

    private MessageEmbed createVerificationEmbed(String playerName, String code) {
        return new EmbedBuilder()
                .setTitle("Minecraft Account Verification")
                .setDescription(String.format(
                        "Hello %s!\nYour Minecraft account `%s` is attempting to link with this Discord account.\n" +
                                "Please verify yourself by typing `/verify %s` in the verification channel.\n" +
                                "This code is valid for 5 minutes.",
                        playerName, playerName, code))
                .setColor(Color.CYAN)
                .setTimestamp(Instant.now())
                .build();
    }

    private void handleVerification(SlashCommandInteractionEvent event, String discordId, String code) {
        LinkStorage linkStorage = plugin.getLinkStorage();

        UUID uuid = linkStorage.getPendingVerificationUuidByDiscordIdAndCode(discordId, code);
        if (uuid == null) {
            replyWithEmbed(event, getMessage("no_pending_verification", DEFAULT_LANG_INDEX), false);
            return;
        }

        if (linkStorage.isPlayerLinked(uuid)) {
            replyWithEmbed(event, getMessage("already_linked_minecraft", DEFAULT_LANG_INDEX), false);
            return;
        }

        if (linkStorage.isDiscordIdLinked(discordId)) {
            replyWithEmbed(event, getMessage("already_linked_discord", DEFAULT_LANG_INDEX), false);
            return;
        }

        if (linkStorage.isVerificationExpired(uuid)) {
            linkStorage.removePendingVerification(uuid);
            replyWithEmbed(event, getMessage("code_expired", DEFAULT_LANG_INDEX), false);
            return;
        }

        if (!linkStorage.isCodeValid(uuid, code)) {
            replyWithEmbed(event, getMessage("invalid_code", DEFAULT_LANG_INDEX), false);
            return;
        }

        linkStorage.confirmVerification(uuid);
        linkStorage.removePendingVerification(uuid);

        event.reply(getMessage("success", DEFAULT_LANG_INDEX))
                .setEphemeral(true)
                .queue();

        var proxy = plugin.getProxy();
        var mcPlayer = proxy.getPlayer(uuid);
        if (mcPlayer != null) {
            mcPlayer.sendMessage(new TextComponent(ChatColor.GREEN + "You are successfully linked with Discord: " + event.getUser().getName()));
        }

        plugin.getLogger().info("Discord verification succeeded for user ID " + discordId);
    }

    private void replyWithEmbed(SlashCommandInteractionEvent event, String message, boolean success) {
        MessageEmbed embed = new EmbedBuilder()
                .setDescription(message)
                .setColor(success ? Color.GREEN : Color.RED)
                .setTimestamp(Instant.now())
                .build();

        event.replyEmbeds(embed).setEphemeral(true).queue();
    }

    private boolean isOnCooldown(String discordId) {
        Long lastUsed = cooldowns.get(discordId);
        return lastUsed != null && (System.currentTimeMillis() - lastUsed) < COMMAND_COOLDOWN_MS;
    }

    private void setCooldown(String discordId) {
        cooldowns.put(discordId, System.currentTimeMillis());
    }

    private String getMessage(String key, int langIndex) {
        String[] localized = MESSAGES.get(key);
        if (localized == null) return key;
        if (langIndex < 0 || langIndex >= localized.length) return localized[0];
        return localized[langIndex];
    }
}
