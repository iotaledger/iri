package com.iota.iri.model;

public class TestModelUtils {

    public static Address createAddresses(int numOfBytes) {
        Address hashes = new Address();
        byte[] bytes = generateBytes(numOfBytes);
        hashes.read(bytes);
        return  hashes;
    }

    private static byte[] generateBytes(int numOfBytes) {
        byte[] bytes = new byte[numOfBytes];
        for (int i = 0; i < bytes.length; i++) {
            if (i != 0 && i % (Hash.SIZE_IN_BYTES + i) == 0) {
                bytes[i] = Hashes.DELIMITER;
            }
            else {
                bytes[i] = (byte) (i % 127);
            }
        }
        return bytes;
    }

}
