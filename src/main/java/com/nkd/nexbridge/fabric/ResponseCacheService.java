package com.nkd.nexbridge.fabric;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class ResponseCacheService {

    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    private record CacheEntry(Map<String, Object> data, Instant expiresAt) {
        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    public Optional<Map<String, Object>> get(String key) {
        CacheEntry entry = cache.get(key);
        if (entry == null || entry.isExpired()) {
            cache.remove(key);
            return Optional.empty();
        }
        return Optional.of(entry.data());
    }

    public void put(String key, Map<String, Object> data, int ttlSec) {
        if (ttlSec <= 0) return;
        cache.put(key, new CacheEntry(data, Instant.now().plusSeconds(ttlSec)));
        log.debug("ResponseCache: cached key={} ttl={}s", key, ttlSec);
    }

    public String buildKey(String path, String method, Map<String, Object> params) {
        return path + ":" + method + ":" + new TreeMap<>(params);
    }

    public void evict(String pathPrefix) {
        cache.entrySet().removeIf(e -> e.getKey().startsWith(pathPrefix));
    }

    public int size() {
        return cache.size();
    }
}
