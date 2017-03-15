package com.iota.iri.service.tangle;

import java.io.*;
import java.nio.ByteBuffer;

/**
 * Created by paul on 3/13/17 for iri-testnet.
 */
public class Serializer {
    public static byte[] serialize(Object obj) throws IOException {
        if (obj instanceof byte[]) {
            return (byte[]) obj;
        } else if(obj instanceof Byte) {
            return new byte[]{(byte)obj};
        } else if (obj == null) {
            return null;
        } else if (obj instanceof Long) {
            ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
            buffer.putLong((long)obj);
            return buffer.array();
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
        } else if (target == byte.class) {
            return bytes[0];
        } else if (target == long.class || target == Long.class) {
            ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
            buffer.put(bytes);
            buffer.flip();
            return buffer.getLong();
        }
        try (ByteArrayInputStream b = new ByteArrayInputStream(bytes)) {
            try (ObjectInputStream o = new ObjectInputStream(b)) {
                return o.readObject();
            }
        }
    }
}
