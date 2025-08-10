package me.abdelrahmanmoharramdev.aquixLinkCoreBungee.Discord;

import me.abdelrahmanmoharramdev.aquixLinkCoreBungee.AquixLinkCoreBungee;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.types.InheritanceNode;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class RoleSync {

    private final AquixLinkCoreBungee plugin;
    private final LuckPerms luckPerms;

    public RoleSync(AquixLinkCoreBungee plugin, LuckPerms luckPerms) {
        this.plugin = plugin;
        this.luckPerms = luckPerms;
    }

    /**
     * Syncs a player's Minecraft LuckPerms groups based on their Discord roles.
     *
     * @param playerUuid The UUID of the Minecraft player
     * @param member     The Discord Member object representing the user
     */
    public void syncPlayerRoles(UUID playerUuid, Member member) {
        // Load role mappings from config under "rolesync", excluding "default-rank"
        Map<String, String> roleMappings = plugin.getConfig().getSection("rolesync").getKeys()
                .stream()
                .filter(key -> !key.equalsIgnoreCase("default-rank"))
                .collect(Collectors.toMap(
                        discordRoleId -> discordRoleId,
                        discordRoleId -> plugin.getConfig().getString("rolesync." + discordRoleId)
                ));

        // Get the default rank from config, defaulting to "default"
        String defaultRank = plugin.getConfig().getString("rolesync.default-rank", "default");

        // Load the LuckPerms user asynchronously
        luckPerms.getUserManager().loadUser(playerUuid).thenAccept(user -> {
            boolean updated = false;

            // Iterate through all role mappings to add or remove groups
            for (Map.Entry<String, String> entry : roleMappings.entrySet()) {
                String discordRoleId = entry.getKey();
                String mcGroup = entry.getValue();

                boolean hasDiscordRole = member.getRoles().stream()
                        .map(Role::getId)
                        .anyMatch(id -> id.equals(discordRoleId));

                boolean hasGroup = user.getNodes().stream()
                        .filter(node -> node instanceof InheritanceNode)
                        .map(node -> ((InheritanceNode) node).getGroupName())
                        .anyMatch(group -> group.equalsIgnoreCase(mcGroup));

                // Add LuckPerms group if Discord role exists but group missing
                if (hasDiscordRole && !hasGroup) {
                    user.data().add(InheritanceNode.builder(mcGroup).build());
                    updated = true;
                }

                // Remove LuckPerms group if Discord role missing but group exists
                if (!hasDiscordRole && hasGroup) {
                    user.data().remove(InheritanceNode.builder(mcGroup).build());
                    updated = true;
                }
            }

            // Determine if user has any mapped LuckPerms groups
            boolean hasAnyMappedGroup = roleMappings.values().stream()
                    .anyMatch(mcGroup -> user.getNodes().stream()
                            .filter(node -> node instanceof InheritanceNode)
                            .map(node -> ((InheritanceNode) node).getGroupName())
                            .anyMatch(group -> group.equalsIgnoreCase(mcGroup))
                    );

            // Determine if user has the default group
            boolean hasDefaultGroup = user.getNodes().stream()
                    .filter(node -> node instanceof InheritanceNode)
                    .map(node -> ((InheritanceNode) node).getGroupName())
                    .anyMatch(group -> group.equalsIgnoreCase(defaultRank));

            // Assign default rank if no mapped groups exist
            if (!hasAnyMappedGroup && !hasDefaultGroup) {
                user.data().add(InheritanceNode.builder(defaultRank).build());
                updated = true;
            }

            // Remove default rank if other groups exist
            if (hasAnyMappedGroup && hasDefaultGroup) {
                user.data().remove(InheritanceNode.builder(defaultRank).build());
                updated = true;
            }

            // Save changes to LuckPerms user if updates were made
            if (updated) {
                luckPerms.getUserManager().saveUser(user);
                plugin.getLogger().info("Synced roles for Discord user '" + member.getEffectiveName() + "' (MC UUID: " + playerUuid + ")");
            }
        }).exceptionally(ex -> {
            plugin.getLogger().severe("Failed to load LuckPerms user for UUID " + playerUuid + ": " + ex.getMessage());
            return null;
        });
    }
}
