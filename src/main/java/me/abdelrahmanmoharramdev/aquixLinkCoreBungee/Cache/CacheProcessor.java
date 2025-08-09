package me.abdelrahmanmoharramdev.aquixLinkCoreBungee.cache;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class CacheProcessor {

    // Cache for linked players: UUID -> DiscordID (10 minutes expiry)
    private final Cache<UUID, String> linkedCache = CacheBuilder.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .maximumSize(1000)
            .build();

    // Cache for pending verifications: UUID -> code (5 minutes expiry)
    private final Cache<UUID, String> pendingVerificationCache = CacheBuilder.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .maximumSize(1000)
            .build();

    // Linked player cache methods
    public void cacheLinkedPlayer(UUID uuid, String discordId) {
        linkedCache.put(uuid, discordId);
    }

    public String getCachedDiscordId(UUID uuid) {
        return linkedCache.getIfPresent(uuid);
    }

    public void invalidateLinkedPlayer(UUID uuid) {
        linkedCache.invalidate(uuid);
    }

    // Pending verification cache methods
    public void cachePendingVerification(UUID uuid, String code) {
        pendingVerificationCache.put(uuid, code);
    }

    public String getCachedPendingCode(UUID uuid) {
        return pendingVerificationCache.getIfPresent(uuid);
    }

    public void invalidatePendingVerification(UUID uuid) {
        pendingVerificationCache.invalidate(uuid);
    }

    // Clear all caches
    public void invalidateAll() {
        linkedCache.invalidateAll();
        pendingVerificationCache.invalidateAll();
    }
}
