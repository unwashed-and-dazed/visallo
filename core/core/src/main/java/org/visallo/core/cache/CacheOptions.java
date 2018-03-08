package org.visallo.core.cache;

public class CacheOptions {
    private Long maximumSize;

    public Long getMaximumSize() {
        return maximumSize;
    }

    public CacheOptions setMaximumSize(Long maximumSize) {
        this.maximumSize = maximumSize;
        return this;
    }
}
