package com.iota.iri.utils;


import org.apache.commons.io.IOUtils;

public class IotaIOUtils extends IOUtils {

    public static void closeQuietly(AutoCloseable... autoCloseables) {
        for (AutoCloseable it : autoCloseables) {
            try {
                it.close();
            } catch (Exception ignored) {
                //ignored
            }
        }
    }
}
