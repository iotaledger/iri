package com.iota.iri.utils.collections.impl;

import com.iota.iri.utils.SafeUtils;
import com.iota.iri.utils.collections.impl.BoundedHashSet;

import java.util.Collection;
import java.util.Collections;
import java.util.function.Function;
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
        if (!isFull()) {
            e = transformer.apply(e);
        }
        return super.add(e);
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        if (!isFull()) {
            c = c.stream()
                    .map(el -> transformer.apply(el))
                    .collect(Collectors.toSet());
        }
        return super.addAll(c);
    }
}
