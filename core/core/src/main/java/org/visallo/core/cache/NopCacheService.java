package org.visallo.core.cache;

public class NopCacheService implements CacheService{
    @Override
    public <T> T put(String cacheName, String key, T t, CacheOptions cacheOptions) {
        return t;
    }

    @Override
    public <T> T getIfPresent(String cacheName, String key) {
        return null;
    }

    @Override
    public void invalidate(String cacheName) {

    }

    @Override
    public void invalidate(String cacheName, String key) {

    }
}
