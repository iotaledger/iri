package com.iota.iri.service.tangle;

import java.io.*;

/**
 * Created by paul on 3/13/17 for iri-testnet.
 */
public class Serializer {
    public static byte[] serialize(Object obj) throws IOException {
        if (obj instanceof byte[]) {
            return (byte[]) obj;
        } else if (obj == null) {
            return null;
        }
        try (ByteArrayOutputStream b = new ByteArrayOutputStream()) {
            try (ObjectOutputStream o = new ObjectOutputStream(b)) {
                o.writeObject(obj);
            }
            return b.toByteArray();
        }
    }

    public static Object deserialize(byte[] bytes, Class target) throws IOException, ClassNotFoundException {
        if (target == byte[].class) {
            return bytes;
        }
        try (ByteArrayInputStream b = new ByteArrayInputStream(bytes)) {
            try (ObjectInputStream o = new ObjectInputStream(b)) {
                return o.readObject();
            }
        }
    }
}
