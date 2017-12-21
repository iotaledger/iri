package com.iota.iri.model;

import com.iota.iri.storage.Persistable;
import org.apache.commons.lang3.ArrayUtils;

import javax.naming.OperationNotSupportedException;
import java.util.*;

/**
 * Created by paul on 3/8/17 for iri.
 */
public class Hashes implements Persistable {
    public Set<Hash> set = new LinkedHashSet<>();
    public static final byte DELIMITER = ",".getBytes()[0];

    public byte[] bytes() {
        //I assume this is the maximal size of the array
        int setSize = set.size();
        int numOfBytes = setSize == 0 ? 0 : (Hash.SIZE_IN_BYTES + 1) * setSize - 1;
        byte[] bytes = new byte[numOfBytes];

        int i = 0;
        for (Hash hash : set) {
            byte[] hashBytes = hash.bytes();
            System.arraycopy(hashBytes, 0, bytes, i * (hashBytes.length + 1), hashBytes.length);
            int delimiterIndex = ++i*(hashBytes.length + 1) - 1;
            //do not include last delimiter
            if (delimiterIndex < bytes.length){
                bytes[delimiterIndex] = DELIMITER;
            }
        }
        return bytes;
    }

    public void read(byte[] bytes) {
        if (bytes != null) {
            // read will do an append operation
            if (set == null) {
                set = new LinkedHashSet<>(bytes.length / (1 + Hash.SIZE_IN_BYTES) + 1);
            }
            for (int i = 0; i < bytes.length; i += 1 + Hash.SIZE_IN_BYTES) {
                set.add(new Hash(bytes, i, Hash.SIZE_IN_BYTES));
            }
        }
    }

    @Override
    public byte[] metadata() {
        return new byte[0];
    }

    @Override
    public void readMetadata(byte[] bytes) {

    }

    @Override
    public boolean merge() {
        return true;
    }

    @Override
    public boolean isSplittable() {
        return true;
    }

    @Override
    public List<byte[]> splitBytes(int maxByteLength) throws OperationNotSupportedException {
        //TODO if average hashes size is known you can precalculate List size and decrease memory reallocations
        List<byte[]> serializedBytes = new ArrayList<>();
        byte[] bytes = new byte[maxByteLength];
        int currPos = 0;
        for (Hash hash : set) {
            byte[] hashBytes = hash.bytes();
            if (currPos + hashBytes.length <= maxByteLength) {
                currPos = addHashToBytesArray(bytes, currPos, hashBytes);
            } else {
                serializedBytes = addBytesToList(serializedBytes, bytes, currPos);
                //reinitialize
                bytes = new byte[maxByteLength];
                currPos = addHashToBytesArray(bytes, 0, hashBytes);
            }
        }
        //--currPos to delete delimiter
        serializedBytes = addBytesToList(serializedBytes, bytes, --currPos);
        return serializedBytes;
    }

    private List<byte[]> addBytesToList(List<byte[]> serializedBytes, byte[] bytes, int currPos) {
        //truncate array
        bytes = ArrayUtils.subarray(bytes,0, currPos);
        //add to list
        serializedBytes.add(bytes);

        return serializedBytes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Hashes)) {
            return false;
        }
        Hashes hashes = (Hashes) o;
        return Objects.equals(set, hashes.set);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("Hashes{");
        sb.append("set=").append(set);
        sb.append('}');
        return sb.toString();
    }

    @Override
    public int hashCode() {
        return Objects.hash(set);
    }

    private int addHashToBytesArray(byte[] bytes, int currPos, byte[] hashBytes) {
        System.arraycopy(hashBytes, 0, bytes, currPos, hashBytes.length);
        currPos += hashBytes.length;
        bytes[currPos] = DELIMITER;
        return ++currPos;
    }
}