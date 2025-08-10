package me.abdelrahmanmoharramdev.aquixLinkCoreBungee.Discord;

import me.abdelrahmanmoharramdev.aquixLinkCoreBungee.AquixLinkCoreBungee;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.types.InheritanceNode;

import java.util.*;
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
     * Preserves any other groups (like bought VIP).
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

        luckPerms.getUserManager().loadUser(playerUuid).thenAccept(user -> {
            boolean updated = false;

            // Build a set of mapped MC groups for quick lookup
            Set<String> mappedMcGroups = new HashSet<>(roleMappings.values());

            // Add groups for Discord roles the user has (if missing)
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

                if (hasDiscordRole && !hasGroup) {
                    user.data().add(InheritanceNode.builder(mcGroup).build());
                    updated = true;
                }
            }

            // Remove only mapped groups if user no longer has the corresponding Discord role
            for (String mcGroup : mappedMcGroups) {
                boolean hasDiscordRoleForGroup = roleMappings.entrySet().stream()
                        .filter(e -> e.getValue().equalsIgnoreCase(mcGroup))
                        .anyMatch(e -> member.getRoles().stream()
                                .map(Role::getId)
                                .anyMatch(id -> id.equals(e.getKey())));

                boolean hasGroup = user.getNodes().stream()
                        .filter(node -> node instanceof InheritanceNode)
                        .map(node -> ((InheritanceNode) node).getGroupName())
                        .anyMatch(group -> group.equalsIgnoreCase(mcGroup));

                if (!hasDiscordRoleForGroup && hasGroup) {
                    user.data().remove(InheritanceNode.builder(mcGroup).build());
                    updated = true;
                }
            }

            // Check if user has any mapped groups
            boolean hasAnyMappedGroup = user.getNodes().stream()
                    .filter(node -> node instanceof InheritanceNode)
                    .map(node -> ((InheritanceNode) node).getGroupName())
                    .anyMatch(mappedMcGroups::contains);

            // Check if user has the default rank
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

            // Save changes if needed
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
