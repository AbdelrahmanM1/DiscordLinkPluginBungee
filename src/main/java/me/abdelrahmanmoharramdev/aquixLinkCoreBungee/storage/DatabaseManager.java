package me.abdelrahmanmoharramdev.aquixLinkCoreBungee.database;

import me.abdelrahmanmoharramdev.aquixLinkCoreBungee.AquixLinkCoreBungee;

import java.io.File;
import java.sql.*;

/**
 * Handles SQLite database connection and schema initialization.
 */
public class DatabaseManager {

    private final AquixLinkCoreBungee plugin;
    private Connection connection;

    public DatabaseManager(AquixLinkCoreBungee plugin) {
        this.plugin = plugin;
        initialize();
    }

    /**
     * Initializes the database connection and creates tables if needed.
     */
    private void initialize() {
        try {
            // Load SQLite JDBC driver
            Class.forName("org.sqlite.JDBC");

            // Ensure data folder exists
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists() && !dataFolder.mkdirs()) {
                throw new IllegalStateException("Failed to create plugin data folder!");
            }

            // Open connection to SQLite database file
            File dbFile = new File(dataFolder, "links.db");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());

            // Create tables and indexes if not exist
            try (Statement stmt = connection.createStatement()) {
                stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS links (
                        uuid TEXT PRIMARY KEY,
                        discord_id TEXT NOT NULL UNIQUE
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

                stmt.executeUpdate("""
                    CREATE INDEX IF NOT EXISTS idx_pending_discord_id
                    ON pending_verifications (discord_id);
                """);
            }

            plugin.getLogger().info("SQLite database initialized successfully.");
        } catch (ClassNotFoundException e) {
            plugin.getLogger().severe("SQLite JDBC driver not found! " + e.getMessage());
        } catch (SQLException e) {
            plugin.getLogger().severe("SQL error during database initialization: " + e.getMessage());
        } catch (Exception e) {
            plugin.getLogger().severe("Unexpected error during database initialization: " + e.getMessage());
        }
    }

    /**
     * Gets the current connection. Reinitializes if closed or null.
     * @return the SQLite connection
     */
    public Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                initialize();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error checking database connection: " + e.getMessage());
        }
        return connection;
    }

    /**
     * Checks if the database connection is open.
     * @return true if connected and open, false otherwise
     */
    public boolean isConnected() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            plugin.getLogger().warning("Error checking if database is connected: " + e.getMessage());
            return false;
        }
    }

    /**
     * Closes the database connection if open.
     */
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
