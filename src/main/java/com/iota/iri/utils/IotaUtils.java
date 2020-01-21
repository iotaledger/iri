package com.iota.iri.utils;

import org.apache.commons.lang3.StringUtils;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

public class IotaUtils {

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
}
