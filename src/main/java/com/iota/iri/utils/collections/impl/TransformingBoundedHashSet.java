package com.iota.iri.utils.collections.impl;

import java.util.Collection;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;


public class TransformingBoundedHashSet<E> extends BoundedHashSet<E>{

    private final UnaryOperator<E> transformer;

    public TransformingBoundedHashSet(int maxSize, UnaryOperator<E> transformer) {
        super(maxSize);
        this.transformer = transformer;
    }

    public TransformingBoundedHashSet(Collection<E> c, int maxSize, UnaryOperator<E> transformer) {
        super(maxSize);
        this.transformer = transformer;
        this.addAll(c);
    }

    @Override
    public boolean add(E e) {
        if (isFull()) {
            return false;
        }

        E el = transformer.apply(e);
        return super.add(el);
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        Collection<? extends E> col = c;
        if (!isFull()) {
            col = c.stream()
                    .map(el -> transformer.apply(el))
                    .collect(Collectors.toSet());
        }
        return super.addAll(col);
    }
}
