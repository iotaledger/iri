package com.iota.iri.service.tipselection;

import com.iota.iri.storage.Indexable;

import java.util.Collection;


/**
 * Represents a mapping between the transaction to its tip selection rating.
 *
 * @param <T>
 */
public interface RatingMap<T extends Indexable> {
    int size();

    boolean isEmpty();

    boolean containsKey(T key);

    boolean containsValue(T value);

    Long get(T key);

    Long put(T key, Long value);

    Long remove(T key);

    void clear();

    Collection<Long> values();
}
