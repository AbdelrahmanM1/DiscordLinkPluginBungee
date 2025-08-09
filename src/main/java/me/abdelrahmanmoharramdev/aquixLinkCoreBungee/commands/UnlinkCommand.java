package me.abdelrahmanmoharramdev.aquixLinkCoreBungee.commands;

import me.abdelrahmanmoharramdev.aquixLinkCoreBungee.AquixLinkCoreBungee;
import me.abdelrahmanmoharramdev.aquixLinkCoreBungee.storage.LinkStorage;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;

public class UnlinkCommand extends Command {

    public UnlinkCommand() {
        super("unlinkdiscord", null, "unlink"); // main command, no permission, alias
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
            player.sendMessage(ChatColor.RED + "An internal error occurred (link storage is null).");
            return;
        }

        if (!linkStorage.isPlayerLinked(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "You are not linked to any Discord account. "
                    + ChatColor.GREEN + "To link, use /linkdiscord <discord_id>.");
            return;
        }

        linkStorage.unlinkPlayer(player.getUniqueId());
        player.sendMessage(ChatColor.GREEN + "Successfully unlinked your Discord account.");
    }
}
