package me.abdelrahmanmoharramdev.aquixLinkCoreBungee;

import me.abdelrahmanmoharramdev.aquixLinkCoreBungee.commands.LinkCommand;
import me.abdelrahmanmoharramdev.aquixLinkCoreBungee.commands.ReloadLinkCommand;
import me.abdelrahmanmoharramdev.aquixLinkCoreBungee.commands.UnlinkCommand;
import me.abdelrahmanmoharramdev.aquixLinkCoreBungee.Listeners.PlayerJoinListener;
import me.abdelrahmanmoharramdev.aquixLinkCoreBungee.commands.VerifyLinkCommand;
import me.abdelrahmanmoharramdev.aquixLinkCoreBungee.storage.LinkStorage;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.plugin.Plugin;

public final class AquixLinkCoreBungee extends Plugin {

    private static AquixLinkCoreBungee instance;
    private LinkStorage linkStorage;

    public static AquixLinkCoreBungee getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        // Initialize SQLite storage
        this.linkStorage = new LinkStorage(this);

        // Register commands
        ProxyServer.getInstance().getPluginManager().registerCommand(this, new LinkCommand());
        ProxyServer.getInstance().getPluginManager().registerCommand(this, new UnlinkCommand());
        ProxyServer.getInstance().getPluginManager().registerCommand(this, new ReloadLinkCommand());
        ProxyServer.getInstance().getPluginManager().registerCommand(this, new VerifyLinkCommand());
        // Register Events
        getProxy().getPluginManager().registerListener(this, new PlayerJoinListener());

        getLogger().info("AquixLinkCoreBungee has been enabled! Development by 3bdoabk");
    }

    @Override
    public void onDisable() {
        if (linkStorage != null) {
            linkStorage.close();
        }
        getLogger().info("AquixLinkCoreBungee has been disabled!");
    }

    public LinkStorage getLinkStorage() {
        return linkStorage;
    }
}
