package me.abdelrahmanmoharramdev.aquixLinkCoreBungee.Discord;

import me.abdelrahmanmoharramdev.aquixLinkCoreBungee.AquixLinkCoreBungee;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.types.InheritanceNode;

import java.util.*;
import java.util.concurrent.CompletableFuture;
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
        Map<String, String> roleMappings = getRoleMappings();
        String defaultRank = plugin.getConfig().getString("rolesync.default-rank", "default");

        luckPerms.getUserManager().loadUser(playerUuid).thenAccept(user -> {
            Set<String> currentGroups = getUserGroups(user);
            Set<String> mappedMcGroups = new HashSet<>(roleMappings.values());

            boolean updated = false;

            // Add missing groups based on Discord roles
            for (Map.Entry<String, String> entry : roleMappings.entrySet()) {
                String discordRoleId = entry.getKey();
                String mcGroup = entry.getValue();

                boolean hasDiscordRole = hasDiscordRole(member, discordRoleId);
                boolean hasGroup = currentGroups.stream().anyMatch(g -> g.equalsIgnoreCase(mcGroup));

                if (hasDiscordRole && !hasGroup) {
                    user.data().add(InheritanceNode.builder(mcGroup).build());
                    updated = true;
                    plugin.getLogger().info("Added MC group '" + mcGroup + "' for user " + member.getEffectiveName());
                }
            }

            // Remove mapped groups if Discord role no longer exists
            for (String mcGroup : mappedMcGroups) {
                boolean hasDiscordRoleForGroup = roleMappings.entrySet().stream()
                        .filter(e -> e.getValue().equalsIgnoreCase(mcGroup))
                        .anyMatch(e -> hasDiscordRole(member, e.getKey()));

                boolean hasGroup = currentGroups.stream().anyMatch(g -> g.equalsIgnoreCase(mcGroup));

                if (!hasDiscordRoleForGroup && hasGroup) {
                    user.data().remove(InheritanceNode.builder(mcGroup).build());
                    updated = true;
                    plugin.getLogger().info("Removed MC group '" + mcGroup + "' for user " + member.getEffectiveName());
                }
            }

            boolean hasAnyMappedGroup = currentGroups.stream()
                    .anyMatch(mappedMcGroups::contains);

            boolean hasDefaultGroup = currentGroups.stream()
                    .anyMatch(g -> g.equalsIgnoreCase(defaultRank));

            // Add default rank if no mapped groups exist
            if (!hasAnyMappedGroup && !hasDefaultGroup) {
                user.data().add(InheritanceNode.builder(defaultRank).build());
                updated = true;
                plugin.getLogger().info("Added default rank '" + defaultRank + "' to user " + member.getEffectiveName());
            }

            // Remove default rank if other groups exist
            if (hasAnyMappedGroup && hasDefaultGroup) {
                user.data().remove(InheritanceNode.builder(defaultRank).build());
                updated = true;
                plugin.getLogger().info("Removed default rank '" + defaultRank + "' from user " + member.getEffectiveName());
            }

            if (updated) {
                luckPerms.getUserManager().saveUser(user);
                plugin.getLogger().info("Synced roles for Discord user '" + member.getEffectiveName() + "' (MC UUID: " + playerUuid + ")");
            }
        }).exceptionally(ex -> {
            plugin.getLogger().severe("Failed to load LuckPerms user for UUID " + playerUuid + ": " + ex.getMessage());
            return null;
        });
    }

    private Map<String, String> getRoleMappings() {
        return plugin.getConfig().getSection("rolesync").getKeys()
                .stream()
                .filter(key -> !key.equalsIgnoreCase("default-rank"))
                .collect(Collectors.toMap(
                        key -> key,
                        key -> plugin.getConfig().getString("rolesync." + key)
                ));
    }

    private boolean hasDiscordRole(Member member, String discordRoleId) {
        return member.getRoles().stream()
                .map(Role::getId)
                .anyMatch(id -> id.equals(discordRoleId));
    }

    private Set<String> getUserGroups(User user) {
        return user.getNodes().stream()
                .filter(node -> node instanceof InheritanceNode)
                .map(node -> ((InheritanceNode) node).getGroupName())
                .collect(Collectors.toSet());
    }
}
