package org.visallo.core.cache;

public interface CacheService {
    <T> T put(String cacheName, String key, T t, CacheOptions cacheOptions);

    <T> T getIfPresent(String cacheName, String key);

    void invalidate(String cacheName);

    void invalidate(String cacheName, String key);
}
