package com.iota.iri.utils.collections.interfaces;

import java.util.Collection;

/**
 * A collection that can't hold more than {@link #getMaxSize()} elements
 *
 * @author galrogo on 08/02/18
 **/
public interface BoundedCollection<E> extends Collection<E> {

    /**
     *
     * @return the maximal number of elements that the collection cha hold
     */
    int getMaxSize();

    /**
     * @return true if no more elements can be added
     */
    default boolean isFull() {
        return getMaxSize() <= this.size();
    }

    /**
     *
     * @param c collection to be added
     * @return true only if all the elements in {@code c} can be added to this collection
     * else return false
     */
    default boolean canCollectionBeFullyAdded(Collection<? extends E> c) {
        if (isFull()) {
            return false;
        }

        int remainingSize = getMaxSize() - this.size();
        return (c.size() <= remainingSize);
    }
}
