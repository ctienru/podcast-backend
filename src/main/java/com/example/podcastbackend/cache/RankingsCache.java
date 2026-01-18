package com.example.podcastbackend.cache;

import com.example.podcastbackend.response.RankingsItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RankingsCache {

    private static final Logger log = LoggerFactory.getLogger(RankingsCache.class);

    private final long cacheTtlSeconds;

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public RankingsCache(@Value("${rankings.cache.ttl-seconds:3600}") long cacheTtlSeconds) {
        this.cacheTtlSeconds = cacheTtlSeconds;
        log.info("RankingsCache initialized with TTL: {} seconds", cacheTtlSeconds);
    }

    public record CacheEntry(
            List<RankingsItem> items,
            Instant cachedAt,
            long ttlSeconds
    ) {
        public boolean isExpired() {
            return Instant.now().isAfter(cachedAt.plusSeconds(ttlSeconds));
        }
    }

    /**
     * Build cache key from country and type
     */
    private String buildKey(String country, String type) {
        return country + ":" + type;
    }

    /**
     * Get cached rankings if available and not expired
     */
    public List<RankingsItem> get(String country, String type) {
        String key = buildKey(country, type);
        CacheEntry entry = cache.get(key);

        if (entry == null) {
            log.debug("Cache miss for key: {}", key);
            return null;
        }

        if (entry.isExpired()) {
            log.debug("Cache expired for key: {}", key);
            cache.remove(key);
            return null;
        }

        log.debug("Cache hit for key: {}", key);
        return entry.items();
    }

    /**
     * Store rankings in cache
     */
    public void put(String country, String type, List<RankingsItem> items) {
        String key = buildKey(country, type);
        cache.put(key, new CacheEntry(items, Instant.now(), cacheTtlSeconds));
        log.info("Cached {} items for key: {}", items.size(), key);
    }

    /**
     * Get stale cache entry (even if expired) - used as fallback when API fails
     */
    public List<RankingsItem> getStale(String country, String type) {
        String key = buildKey(country, type);
        CacheEntry entry = cache.get(key);
        return entry != null ? entry.items() : null;
    }

    /**
     * Get the cached timestamp for a given country and type
     */
    public Instant getCachedAt(String country, String type) {
        String key = buildKey(country, type);
        CacheEntry entry = cache.get(key);
        return entry != null ? entry.cachedAt() : null;
    }

    /**
     * Clear all cache entries
     */
    public void clear() {
        cache.clear();
        log.info("Rankings cache cleared");
    }

    /**
     * Get cache statistics
     */
    public Map<String, Object> getStats() {
        return Map.of(
                "size", cache.size(),
                "keys", cache.keySet()
        );
    }
}
