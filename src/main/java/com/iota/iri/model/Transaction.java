package com.iota.iri.model;

import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.storage.Persistable;
import com.iota.iri.utils.Serializer;

import java.nio.ByteBuffer;

/**
 * Created by paul on 3/2/17 for iri.
 */
public class Transaction implements Persistable {
    public static final int SIZE = 1604;

    public byte[] bytes;

    public Hash address;
    public Hash bundle;
    public Hash trunk;
    public Hash branch;
    public Hash tag;
    public long value;
    public long currentIndex;
    public long lastIndex;
    public long timestamp;

    public int validity = 0;
    public int type = 1;
    public long arrivalTime = 0;

    //public boolean confirmed = false;
    public boolean solid = false;
    public long height = 0;
    public String sender = "";
    public long snapshot;

    public byte[] bytes() {
        return bytes;
    }

    public void read(byte[] bytes) {
        if(bytes != null) {
            this.bytes = new byte[SIZE];
            System.arraycopy(bytes, 0, this.bytes, 0, SIZE);
        }
    }

    @Override
    public byte[] metadata() {
        ByteBuffer buffer = ByteBuffer.allocate(Hash.SIZE_IN_BYTES * 5 + Long.BYTES * 6 + Integer.BYTES * 3 + 1 + sender.getBytes().length);
        buffer.put(address.bytes());
        buffer.put(bundle.bytes());
        buffer.put(trunk.bytes());
        buffer.put(branch.bytes());
        buffer.put(tag.bytes());
        buffer.put(Serializer.serialize(value));
        buffer.put(Serializer.serialize(currentIndex));
        buffer.put(Serializer.serialize(lastIndex));
        buffer.put(Serializer.serialize(timestamp));
        buffer.put(Serializer.serialize(validity));
        buffer.put(Serializer.serialize(type));
        buffer.put(Serializer.serialize(arrivalTime));
        buffer.put(Serializer.serialize(height));
        //buffer.put((byte) (confirmed ? 1:0));
        buffer.put(new byte[]{(byte) (solid ? 1 : 0)});
        buffer.put(Serializer.serialize(snapshot));
        buffer.put(sender.getBytes());
        return buffer.array();
    }

    @Override
    public void readMetadata(byte[] bytes) {
        int i = 0;
        if(bytes != null) {
            address = new Hash(bytes, i, Hash.SIZE_IN_BYTES);
            i += Hash.SIZE_IN_BYTES;
            bundle = new Hash(bytes, i, Hash.SIZE_IN_BYTES);
            i += Hash.SIZE_IN_BYTES;
            trunk = new Hash(bytes, i, Hash.SIZE_IN_BYTES);
            i += Hash.SIZE_IN_BYTES;
            branch = new Hash(bytes, i, Hash.SIZE_IN_BYTES);
            i += Hash.SIZE_IN_BYTES;
            tag = new Hash(bytes, i, Hash.SIZE_IN_BYTES);
            i += Hash.SIZE_IN_BYTES;
            value = Serializer.getLong(bytes, i);
            i += Long.BYTES;
            currentIndex = Serializer.getLong(bytes, i);
            i += Long.BYTES;
            lastIndex = Serializer.getLong(bytes, i);
            i += Long.BYTES;
            timestamp = Serializer.getLong(bytes, i);
            i += Long.BYTES;
            validity = Serializer.getInteger(bytes, i);
            i += Integer.BYTES;
            type = Serializer.getInteger(bytes, i);
            i += Integer.BYTES;
            arrivalTime = Serializer.getLong(bytes, i);
            i += Long.BYTES;
            height = Serializer.getLong(bytes, i);
            i += Long.BYTES;
            /*
            confirmed = bytes[i] == 1;
            i++;
            */
            solid = bytes[i] == 1;
            i++;
            snapshot = Serializer.getInteger(bytes, i);
            i += Integer.BYTES;
            byte[] senderBytes = new byte[bytes.length - i];
            if (senderBytes.length != 0) {
                System.arraycopy(bytes, i, senderBytes, 0, senderBytes.length);
            }
            sender = new String(senderBytes);
        }
    }

    @Override
    public boolean merge() {
        return false;
    }
}
