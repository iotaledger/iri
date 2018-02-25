package com.iota.iri.utils.collections.impl;

import com.iota.iri.utils.collections.interfaces.BoundedSet;

import java.util.Collection;
import java.util.HashSet;
import java.util.stream.Collectors;


/**
 * A set that doesn't allow to add elements to it once it is full
 *
 * @param <E> the type parameter
 */
public class BoundedHashSet<E> extends HashSet<E> implements BoundedSet<E>{
    final private int maxSize;

    /**
     * Instantiates a new Bounded hash set.
     *
     * @param initialCapacity the initial capacity
     * @param loadFactor      the load factor of the hashmap
     * @param maxSize         the max size
     */
    public BoundedHashSet(int initialCapacity, float loadFactor, int maxSize) {
        super(initialCapacity, loadFactor);
        this.maxSize = maxSize;
    }

    /**
     * Instantiates a new Bounded hash set.
     *
     * @param initialCapacity the initial capacity
     * @param maxSize         the max size
     */
    public BoundedHashSet(int initialCapacity, int maxSize) {
        super(initialCapacity);
        this.maxSize = maxSize;
    }

    /**
     * Instantiates a new Bounded hash set.
     *
     * @param maxSize the max size
     */
    public BoundedHashSet(int maxSize) {
        super();
        this.maxSize = maxSize;
    }

    /**
     * Instantiates a new Bounded hash set.
     *
     * @param c       the collection from which you create the set from
     * @param maxSize the max size
     * @throws NullPointerException if the specified collection is null
     */
    public BoundedHashSet(Collection<? extends E> c, int maxSize) {
        this(maxSize);
        c = c.stream()
                .limit(maxSize)
                .collect(Collectors.toSet());
        this.addAll(c);
    }

    @Override
    public int getMaxSize() {
        return maxSize;
    }

    @Override
    public boolean add(E e) {
        if (isFull()) {
            return false;
        }

        return super.add(e);
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        if (isFull()) {
            return false;
        }

        if (!canCollectionBeFullyAdded(c)) {
            int remainingSize = getMaxSize() - this.size();
            c = c.stream()
                    .limit(remainingSize)
                    .collect(Collectors.toSet());
        }
        return super.addAll(c);
    }
}
