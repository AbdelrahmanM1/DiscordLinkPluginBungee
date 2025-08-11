package me.abdelrahmanmoharramdev.aquixLinkCoreBungee.cache;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Handles caching for linked Discord accounts and pending verifications.
 * This reduces database lookups and improves performance.
 *
 * <p><b>Linked Players:</b> UUID &lt;-&gt; Discord ID
 * <br><b>Pending Verifications:</b> UUID -&gt; Verification Code</p>
 */
public class CacheProcessor {

    private static final Logger LOGGER = Logger.getLogger(CacheProcessor.class.getName());

    // Cache for linked players: UUID -> Discord ID
    private final Cache<UUID, String> linkedCache;

    // Reverse cache: Discord ID -> UUID
    private final Cache<String, UUID> reverseLinkedCache;

    // Cache for pending verifications: UUID -> verification code
    private final Cache<UUID, String> pendingVerificationCache;

    /**
     * Creates a CacheProcessor with configurable expiry times and max sizes.
     *
     * @param linkedExpiryMinutes  Time in minutes to keep linked player data cached.
     * @param pendingExpiryMinutes Time in minutes to keep pending verification data cached.
     * @param maxSize              Maximum cache size for each cache.
     */
    public CacheProcessor(long linkedExpiryMinutes, long pendingExpiryMinutes, long maxSize) {
        RemovalListener<Object, Object> removalListener = new RemovalListener<>() {
            @Override
            public void onRemoval(RemovalNotification<Object, Object> notification) {
                LOGGER.fine("Cache entry removed: " + notification.getKey() + " -> " + notification.getValue()
                        + " due to " + notification.getCause());
            }
        };

        this.linkedCache = CacheBuilder.newBuilder()
                .expireAfterWrite(linkedExpiryMinutes, TimeUnit.MINUTES)
                .maximumSize(maxSize)
                .removalListener(removalListener)
                .build();

        this.reverseLinkedCache = CacheBuilder.newBuilder()
                .expireAfterWrite(linkedExpiryMinutes, TimeUnit.MINUTES)
                .maximumSize(maxSize)
                .removalListener(removalListener)
                .build();

        this.pendingVerificationCache = CacheBuilder.newBuilder()
                .expireAfterWrite(pendingExpiryMinutes, TimeUnit.MINUTES)
                .maximumSize(maxSize)
                .removalListener(removalListener)
                .build();
    }

    /**
     * Default constructor with 10 minutes for linked data,
     * 5 minutes for pending verifications,
     * and maximum 1000 entries cache size.
     */
    public CacheProcessor() {
        this(10, 5, 1000);
    }

    /* ===================== LINKED PLAYERS ===================== */

    /**
     * Cache a linked player and their Discord ID.
     *
     * @param uuid      Minecraft player's UUID
     * @param discordId Discord ID as a String
     */
    public void cacheLinkedPlayer(UUID uuid, String discordId) {
        if (uuid == null || discordId == null) return;
        linkedCache.put(uuid, discordId);
        reverseLinkedCache.put(discordId, uuid);
    }

    /**
     * Get the cached Discord ID for a player.
     *
     * @param uuid Minecraft player's UUID
     * @return Cached Discord ID or null if none cached
     */
    public String getCachedDiscordId(UUID uuid) {
        if (uuid == null) return null;
        return linkedCache.getIfPresent(uuid);
    }

    /**
     * Get the cached UUID for a Discord user.
     *
     * @param discordId Discord user ID
     * @return Cached UUID or null if none cached
     */
    public UUID getCachedUuidByDiscordId(String discordId) {
        if (discordId == null) return null;
        return reverseLinkedCache.getIfPresent(discordId);
    }

    /**
     * Checks if a player is cached as linked.
     *
     * @param uuid Minecraft player's UUID
     * @return true if cached as linked, false otherwise
     */
    public boolean isPlayerLinked(UUID uuid) {
        if (uuid == null) return false;
        return linkedCache.getIfPresent(uuid) != null;
    }

    /**
     * Remove a linked player from the cache.
     *
     * @param uuid Minecraft player's UUID
     */
    public void invalidateLinkedPlayer(UUID uuid) {
        if (uuid == null) return;
        String discordId = linkedCache.getIfPresent(uuid);
        linkedCache.invalidate(uuid);
        if (discordId != null) {
            reverseLinkedCache.invalidate(discordId);
        }
    }

    /* ===================== PENDING VERIFICATIONS ===================== */

    /**
     * Cache a pending verification code for a player.
     *
     * @param uuid Minecraft player's UUID
     * @param code Verification code
     */
    public void cachePendingVerification(UUID uuid, String code) {
        if (uuid == null || code == null) return;
        pendingVerificationCache.put(uuid, code);
    }

    /**
     * Get the cached pending verification code for a player.
     *
     * @param uuid Minecraft player's UUID
     * @return Cached verification code or null if none cached
     */
    public String getCachedPendingCode(UUID uuid) {
        if (uuid == null) return null;
        return pendingVerificationCache.getIfPresent(uuid);
    }

    /**
     * Checks if a player has a pending verification code cached.
     *
     * @param uuid Minecraft player's UUID
     * @return true if a pending verification is cached, false otherwise
     */
    public boolean hasPendingVerification(UUID uuid) {
        if (uuid == null) return false;
        return pendingVerificationCache.getIfPresent(uuid) != null;
    }

    /**
     * Remove a pending verification entry from the cache.
     *
     * @param uuid Minecraft player's UUID
     */
    public void invalidatePendingVerification(UUID uuid) {
        if (uuid == null) return;
        pendingVerificationCache.invalidate(uuid);
    }

    /* ===================== UTILITY ===================== */

    /**
     * Clears all caches (linked players and pending verifications).
     */
    public synchronized void invalidateAll() {
        linkedCache.invalidateAll();
        reverseLinkedCache.invalidateAll();
        pendingVerificationCache.invalidateAll();
    }
}
