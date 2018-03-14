package com.iota.iri.utils.funcinterfaces;


import java.util.Objects;

@FunctionalInterface
public interface BiFunctionThrows<T, U, R> {

    R apply(T t, U u) throws Exception;

    default <V> BiFunctionThrows<T, U, V> andThen(FunctionThrows<? super R, ? extends V> after) {
        Objects.requireNonNull(after);
        return (T t, U u) -> after.apply(apply(t, u));
    }
}
