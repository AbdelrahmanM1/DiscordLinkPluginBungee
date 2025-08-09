package me.abdelrahmanmoharramdev.aquixLinkCoreBungee.commands;

import me.abdelrahmanmoharramdev.aquixLinkCoreBungee.AquixLinkCoreBungee;
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
        if (!(sender instanceof ProxiedPlayer)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return;
        }

        ProxiedPlayer player = (ProxiedPlayer) sender;
        LinkStorage linkStorage = AquixLinkCoreBungee.getInstance().getLinkStorage();

        if (linkStorage == null) {
            player.sendMessage(ChatColor.RED + "Internal error: Link storage unavailable.");
            return;
        }

        // Already linked
        if (linkStorage.isPlayerLinked(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "You are already linked to a Discord account.");
            return;
        }

        if (args.length != 1) {
            player.sendMessage(ChatColor.RED + "Usage: /verifylink <code>");
            return;
        }

        String code = args[0];

        // No pending verification
        if (!linkStorage.hasPendingVerification(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "You have no pending link request.");
            return;
        }

        // Expired check
        if (linkStorage.isVerificationExpired(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Your verification code has expired. Please use /linkdiscord again.");
            linkStorage.removePendingVerification(player.getUniqueId());
            return;
        }

        // Code match check
        if (!linkStorage.isCodeValid(player.getUniqueId(), code)) {
            player.sendMessage(ChatColor.RED + "Invalid verification code.");
            return;
        }

        // Success: confirm linking and remove pending verification
        linkStorage.confirmVerification(player.getUniqueId());
        linkStorage.removePendingVerification(player.getUniqueId());
        player.sendMessage(ChatColor.GREEN + "Your Discord account has been successfully linked!");
    }
}
