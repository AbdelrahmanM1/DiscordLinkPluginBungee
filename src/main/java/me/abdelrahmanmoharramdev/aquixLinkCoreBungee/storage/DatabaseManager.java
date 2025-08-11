package me.abdelrahmanmoharramdev.aquixLinkCoreBungee.database;

import me.abdelrahmanmoharramdev.aquixLinkCoreBungee.AquixLinkCoreBungee;

import java.io.File;
import java.sql.*;
import java.util.logging.Level;

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
     * Safe to call multiple times.
     */
    private void initialize() {
        try {
            Class.forName("org.sqlite.JDBC");

            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists() && !dataFolder.mkdirs()) {
                throw new IllegalStateException("Failed to create plugin data folder!");
            }

            File dbFile = new File(dataFolder, "links.db");
            String jdbcUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath();
            connection = DriverManager.getConnection(jdbcUrl);
            connection.setAutoCommit(false); // for atomic schema setup

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
                        created_at INTEGER NOT NULL
                    );
                """);

                stmt.executeUpdate("""
                    CREATE INDEX IF NOT EXISTS idx_pending_discord_id
                    ON pending_verifications (discord_id);
                """);
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }

            plugin.getLogger().info("SQLite database initialized successfully.");

        } catch (ClassNotFoundException e) {
            plugin.getLogger().log(Level.SEVERE, "SQLite JDBC driver not found!", e);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "SQL error during database initialization.", e);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Unexpected error during database initialization.", e);
        } finally {
            try {
                if (connection != null && !connection.getAutoCommit()) {
                    connection.setAutoCommit(true);
                }
            } catch (SQLException ignored) {}
        }
    }

    /**
     * Gets the current SQLite connection, reinitializing if closed or null.
     * @return the SQLite connection
     */
    public Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                initialize();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error checking database connection.", e);
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
