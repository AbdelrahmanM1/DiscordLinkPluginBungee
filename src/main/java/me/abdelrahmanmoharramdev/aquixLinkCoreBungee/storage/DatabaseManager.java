package me.abdelrahmanmoharramdev.aquixLinkCoreBungee.database;

import me.abdelrahmanmoharramdev.aquixLinkCoreBungee.AquixLinkCoreBungee;

import java.io.File;
import java.sql.*;

public class DatabaseManager {

    private final AquixLinkCoreBungee plugin;
    private Connection connection;

    public DatabaseManager(AquixLinkCoreBungee plugin) {
        this.plugin = plugin;
        initialize();
    }

    private void initialize() {
        try {
            File dataFolder = new File(plugin.getDataFolder(), "");
            if (!dataFolder.exists()) dataFolder.mkdirs();

            File dbFile = new File(dataFolder, "links.db");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());

            try (Statement stmt = connection.createStatement()) {
                stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS links (
                        uuid TEXT PRIMARY KEY,
                        discord_id TEXT NOT NULL
                    );
                """);

                stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS pending_verifications (
                        uuid TEXT PRIMARY KEY,
                        discord_id TEXT NOT NULL,
                        code TEXT NOT NULL,
                        created_at TEXT NOT NULL
                    );
                """);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to initialize database: " + e.getMessage());
        }
    }

    public Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) initialize();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error reconnecting to database: " + e.getMessage());
        }
        return connection;
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                plugin.getLogger().info("Database connection closed.");
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Error closing database: " + e.getMessage());
        }
    }
}
