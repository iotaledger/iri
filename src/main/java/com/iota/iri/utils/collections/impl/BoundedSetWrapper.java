package com.iota.iri.utils.collections.impl;

import com.iota.iri.utils.collections.interfaces.BoundedSet;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;


/**
 * A wrapper for a set that allows up to {@code maxsize} elements.
 * Old elements are removed once it is full and new elements are added.
 * The way old elements are chosen depends on the underlying set implementation.
 *
 *
 * @param <E> the type parameter
 */
public class BoundedSetWrapper<E> implements BoundedSet<E>{
    private final int maxSize;
    private final Set<E> delegate;

    /**
     * Wraps the given set {@code c}
     *
     * @param c the set which you delegate the actions to
     * @param maxSize the max size
     * @throws NullPointerException if the {@code c} is null
     * @throws IllegalArgumentException if {@code c} is larger than {@code maxSize}
     */
    public BoundedSetWrapper(Set<E> c, int maxSize) {
        Objects.requireNonNull(c, "trying to wrap a null set");
        requireCollectionIsNotAboveMaxSize(c, maxSize);
        this.maxSize = maxSize;
        this.delegate = c;
    }

    @Override
    public int getMaxSize() {
        return maxSize;
    }

    @Override
    public int size() {
       return delegate.size();
    }

    @Override
    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        return delegate.contains(o);
    }

    @Override
    public Iterator<E> iterator() {
        return delegate.iterator();
    }

    @Override
    public void forEach(Consumer<? super E> action) {
        delegate.forEach(action);
    }

    @Override
    public Object[] toArray() {
        return delegate.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return delegate.toArray(a);
    }

    @Override
    public boolean add(E e) {
        if (isFull()) {
            Iterator<E> iterator = delegate.iterator();
            iterator.next();
            iterator.remove();
        }

        return delegate.add(e);
    }

    @Override
    public boolean remove(Object o) {
        return delegate.remove(o);
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return delegate.containsAll(c);
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        requireCollectionIsNotAboveMaxSize(c, getMaxSize());
        if (canCollectionBeFullyAdded(c)) {
            return delegate.addAll(c);
        }
        else {
            int itemsToDelete = delegate.size() + c.size() - getMaxSize();
            Iterator<E> iterator = delegate.iterator();
            for (int i = 0; i < itemsToDelete; i++) {
                iterator.next();
                iterator.remove();
            }
            return delegate.addAll(c);
        }
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return delegate.removeAll(c);
    }

    @Override
    public boolean removeIf(Predicate<? super E> filter) {
        return delegate.removeIf(filter);
    }

    @Override
    public boolean retainAll(Collection<?> c) {
       return delegate.retainAll(c);
    }

    @Override
    public void clear() {
        delegate.clear();
    }

    @Override
    public Spliterator<E> spliterator() {
        return delegate.spliterator();
    }

    @Override
    public Stream<E> stream() {
        return delegate.stream();
    }

    @Override
    public Stream<E> parallelStream() {
        return delegate.parallelStream();
    }

    @Override
    public boolean equals(Object obj) {
        return delegate.equals(obj);
    }

    @Override
    public int hashCode() {
        return delegate.hashCode();
    }

    @Override
    public String toString() {
        return delegate.toString();
    }

    private void requireCollectionIsNotAboveMaxSize(Collection<? extends E> c, int maxSize) {
        if (c.size() > maxSize) {
            throw new IllegalArgumentException(String.format("The given collection size %d is larger then maxSize %d", c.size(),
                    maxSize));
        }
    }
}
