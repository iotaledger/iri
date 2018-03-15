package com.iota.iri.utils.funcinterfaces;

import java.util.Objects;

@FunctionalInterface
public interface BiPredicateThrows<T, U> {

    boolean test(T t, U u) throws Exception;

    default BiPredicateThrows<T, U> and(BiPredicateThrows<? super T, ? super U> other) throws Exception {
        Objects.requireNonNull(other);
        return (T t, U u) -> test(t, u) && other.test(t, u);
    }

    default BiPredicateThrows<T, U> negate() throws Exception {
        return (T t, U u) -> !test(t, u);
    }

    default BiPredicateThrows<T, U> or(BiPredicateThrows<? super T, ? super U> other) throws Exception {
        Objects.requireNonNull(other);
        return (T t, U u) -> test(t, u) || other.test(t, u);
    }
}
