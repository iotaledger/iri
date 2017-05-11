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
    public int validity = 0;
    public int type = 1;
    public long arrivalTime = 0;

    public boolean solid = false;
    public boolean confirmed = false;
    public long height = 0;
    public String sender = "";

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
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES * 2 + Long.BYTES * 2 + 2 + sender.getBytes().length);
        buffer.put(Serializer.serialize(validity));
        buffer.put(Serializer.serialize(type));
        buffer.put(Serializer.serialize(arrivalTime));
        buffer.put(Serializer.serialize(height));
        buffer.put(new byte[]{(byte) (solid ? 1 : 0)});
        buffer.put((byte) (confirmed ? 1:0));
        buffer.put(sender.getBytes());
        return buffer.array();
    }

    @Override
    public void readMetadata(byte[] bytes) {
        int i = 0;
        if(bytes != null) {
            validity = Serializer.getInteger(bytes, i);
            i += Integer.BYTES;
            type = Serializer.getInteger(bytes, i);
            i += Integer.BYTES;
            arrivalTime = Serializer.getLong(bytes, i);
            i += Long.BYTES;
            height = Serializer.getLong(bytes, i);
            i += Long.BYTES;
            solid = bytes[i] == 1;
            i++;
            confirmed = bytes[i] == 1;
            i++;
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
