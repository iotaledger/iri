package com.iota.iri.cache.util;

/**
 * Simple wrapper for cache values
 */
public class ValueWrapper {

    /**
     * Value
     */
    private final Object value;

    /**
     * Constructor
     * 
     * @param value The value to wrap
     */
    public ValueWrapper(Object value) {
        this.value = value;
    }

    /**
     * Get the value
     * 
     * @return The value
     */
    public Object get() {
        return this.value;
    }
}
