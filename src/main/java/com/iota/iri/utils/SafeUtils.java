package com.iota.iri.utils;

import java.util.Collection;


/**
 * Null safe utils
 */
public class SafeUtils {

    public static <T> boolean isContaining(Collection<T> collection, T element) {
        return collection != null && element != null && collection.contains(element);
    }
}
