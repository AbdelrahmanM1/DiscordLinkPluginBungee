package me.abdelrahmanmoharramdev.aquixLinkCoreBungee.storage;

import me.abdelrahmanmoharramdev.aquixLinkCoreBungee.AquixLinkCoreBungee;
import me.abdelrahmanmoharramdev.aquixLinkCoreBungee.cache.CacheProcessor;
import me.abdelrahmanmoharramdev.aquixLinkCoreBungee.database.DatabaseManager;
import net.dv8tion.jda.api.entities.Member;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Handles all storage-related operations for player-Discord linking, pending verifications, etc.
 */
public class LinkStorage {

    private final AquixLinkCoreBungee plugin;
    private final DatabaseManager db;
    private final CacheProcessor cacheProcessor;
    private final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final int verificationExpiryMinutes;

    public LinkStorage(AquixLinkCoreBungee plugin) {
        this.plugin = plugin;
        this.db = new DatabaseManager(plugin);
        this.cacheProcessor = new CacheProcessor();
        this.verificationExpiryMinutes = plugin.getConfig().getInt("verification-expiry-minutes", 5);
    }

    /**
     * Store a new pending verification request.
     */
    public void setPendingVerification(UUID uuid, String discordId, String code) {
        String sql = """
            INSERT OR REPLACE INTO pending_verifications (uuid, discord_id, code, created_at)
            VALUES (?, ?, ?, ?)
        """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, discordId);
            ps.setString(3, code);
            ps.setString(4, LocalDateTime.now().format(dateFormat));
            ps.executeUpdate();

