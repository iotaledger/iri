package com.iota.iri.utils;

import org.apache.commons.lang3.StringUtils;

import com.iota.iri.model.Hash;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.iota.iri.model.Hash;
import com.iota.iri.model.HashFactory;
import java.util.Random;

public class IotaUtils {

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
    
    public static Hash getRandomTransactionHash() {
        Random seed = new Random();
        byte[] out = new byte[Hash.SIZE_IN_TRITS];

        for(int i = 0; i < out.length; i++) {
            out[i] = (byte) (seed.nextInt(3) - 1);
        }

        return HashFactory.TRANSACTION.create(out);
    }

    public static String abbrieviateHash(Hash h, int len) {
        String full = Converter.trytes(h.trits());
        return full.substring(0, len);
    }

    public static String abbrieviateHash(String h, int len) {
        String idx = h.split(":")[1];
        return h.substring(0, len) + "." + idx;
    }

}
