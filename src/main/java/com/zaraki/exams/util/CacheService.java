package com.zaraki.exams.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CacheService {

    private static final Map<String, CacheEntry<?>> cache = new ConcurrentHashMap<>();
    private static final int DEFAULT_TTL_SECONDS = 300;

    private CacheService() {}

    public static <T> T get(String key) {
        CacheEntry<?> entry = cache.get(key);
        if (entry == null) return null;
        if (System.currentTimeMillis() - entry.createdAt > entry.ttl * 1000L) {
            cache.remove(key);
            return null;
        }
        return (T) entry.value;
    }

    public static <T> void put(String key, T value) {
        put(key, value, DEFAULT_TTL_SECONDS);
    }

    public static <T> void put(String key, T value, int ttlSeconds) {
        cache.put(key, new CacheEntry<>(value, System.currentTimeMillis(), ttlSeconds));
    }

    public static void remove(String key) {
        cache.remove(key);
    }

    public static void clear() {
        cache.clear();
    }

    public static int size() {
        return cache.size();
    }

    private record CacheEntry<T>(T value, long createdAt, int ttl) {}
}
