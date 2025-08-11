package me.abdelrahmanmoharramdev.aquixLinkCoreBungee;

import me.abdelrahmanmoharramdev.aquixLinkCoreBungee.Discord.DiscordBot;
import me.abdelrahmanmoharramdev.aquixLinkCoreBungee.Discord.RoleSync;
import me.abdelrahmanmoharramdev.aquixLinkCoreBungee.Listeners.PlayerJoinListener;
import me.abdelrahmanmoharramdev.aquixLinkCoreBungee.commands.LinkCommand;
import me.abdelrahmanmoharramdev.aquixLinkCoreBungee.commands.ReloadLinkCommand;
import me.abdelrahmanmoharramdev.aquixLinkCoreBungee.commands.UnlinkCommand;
import me.abdelrahmanmoharramdev.aquixLinkCoreBungee.storage.LinkStorage;
import net.luckperms.api.LuckPerms;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.logging.Level;

public final class AquixLinkCoreBungee extends Plugin {

    private static AquixLinkCoreBungee instance;

    private LinkStorage linkStorage;
    private DiscordBot discordBot;
    private Configuration config;
    private LuckPerms luckPerms;
    private RoleSync roleSync;

    public static AquixLinkCoreBungee getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        loadConfig();

        hookLuckPerms();

        // Initialize storage and role sync
        this.linkStorage = new LinkStorage(this);
        this.roleSync = new RoleSync(this, luckPerms);

        // Start Discord bot asynchronously
        startDiscordBot();

        registerCommandsAndListeners();

        getLogger().info("=== AquixLinkCoreBungee enabled successfully! Developed by 3bdoabk ===");
    }

    @Override
    public void onDisable() {
        if (linkStorage != null) {
            linkStorage.close();
        }

        if (discordBot != null && discordBot.getJda() != null) {
            discordBot.getJda().shutdownNow();
            getLogger().info("Discord bot connection closed.");
        }

        getLogger().info("=== AquixLinkCoreBungee disabled successfully! ===");
    }

    private void hookLuckPerms() {
        if (getProxy().getPluginManager().getPlugin("LuckPerms") != null) {
            try {
                luckPerms = net.luckperms.api.LuckPermsProvider.get();
                if (luckPerms != null) {
                    getLogger().info("✅ Hooked into LuckPerms successfully.");
                } else {
                    getLogger().severe("⚠ Failed to hook into LuckPerms API instance.");
                }
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Error hooking into LuckPerms!", e);
            }
        } else {
            getLogger().severe("⚠ LuckPerms plugin not found! Role sync will not work.");
        }
    }

    private void registerCommandsAndListeners() {
        ProxyServer proxy = ProxyServer.getInstance();

        proxy.getPluginManager().registerCommand(this, new LinkCommand());
        proxy.getPluginManager().registerCommand(this, new UnlinkCommand());
        proxy.getPluginManager().registerCommand(this, new ReloadLinkCommand());
        proxy.getPluginManager().registerListener(this, new PlayerJoinListener());
    }

    private void loadConfig() {
        try {
            if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
                getLogger().severe("Could not create plugin data folder!");
            }

            File configFile = new File(getDataFolder(), "config.yml");
            if (!configFile.exists()) {
                try (InputStream in = getResourceAsStream("config.yml")) {
                    if (in == null) {
                        getLogger().severe("Default config.yml not found in plugin jar!");
                        return;
                    }
                    Files.copy(in, configFile.toPath());
                    getLogger().info("Created default config.yml");
                }
            }

            config = ConfigurationProvider.getProvider(YamlConfiguration.class).load(configFile);
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Failed to load config.yml", e);
        }
    }

    private void startDiscordBot() {
        if (config == null) {
            getLogger().severe("Config not loaded, cannot start Discord bot.");
            return;
        }

        String token = config.getString("discord-token", "").trim();
        if (token.isEmpty()) {
            getLogger().severe("Discord bot token is missing or empty in config.yml! Bot will not start.");
            return;
        }

        long verificationChannelId = config.getLong("discord-verification-channel-id", 0L);
        if (verificationChannelId <= 0) {
            getLogger().severe("Discord verification channel ID is missing or invalid in config.yml! Bot will not start.");
            return;
        }

        getProxy().getScheduler().runAsync(this, () -> {
            try {
                this.discordBot = new DiscordBot(this, token, verificationChannelId);
                getLogger().info("✅ Discord bot started successfully.");
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Failed to start Discord bot.", e);
            }
        });
    }

    // New method to get Discord guild/server ID from config.yml
    public long getGuildId() {
        if (config == null) return 0L;
        return config.getLong("discord-guild-id", 0L);
    }

    public LinkStorage getLinkStorage() {
        return linkStorage;
    }

    public DiscordBot getDiscordBot() {
        return discordBot;
    }

    public void setDiscordBot(DiscordBot bot) {
        this.discordBot = bot;
    }

    public RoleSync getRoleSync() {
        return roleSync;
    }

    public Configuration getConfig() {
        return config;
    }

    public LuckPerms getLuckPerms() {
        return luckPerms;
    }

    /**
     * Reloads plugin config and restarts Discord bot safely.
     */
    public void reloadConfig() {
        getLogger().info("Reloading configuration...");
        loadConfig();

        // Restart Discord bot with new config
        if (discordBot != null && discordBot.getJda() != null) {
            discordBot.getJda().shutdownNow();
            discordBot = null;
            getLogger().info("Old Discord bot instance shut down.");
        }
        startDiscordBot();
        getLogger().info("Configuration reloaded successfully.");
    }
}
