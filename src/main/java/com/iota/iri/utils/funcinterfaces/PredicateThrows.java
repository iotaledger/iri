package com.iota.iri.utils.funcinterfaces;

import java.util.Objects;

@FunctionalInterface
public interface PredicateThrows<T> {
    boolean test(T t) throws Exception;

    default PredicateThrows<T> and(PredicateThrows<? super T> other) {
        Objects.requireNonNull(other);
        return (t) -> test(t) && other.test(t);
    }

    default PredicateThrows<T> negate() {
        return (t) -> !test(t);
    }

    default PredicateThrows<T> or(PredicateThrows<? super T> other) {
        Objects.requireNonNull(other);
        return (t) -> test(t) || other.test(t);
    }


    static <T> PredicateThrows<T> isEqual(Object targetRef) {
        return (null == targetRef)
                ? Objects::isNull
                : object -> targetRef.equals(object);
    }
}