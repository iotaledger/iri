package com.iota.iri.utils;


import java.io.*;
import java.nio.file.Paths;
import java.util.Objects;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class that helps access IO
 */
public class IotaIOUtils extends IOUtils {

    private static final Logger log = LoggerFactory.getLogger(IotaIOUtils.class);

    /**
     * Looks up a file in the current working directory. If not available checks to see it is available
     * as a resource bundled in the Jar.
     *
     * @param path the relative path of the file\resource we are looking up. Should be prefixed with {@code \}.
     * @return A stream reader of the file\resource
     * @throws IOException if no file was loaded
     */
    public static InputStreamReader getFileStreamFromCwdOrResource(String path) throws IOException {
        String cwdPath = Paths.get("").toAbsolutePath().toString();
        File file = new File(cwdPath + path);
        try {
            if (file.exists()) {
                log.info(path + " has been found in the current working directory");
                return new FileReader(file);
            }
            else {
                InputStream resourceAsStream = IotaIOUtils.class.getResourceAsStream(path);
                Objects.requireNonNull(resourceAsStream, path + " resource is missing");
                log.info(path + " has been found as a jar resource");
                return new InputStreamReader(resourceAsStream);
            }
        } catch (NullPointerException e) {
            throw new IOException("Can't load resource " + path, e);
        }
    }

    public static void closeQuietly(AutoCloseable... autoCloseables) {
        for (AutoCloseable it : autoCloseables) {
            try {
                if (it != null) {
                    it.close();
                }
            } catch (Exception ignored) {
                log.debug("Silent exception occured", ignored);
            }
        }
    }
}