            cacheProcessor.cachePendingVerification(uuid, code);
        } catch (SQLException e) {
            plugin.getLogger().severe("Error storing pending verification for " + uuid + ": " + e.getMessage());
        }
    }

    /**
     * Checks if a player has a pending verification request.
     */
    public boolean hasPendingVerification(UUID uuid) {
        if (cacheProcessor.getCachedPendingCode(uuid) != null) return true;

        String sql = "SELECT 1 FROM pending_verifications WHERE uuid = ?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Error checking pending verification for " + uuid + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Checks if the provided verification code is valid for the player.
     */
    public boolean isCodeValid(UUID uuid, String code) {
        String cachedCode = cacheProcessor.getCachedPendingCode(uuid);
        if (cachedCode != null) return cachedCode.equals(code);

        String sql = "SELECT 1 FROM pending_verifications WHERE uuid = ? AND code = ?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, code);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Error checking code validity for " + uuid + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Checks if the verification code has expired.
     */
    public boolean isVerificationExpired(UUID uuid) {
        String sql = "SELECT created_at FROM pending_verifications WHERE uuid = ?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String createdStr = rs.getString("created_at");
                    try {
                        LocalDateTime createdAt = LocalDateTime.parse(createdStr, dateFormat);
                        return createdAt.plusMinutes(verificationExpiryMinutes).isBefore(LocalDateTime.now());
                    } catch (Exception parseErr) {
                        plugin.getLogger().warning("Invalid date format in DB for " + uuid + ": " + createdStr);
                        return true; // expire if corrupted
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Error checking expiry for " + uuid + ": " + e.getMessage());
        }
        return true; // treat missing record as expired
    }

    /**
     * Remove a player's pending verification request (after expiry or completion).
     */
    public void removePendingVerification(UUID uuid) {
        String sql = "DELETE FROM pending_verifications WHERE uuid = ?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
            cacheProcessor.invalidatePendingVerification(uuid);
        } catch (SQLException e) {
            plugin.getLogger().warning("Error removing pending verification for " + uuid + ": " + e.getMessage());
        }
    }

    /**
     * Confirm verification, linking Minecraft UUID to Discord ID.
     * Moves from pending_verifications to links table, updates cache, syncs roles.
     */
    public void confirmVerification(UUID uuid) {
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);

            try (
                    PreparedStatement select = conn.prepareStatement("SELECT discord_id FROM pending_verifications WHERE uuid = ?");
                    PreparedStatement insert = conn.prepareStatement("INSERT OR REPLACE INTO links (uuid, discord_id) VALUES (?, ?)");
                    PreparedStatement delete = conn.prepareStatement("DELETE FROM pending_verifications WHERE uuid = ?");
            ) {
                select.setString(1, uuid.toString());
                try (ResultSet rs = select.executeQuery()) {
                    if (rs.next()) {
                        String discordId = rs.getString("discord_id");

                        insert.setString(1, uuid.toString());
                        insert.setString(2, discordId);
                        insert.executeUpdate();

                        delete.setString(1, uuid.toString());
                        delete.executeUpdate();

                        conn.commit();

                        cacheProcessor.cacheLinkedPlayer(uuid, discordId);
                        cacheProcessor.invalidatePendingVerification(uuid);

                        // Sync LuckPerms roles immediately after linking
                        syncRolesForPlayer(uuid, discordId);

                        return;
                    }
                }
                conn.rollback();
            } catch (SQLException e) {
                conn.rollback();
                plugin.getLogger().severe("Verification transaction failed for " + uuid + ": " + e.getMessage());
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to confirm verification for " + uuid + ": " + e.getMessage());
        }
    }

    /**
     * Immediately sync roles after linking by fetching the Discord Member(s) from all guilds.
     */
    private void syncRolesForPlayer(UUID playerUuid, String discordId) {
        plugin.getLogger().info("Syncing roles for player UUID: " + playerUuid + ", Discord ID: " + discordId);

        plugin.getDiscordBot().getJda().retrieveUserById(discordId).queue(user -> {
            plugin.getDiscordBot().getJda().getGuilds().forEach(guild -> {
                guild.retrieveMember(user).queue(member -> {
                    if (member != null) {
                        plugin.getRoleSync().syncPlayerRoles(playerUuid, member);
                    }
                }, failure -> plugin.getLogger().warning("Could not get guild member for role sync: " + failure.getMessage()));
            });
        }, failure -> plugin.getLogger().warning("Could not get Discord user for role sync: " + failure.getMessage()));
    }

    /**
     * Check if the player UUID is already linked.
     */
    public boolean isPlayerLinked(UUID uuid) {
        String cachedId = cacheProcessor.getCachedDiscordId(uuid);
        if (cachedId != null) return true;

        String sql = "SELECT discord_id FROM links WHERE uuid = ?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    cacheProcessor.cacheLinkedPlayer(uuid, rs.getString("discord_id"));
                    return true;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Error checking linked status for " + uuid + ": " + e.getMessage());
        }
        return false;
    }

    /**
     * Get Discord ID linked to given Minecraft UUID.
     */
    public String getDiscordId(UUID uuid) {
        String cached = cacheProcessor.getCachedDiscordId(uuid);
        if (cached != null) return cached;

        String sql = "SELECT discord_id FROM links WHERE uuid = ?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String discordId = rs.getString("discord_id");
                    cacheProcessor.cacheLinkedPlayer(uuid, discordId);
                    return discordId;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Error getting Discord ID for " + uuid + ": " + e.getMessage());
        }
        return null;
    }

    /**
     * Unlink a player from Discord, removing from links table and cache.
     */
    public void unlinkPlayer(UUID uuid) {
        String sql = "DELETE FROM links WHERE uuid = ?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
            cacheProcessor.invalidateLinkedPlayer(uuid);

            // After unlinking, sync roles to update ranks if needed
            String discordId = getDiscordId(uuid);
            if (discordId != null) {
                plugin.getDiscordBot().getJda().retrieveUserById(discordId).queue(user -> {
                    plugin.getDiscordBot().getJda().getGuilds().forEach(guild -> {
                        guild.retrieveMember(user).queue(member -> {
                            if (member != null) {
                                plugin.getRoleSync().syncPlayerRoles(uuid, member);
                            }
                        });
                    });
                });
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Error unlinking player for " + uuid + ": " + e.getMessage());
        }
    }

    /**
     * Check if the given Discord ID is already linked to any Minecraft UUID.
     */
    public boolean isDiscordIdLinked(String discordId) {
        String sql = "SELECT 1 FROM links WHERE discord_id = ?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, discordId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Error checking if Discord ID is linked (" + discordId + "): " + e.getMessage());
        }
        return false;
    }

    /**
     * Get Minecraft UUID linked to a given Discord ID.
     */
    public UUID getUuidByDiscordId(String discordId) {
        String sql = "SELECT uuid FROM links WHERE discord_id = ?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, discordId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return UUID.fromString(rs.getString("uuid"));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Error getting UUID by Discord ID (" + discordId + "): " + e.getMessage());
        }
        return null;
    }

    /**
     * Returns all linked players from DB, useful for syncing all at once.
     */
    public Map<UUID, String> getAllLinkedPlayers() {
        Map<UUID, String> linkedPlayers = new HashMap<>();

        String sql = "SELECT uuid, discord_id FROM links";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("uuid"));
                String discordId = rs.getString("discord_id");
                linkedPlayers.put(uuid, discordId);

                cacheProcessor.cacheLinkedPlayer(uuid, discordId);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Error loading all linked players: " + e.getMessage());
        }

        return linkedPlayers;
    }

    /**
     * Close database connections cleanly.
     */
    public void close() {
        db.close();
    }
}
