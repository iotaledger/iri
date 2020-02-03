package com.iota.iri.utils;

import com.iota.iri.IRI;
import com.iota.iri.model.Hash;

import java.io.FileReader;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IotaUtils {
    
    private final static long MB_FACTOR = 1000 * 1000;
    private final static long MIB_FACTOR = 1024 * 1024;
    private final static long GB_FACTOR = 1000 * MB_FACTOR;
    private final static long GIB_FACTOR = 1024 * MIB_FACTOR;
    private final static long TB_FACTOR = 1000 * GB_FACTOR;
    private final static long TIB_FACTOR = 1024 * GIB_FACTOR;
    
    private static final Logger log = LoggerFactory.getLogger(IotaUtils.class);

    /**
     * Returns the current version IRI is running by reading the Jar manifest.
     * If we run not from a jar or the manifest is missing we read straight from the pom
     *
     * @return the implementation version of IRI
     */
    public static String getIriVersion() {
        String implementationVersion = IRI.class.getPackage().getImplementationVersion();
        //If not in manifest (can happen when running from IDE)
        if (implementationVersion == null) {
            MavenXpp3Reader reader = new MavenXpp3Reader();
            try {
                Model model = reader.read(new FileReader("pom.xml"));
                implementationVersion = model.getVersion();
            } catch (Exception e) {
                log.error("Failed to parse version from pom", e);
            }
        }
        return implementationVersion;
    }

    public static List<String> splitStringToImmutableList(String string, String regexSplit) {
        return Arrays.stream(string.split(regexSplit))
                .filter(StringUtils::isNoneBlank)
                .collect(Collectors
                        .collectingAndThen(Collectors.toList(), Collections::unmodifiableList));
    }

    /**
     * Used to create low-memory index keys.
     *
     * @param hash the hash we create the key from
     * @param length the length of the desired subhash
     * @return a {@link ByteBuffer} that holds a subarray of {@link Hash#bytes()}
     * that has the specified {@code length}
     */
    public static ByteBuffer getSubHash(Hash hash, int length) {
        if (hash == null) {
            return null;
        }

        return ByteBuffer.wrap(Arrays.copyOf(hash.bytes(), length));
    }

    /**
     * @param clazz Class to inspect
     * @return All the declared and inherited setter method of {@code clazz}
     */
    public static List<Method> getAllSetters(Class<?> clazz) {
        List<Method> setters = new ArrayList<>();
        while (clazz != Object.class) {
            setters.addAll(Stream.of(clazz.getDeclaredMethods())
                    .filter(method -> method.getName().startsWith("set"))
                    .collect(Collectors.toList()));
            clazz = clazz.getSuperclass();
        }
        return Collections.unmodifiableList(setters);
    }

	public static <T> List<T> createImmutableList(T... values) {
		return Collections.unmodifiableList(Arrays.asList(values));
	}

    /**
     * Creates a single thread executor service that has an unbounded queue.

     * @param name the name to give to the thread
     * @return a named single thread executor service
     *
     * @see com.iota.iri.utils.thread.BoundedScheduledExecutorService
     * @see com.iota.iri.utils.thread.DedicatedScheduledExecutorService
     */
	public static ExecutorService createNamedSingleThreadExecutor(String name) {
        return Executors.newSingleThreadExecutor(r -> new Thread(r, name));

    }
	
	/**
     * Parses a human readable string for a file size (tb, gb, mb -> file modifier)
     * Follows the following format: [double][optional space][case insensitive file modifier]
     * 
     * Kb is not parsed as this is too small for us to be used in a persistence provider
     * 
     * @return The size of the human readable string in bytes, rounded down.
     */
    public static long parseFileSize(String humanReadableSize) {
        humanReadableSize = humanReadableSize.replaceAll(",", "").toLowerCase();
        int spaceNdx = humanReadableSize.indexOf(" ");
        double amount;
        //If we forgot a space,check until we find it
        if (spaceNdx == -1) {
            spaceNdx = 0;
            while (spaceNdx < humanReadableSize.length() && 
                    (Character.isDigit(humanReadableSize.charAt(spaceNdx)) || humanReadableSize.charAt(spaceNdx) == '.' )) {
                spaceNdx++;
            }

            // Still nothing? started with a character
            if (spaceNdx == 0) {
                return -1;
            }
            amount = Double.parseDouble(humanReadableSize.substring(0, spaceNdx));
        } else {
            // ++ to skip the space
            amount = Double.parseDouble(humanReadableSize.substring(0, spaceNdx++));
        }

        //Default to GB
        String sub = amount == humanReadableSize.length() ? "GB" : humanReadableSize.substring(spaceNdx);
        switch (sub) {
            case "tb":
                return (long) (amount * TB_FACTOR);
            case "tib":
                return (long) (amount * TIB_FACTOR);
            case "gb":
                return (long) (amount * GB_FACTOR);
            case "gib":
                return (long) (amount * GIB_FACTOR);
            case "mb":
                return (long) (amount * MB_FACTOR);
            case "mib":
                return (long) (amount * MIB_FACTOR);
            default: 
                return -1;
        }
    }
    
    /**
     * @see FileUtils#byteCountToDisplaySize(Long)
     */
    public static String bytesToReadableFilesize(long bytes) {
        return FileUtils.byteCountToDisplaySize(bytes);
    }
}
