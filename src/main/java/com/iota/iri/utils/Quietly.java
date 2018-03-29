package com.iota.iri.utils;

import java.io.Closeable;
import java.util.function.Consumer;

public class Quietly {

    /**
     * To close many closeables in order even if one fails.
     *
     * @param closeables any number of closeables that must be closed.
     */
    public static void closeAll(Closeable... closeables) {
        for (Closeable closeable : closeables) {
            close(closeable);
        }
    }

    /**
     * This will quietly close resources and swallow the close exception (of any Throwable type).
     * It is safe to pass this a null closeable.
     *
     * @param closeable the closeable (nullable) that needs to be closed if it is non-null
     */
    public static void close(Closeable closeable) {
        close(closeable, null);
    }

    /**
     * Useful for logging or otherwise making note of the close exception. If the consumer throws
     * or generates an exception - it will be completely ignored
     *
     * @param closeable the closeable (nullable) that needs to be closed if it is non-null
     * @param consumer  If the consumer is not null, it this will be passed the close exception
     *                  to log or do something else with it.
     */
    public static void close(Closeable closeable, Consumer<Throwable> consumer) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Throwable closeException) {
            if (consumer != null) {
                try {
                    consumer.accept(closeException);
                } catch (Throwable ignored) {
                }
            }
        }
    }
}
