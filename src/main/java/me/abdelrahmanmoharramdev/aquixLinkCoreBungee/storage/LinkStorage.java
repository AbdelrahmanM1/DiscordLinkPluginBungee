package me.abdelrahmanmoharramdev.aquixLinkCoreBungee.storage;

import me.abdelrahmanmoharramdev.aquixLinkCoreBungee.AquixLinkCoreBungee;
import me.abdelrahmanmoharramdev.aquixLinkCoreBungee.cache.CacheProcessor;
import me.abdelrahmanmoharramdev.aquixLinkCoreBungee.database.DatabaseManager;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public class LinkStorage {

    private final AquixLinkCoreBungee plugin;
    private final DatabaseManager db;
    private final CacheProcessor cacheProcessor;

    public LinkStorage(AquixLinkCoreBungee plugin) {
        this.plugin = plugin;
        this.db = new DatabaseManager(plugin);
        this.cacheProcessor = new CacheProcessor();
    }

    public void setPendingVerification(UUID uuid, String discordId, String code) {
        String sql = """
            INSERT OR REPLACE INTO pending_verifications (uuid, discord_id, code, created_at)
            VALUES (?, ?, ?, ?)
        """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, discordId);
            ps.setString(3, code);
            ps.setString(4, LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            ps.executeUpdate();

            cacheProcessor.cachePendingVerification(uuid, code);
        } catch (SQLException e) {
            plugin.getLogger().severe("Error storing pending verification: " + e.getMessage());
        }
    }

    public boolean hasPendingVerification(UUID uuid) {
        if (cacheProcessor.getCachedPendingCode(uuid) != null) return true;

        String sql = "SELECT 1 FROM pending_verifications WHERE uuid = ?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Error checking pending verification: " + e.getMessage());
            return false;
        }
    }

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
            plugin.getLogger().warning("Error checking code validity: " + e.getMessage());
            return false;
        }
    }

    public boolean isVerificationExpired(UUID uuid) {
        String sql = "SELECT created_at FROM pending_verifications WHERE uuid = ?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    LocalDateTime createdAt = LocalDateTime.parse(rs.getString("created_at"), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                    return createdAt.plusMinutes(5).isBefore(LocalDateTime.now());
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error checking expiry: " + e.getMessage());
        }
        return true;
    }

    public void removePendingVerification(UUID uuid) {
        String sql = "DELETE FROM pending_verifications WHERE uuid = ?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
            cacheProcessor.invalidatePendingVerification(uuid);
        } catch (SQLException e) {
            plugin.getLogger().warning("Error removing pending verification: " + e.getMessage());
        }
    }

    public void confirmVerification(UUID uuid) {
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);

            try (
                    PreparedStatement select = conn.prepareStatement("SELECT discord_id FROM pending_verifications WHERE uuid = ?");
                    PreparedStatement insert = conn.prepareStatement("INSERT OR REPLACE INTO links (uuid, discord_id) VALUES (?, ?)");
                    PreparedStatement delete = conn.prepareStatement("DELETE FROM pending_verifications WHERE uuid = ?")
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
                        return;
                    }
                }
                conn.rollback();
            } catch (SQLException e) {
                conn.rollback();
                plugin.getLogger().severe("Verification failed: " + e.getMessage());
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to confirm verification: " + e.getMessage());
        }
    }

    public boolean isPlayerLinked(UUID uuid) {
        if (cacheProcessor.getCachedDiscordId(uuid) != null) return true;

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
            plugin.getLogger().warning("Error checking linked player: " + e.getMessage());
        }
        return false;
    }

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
            plugin.getLogger().warning("Error getting Discord ID: " + e.getMessage());
        }
        return null;
    }

    public void unlinkPlayer(UUID uuid) {
        String sql = "DELETE FROM links WHERE uuid = ?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
            cacheProcessor.invalidateLinkedPlayer(uuid);
        } catch (SQLException e) {
            plugin.getLogger().warning("Error unlinking player: " + e.getMessage());
        }
    }

    public boolean isDiscordIdLinked(String discordId) {
        String sql = "SELECT 1 FROM links WHERE discord_id = ?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, discordId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Error checking Discord ID link: " + e.getMessage());
        }
        return false;
    }

    public void close() {
        db.close();
    }
}
