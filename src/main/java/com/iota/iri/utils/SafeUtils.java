package com.iota.iri.utils;

import java.util.Collection;
import java.util.Collections;
import java.util.stream.Stream;


/**
 * Null safe utils
 */
public class SafeUtils {

    public static <T> boolean isContaining(Collection<T> collection, T element) {
        return collection != null && element != null && collection.contains(element);
    }

    public static <T> Stream<T> stream(Collection<T> collection) {
        return collection == null ? Collections.EMPTY_LIST.stream() : collection.stream();
    }
}
