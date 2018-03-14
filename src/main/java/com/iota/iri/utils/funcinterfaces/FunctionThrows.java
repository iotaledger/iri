package com.iota.iri.utils.funcinterfaces;

import java.util.Objects;

@FunctionalInterface
public interface FunctionThrows<T, R> {

    R apply(T t) throws Exception;

    default <V> FunctionThrows<V, R> compose(FunctionThrows<? super V, ? extends T> before) {
        Objects.requireNonNull(before);
        return (V v) -> apply(before.apply(v));
    }

    default <V> FunctionThrows<T, V> andThen(FunctionThrows<? super R, ? extends V> after) {
        Objects.requireNonNull(after);
        return (T t) -> after.apply(apply(t));
    }

    static <T> FunctionThrows<T, T> identity() {
        return t -> t;
    }
}
