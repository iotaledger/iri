package com.iota.iri.utils;

import com.iota.iri.utils.funcinterfaces.RunnableThrows;

import java.io.Closeable;

public class Quiet {
    public static void run(RunnableThrows runnableThrows) {
        try {
            runnableThrows.run();
        } catch (Throwable t) {
            // IGNORE
        }
    }

    public static void close(Closeable closeable) {
        try {
            if (closeable != null) closeable.close();
        } catch (Throwable t) {
            // IGNORE
        }
    }
}
