package com.iota.iri.cache;

/**
 * Cache configuration. This class represents the config values used to instantiate a {@link Cache}
 */
public class CacheConfiguration {

    public static final long DEFAULT_MAX_SIZE = 256;
    public static final int DEFAULT_EVICTION_COUNT = 10;
    public static final boolean DEFAULT_WEAK_REFERENCE = false;
    public static final CacheEvictionPolicy DEFAULT_EVICTION_POLICY = CacheEvictionPolicy.FIFO;
    public static final boolean DEFAULT_IS_NULL_ALLOWED = false;
    public static final int DEFAULT_TIME_TO_IDLE_SECONDS = 0;

    /**
     * Max size of the cache
     */
    private long maxSize = DEFAULT_MAX_SIZE;

    /**
     * The number of items to evict in a single batch of eviction.
     */
    private int evictionCount = DEFAULT_EVICTION_COUNT;

    /**
     * A flag to determine if the cache should use weak/soft reference to values or not
     */
    private boolean weakReference = DEFAULT_WEAK_REFERENCE;

    /**
     * The maximum number of seconds an element can exist in cache without it being accessed. Idle elements will not be
     * returned. The default value is 0, which means there's no TTL on this cache
     */
    private int timeToIdleSeconds = DEFAULT_TIME_TO_IDLE_SECONDS;

    /**
     * The {@link CacheEvictionPolicy} policy used.
     */
    private CacheEvictionPolicy evictionPolicy = DEFAULT_EVICTION_POLICY;

    /**
     * Determines if null values are allowed in the cache
     */
    private boolean isNullAllowed = DEFAULT_IS_NULL_ALLOWED;

    /**
     * Empty constructor
     */
    public CacheConfiguration() {
    }

    /**
     * Constructor for this cache manager
     * 
     * @param maxSize           Max size of the cache
     * @param evictionCount     The number of items to evict at a time
     * @param weakReference     If week references should be used
     * @param evictionPolicy    The eviction policy. See {@link CacheEvictionPolicy}
     * @param isNullAllowed     If null values are allowed to be cached
     * @param timeToIdleSeconds Maximum seconds before a cached items becomes idle
     */
    public CacheConfiguration(long maxSize, int evictionCount, boolean weakReference,
            CacheEvictionPolicy evictionPolicy, boolean isNullAllowed, int timeToIdleSeconds) {
        this.maxSize = maxSize;
        this.evictionCount = evictionCount;
        this.weakReference = weakReference;
        this.evictionPolicy = evictionPolicy;
        this.isNullAllowed = isNullAllowed;
        this.timeToIdleSeconds = timeToIdleSeconds;
    }

    public long getMaxSize() {
        return maxSize;
    }

    public void setMaxSize(long maxSize) {
        this.maxSize = maxSize;
    }

    public int getEvictionCount() {
        return evictionCount;
    }

    public void setEvictionCount(int evictionCount) {
        this.evictionCount = evictionCount;
    }

    public boolean isWeakReference() {
        return weakReference;
    }

    public void setWeakReference(boolean weakReference) {
        this.weakReference = weakReference;
    }

    public CacheEvictionPolicy getEvictionPolicy() {
        return evictionPolicy;
    }

    public void setEvictionPolicy(CacheEvictionPolicy evictionPolicy) {
        this.evictionPolicy = evictionPolicy;
    }

    public int getTimeToIdleSeconds() {
        return timeToIdleSeconds;
    }

    public void setTimeToIdleSeconds(int timeToIdleSeconds) {
        this.timeToIdleSeconds = timeToIdleSeconds;
    }

    public boolean isNullAllowed() {
        return isNullAllowed;
    }

    public void setNullAllowed(boolean nullAllowed) {
        isNullAllowed = nullAllowed;
    }

    /**
     * Validates the configuration
     * 
     * @throws InvalidCacheConfigurationException Exception to be thrown in case of invalid config
     */
    public void validateConfiguration() throws InvalidCacheConfigurationException {
        // Empty body
    }
}
