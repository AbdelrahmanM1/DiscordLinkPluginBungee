package me.abdelrahmanmoharramdev.aquixLinkCoreBungee.cache;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Handles caching for linked Discord accounts and pending verifications.
 * This reduces database lookups and improves performance.
 * Linked Players: UUID <-> Discord ID
 * Pending Verifications: UUID -> Verification Code
 */
public class CacheProcessor {

    // Linked players: UUID -> Discord ID
    private final Cache<UUID, String> linkedCache;

    // Reverse mapping: Discord ID -> UUID
    private final Cache<String, UUID> reverseLinkedCache;

    // Pending verifications: UUID -> verification code
    private final Cache<UUID, String> pendingVerificationCache;

    /**
     * Creates a CacheProcessor with configurable expiry times.
     *
     * @param linkedExpiryMinutes  Time in minutes to keep linked player data cached.
     * @param pendingExpiryMinutes Time in minutes to keep pending verification data cached.
     */
    public CacheProcessor(long linkedExpiryMinutes, long pendingExpiryMinutes) {
        this.linkedCache = CacheBuilder.newBuilder()
                .expireAfterWrite(linkedExpiryMinutes, TimeUnit.MINUTES)
                .maximumSize(1000)
                .build();

        this.reverseLinkedCache = CacheBuilder.newBuilder()
                .expireAfterWrite(linkedExpiryMinutes, TimeUnit.MINUTES)
                .maximumSize(1000)
                .build();

        this.pendingVerificationCache = CacheBuilder.newBuilder()
                .expireAfterWrite(pendingExpiryMinutes, TimeUnit.MINUTES)
                .maximumSize(1000)
                .build();
    }

    /**
     * Default constructor with 10 min for linked data and 5 min for pending verifications.
     */
    public CacheProcessor() {
        this(10, 5);
    }

    /* ===================== LINKED PLAYERS ===================== */

    /**
     * Caches a linked player and their Discord ID.
     */
    public void cacheLinkedPlayer(UUID uuid, String discordId) {
        linkedCache.put(uuid, discordId);
        reverseLinkedCache.put(discordId, uuid);
    }

    /**
     * Gets the cached Discord ID for a player.
     */
    public String getCachedDiscordId(UUID uuid) {
        return linkedCache.getIfPresent(uuid);
    }

    /**
     * Gets the cached UUID for a Discord user.
     */
    public UUID getCachedUuidByDiscordId(String discordId) {
        return reverseLinkedCache.getIfPresent(discordId);
    }

    /**
     * Checks if a player is cached as linked.
     */
    public boolean isPlayerLinked(UUID uuid) {
        return linkedCache.getIfPresent(uuid) != null;
    }

    /**
     * Removes a linked player from the cache.
     */
    public void invalidateLinkedPlayer(UUID uuid) {
        String discordId = linkedCache.getIfPresent(uuid);
        linkedCache.invalidate(uuid);
        if (discordId != null) {
            reverseLinkedCache.invalidate(discordId);
        }
    }

    /* ===================== PENDING VERIFICATIONS ===================== */

    /**
     * Caches a pending verification code for a player.
     */
    public void cachePendingVerification(UUID uuid, String code) {
        pendingVerificationCache.put(uuid, code);
    }

    /**
     * Gets the cached pending verification code for a player.
     */
    public String getCachedPendingCode(UUID uuid) {
        return pendingVerificationCache.getIfPresent(uuid);
    }

    /**
     * Checks if a player has a pending verification code cached.
     */
    public boolean hasPendingVerification(UUID uuid) {
        return pendingVerificationCache.getIfPresent(uuid) != null;
    }

    /**
     * Removes a pending verification entry from the cache.
     */
    public void invalidatePendingVerification(UUID uuid) {
        pendingVerificationCache.invalidate(uuid);
    }

    /* ===================== UTILITY ===================== */

    /**
     * Clears all caches.
     */
    public synchronized void invalidateAll() {
        linkedCache.invalidateAll();
        reverseLinkedCache.invalidateAll();
        pendingVerificationCache.invalidateAll();
    }
}
