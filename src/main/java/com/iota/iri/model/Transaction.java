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
    public Hash obsoleteTag;
    public long value;
    public long currentIndex;
    public long lastIndex;
    public long timestamp;

    public Hash tag;
    public long attachmentTimestamp;
    public long attachmentTimestampLowerBound;
    public long attachmentTimestampUpperBound;

    public int validity = 0;
    public int type = 1;
    public long arrivalTime = 0;

    //public boolean confirmed = false;
    public boolean parsed = false;
    public boolean solid = false;
    public long height = 0;
    public String sender = "";
    public int snapshot;

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
        int allocateSize =
                Hash.SIZE_IN_BYTES * 6 + //address,bundle,trunk,branch,obsoleteTag,tag
                        Long.BYTES * 9 + //value,currentIndex,lastIndex,timestamp,attachmentTimestampLowerBound,attachmentTimestampUpperBound,arrivalTime,height
                        Integer.BYTES * 3 + //validity,type,snapshot
                        1 + //solid
                        sender.getBytes().length; //sender
        ByteBuffer buffer = ByteBuffer.allocate(allocateSize);
        buffer.put(address.bytes());
        buffer.put(bundle.bytes());
        buffer.put(trunk.bytes());
        buffer.put(branch.bytes());
        buffer.put(obsoleteTag.bytes());
        buffer.put(Serializer.serialize(value));
        buffer.put(Serializer.serialize(currentIndex));
        buffer.put(Serializer.serialize(lastIndex));
        buffer.put(Serializer.serialize(timestamp));

        buffer.put(tag.bytes());
        buffer.put(Serializer.serialize(attachmentTimestamp));
        buffer.put(Serializer.serialize(attachmentTimestampLowerBound));
        buffer.put(Serializer.serialize(attachmentTimestampUpperBound));

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
            obsoleteTag = new Hash(bytes, i, Hash.SIZE_IN_BYTES);
            i += Hash.SIZE_IN_BYTES;
            value = Serializer.getLong(bytes, i);
            i += Long.BYTES;
            currentIndex = Serializer.getLong(bytes, i);
            i += Long.BYTES;
            lastIndex = Serializer.getLong(bytes, i);
            i += Long.BYTES;
            timestamp = Serializer.getLong(bytes, i);
            i += Long.BYTES;

            tag = new Hash(bytes, i, Hash.SIZE_IN_BYTES);
            i += Hash.SIZE_IN_BYTES;
            attachmentTimestamp = Serializer.getLong(bytes, i);
            i += Long.BYTES;
            attachmentTimestampLowerBound = Serializer.getLong(bytes, i);
            i += Long.BYTES;
            attachmentTimestampUpperBound = Serializer.getLong(bytes, i);
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
            parsed = true;
        }
    }

    @Override
    public boolean merge() {
        return false;
    }
}
