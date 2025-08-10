package me.abdelrahmanmoharramdev.aquixLinkCoreBungee;

import me.abdelrahmanmoharramdev.aquixLinkCoreBungee.Discord.DiscordBot;
import me.abdelrahmanmoharramdev.aquixLinkCoreBungee.Discord.RoleSync;
import me.abdelrahmanmoharramdev.aquixLinkCoreBungee.Listeners.PlayerJoinListener;
import me.abdelrahmanmoharramdev.aquixLinkCoreBungee.commands.LinkCommand;
import me.abdelrahmanmoharramdev.aquixLinkCoreBungee.commands.ReloadLinkCommand;
import me.abdelrahmanmoharramdev.aquixLinkCoreBungee.commands.UnlinkCommand;
import me.abdelrahmanmoharramdev.aquixLinkCoreBungee.commands.VerifyLinkCommand;
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

    // Singleton getter for plugin instance
    public static AquixLinkCoreBungee getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        loadConfig();

        // Initialize storage handler
        this.linkStorage = new LinkStorage(this);

        // Initialize role synchronization handler
        this.roleSync = new RoleSync(this, luckPerms);

        // Hook LuckPerms API if plugin is present
        if (getProxy().getPluginManager().getPlugin("LuckPerms") != null) {
            try {
                luckPerms = net.luckperms.api.LuckPermsProvider.get();
                if (luckPerms != null) {
                    getLogger().info("✅ Hooked into LuckPerms successfully!");
                } else {
                    getLogger().severe("⚠ Failed to hook into LuckPerms API instance.");
                }
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Error hooking into LuckPerms!", e);
            }
        } else {
            getLogger().severe("⚠ LuckPerms plugin not found! Role sync will not work.");
        }

        // Start Discord bot asynchronously
        startDiscordBot();

        // Register commands
        ProxyServer.getInstance().getPluginManager().registerCommand(this, new LinkCommand());
        ProxyServer.getInstance().getPluginManager().registerCommand(this, new UnlinkCommand());
        ProxyServer.getInstance().getPluginManager().registerCommand(this, new ReloadLinkCommand());
        ProxyServer.getInstance().getPluginManager().registerCommand(this, new VerifyLinkCommand());

        // Register event listeners
        getProxy().getPluginManager().registerListener(this, new PlayerJoinListener());

        getLogger().info("AquixLinkCoreBungee has been enabled! Development by 3bdoabk");
    }

    @Override
    public void onDisable() {
        // Close storage/database connections cleanly
        if (linkStorage != null) {
            linkStorage.close();
        }
        // Shutdown Discord bot connection cleanly
        if (discordBot != null && discordBot.getJda() != null) {
            discordBot.getJda().shutdownNow();
            getLogger().info("Discord bot connection closed.");
        }
        getLogger().info("AquixLinkCoreBungee has been disabled!");
    }

    // Getters & Setters for plugin components

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

    public void reloadConfig() {
        loadConfig();
    }

    // Load or create config.yml file
    private void loadConfig() {
        try {
            if (!getDataFolder().exists()) getDataFolder().mkdirs();
            File configFile = new File(getDataFolder(), "config.yml");

            if (!configFile.exists()) {
                try (InputStream in = getResourceAsStream("config.yml")) {
                    if (in == null) {
                        getLogger().severe("Default config.yml not found in jar!");
                        return;
                    }
                    Files.copy(in, configFile.toPath());
                }
                getLogger().info("Created default config.yml");
            }

            config = ConfigurationProvider.getProvider(YamlConfiguration.class).load(configFile);
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Could not load config.yml", e);
        }
    }

    // Start Discord bot asynchronously to avoid blocking the main thread
    private void startDiscordBot() {
        String token = config.getString("discord-token", "");
        if (token.isEmpty()) {
            getLogger().severe("Discord bot token is missing in config.yml! Bot will not start.");
            return;
        }

        getProxy().getScheduler().runAsync(this, () -> {
            try {
                this.discordBot = new DiscordBot(this, token);
                getLogger().info("✅ Discord bot started successfully.");
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Failed to start Discord bot: " + e.getMessage(), e);
            }
        });
    }
}
