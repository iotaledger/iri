package com.iota.iri.model.persistables;

import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.model.HashFactory;
import com.iota.iri.storage.Persistable;
import com.iota.iri.utils.Serializer;
import com.iota.iri.utils.TransactionTruncator;

import javax.naming.OperationNotSupportedException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;


/**
 * This is a collection of {@link com.iota.iri.model.Hash} identifiers indexed by a
 * {@link com.iota.iri.model.TransactionHash} in a database. This acts as the access
 * point for all other persistable set collections representing the contents of a
 * <tt>Transaction</tt>.
 *
 * <p>
 *     A Transaction set contains all the information of a particular transaction. This includes
 *     hash objects for the <tt>address</tt>, <tt>bundle</tt>, <tt>trunk</tt>, <tt>branch</tt>,
 *     and <tt>obsolete tag</tt>, as well as data such as the <tt>value</tt> of the
 *     transaction as well as the <tt>timestamps</tt> and more.
 * </p>
 */
public class Transaction implements Persistable {
    public static final int SIZE = 1604;

    /**
     * Bitmask used to access and store the solid flag.
     */
    public static final int IS_SOLID_BITMASK = 0b01;

    /**
     * Bitmask used to access and store the milestone flag.
     */
    public static final int IS_MILESTONE_BITMASK = 0b10;

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
    public AtomicInteger type = new AtomicInteger(TransactionViewModel.PREFILLED_SLOT);

    /**
     * The time when the transaction arrived. In milliseconds.
     */
    public long arrivalTime = 0;


    //public boolean confirmed = false;

    /**
     * This flag indicates if the transaction metadata was parsed from a byte array.
     */
    public AtomicBoolean parsed = new AtomicBoolean(false);

    /**
     * This flag indicates whether the transaction is considered solid or not
     */
    public AtomicBoolean solid = new AtomicBoolean(false);

    /**
     * This flag indicates if the transaction is a coordinator issued milestone.
     */
    public AtomicBoolean milestone = new AtomicBoolean(false);

    public AtomicLong height = new AtomicLong(0);
    public AtomicReference<String> sender = new AtomicReference<>("");
    public AtomicInteger snapshot = new AtomicInteger();

    /**
     * Returns a truncated representation of the bytes of the transaction.
     */
    @Override
    public byte[] bytes() {
        return bytes == null ? null : TransactionTruncator.truncateTransaction(bytes);
    }

    /**
     * Assigns the Transaction set bytes to the given byte array provided the array is not null
     *
     * @param bytes the byte array that the transaction bytes will be assigned to
     */
    @Override
    public void read(byte[] bytes) {
        if(bytes != null) {
            this.bytes = TransactionTruncator.expandTransaction(bytes);
            this.type.set(TransactionViewModel.FILLED_SLOT);
        }
    }

    /**
     * Returns a byte array containing all the relevant metadata for the transaction.
     */
    @Override
    public byte[] metadata() {
        int allocateSize =
                Hash.SIZE_IN_BYTES * 6 + //address,bundle,trunk,branch,obsoleteTag,tag
                        Long.BYTES * 9 + //value,currentIndex,lastIndex,timestamp,attachmentTimestampLowerBound,attachmentTimestampUpperBound,arrivalTime,height
                        Integer.BYTES * 3 + //validity,type,snapshot
                        1 + //solid
                        sender.get().getBytes().length; //sender
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
        buffer.put(Serializer.serialize(type.get()));
        buffer.put(Serializer.serialize(arrivalTime));
        buffer.put(Serializer.serialize(height.get()));
        //buffer.put((byte) (confirmed ? 1:0));

        // encode booleans in 1 byte
        byte flags = 0;
        flags |= solid.get() ? IS_SOLID_BITMASK : 0;
        flags |= milestone.get() ? IS_MILESTONE_BITMASK : 0;
        buffer.put(flags);

        buffer.put(Serializer.serialize(snapshot.get()));
        buffer.put(sender.get().getBytes());
        return buffer.array();
    }

    /**
     * Reads the contents of a given array of bytes, assigning the array contents to the
     * appropriate classes.
     *
     * @param bytes The byte array containing the transaction information
     */
    @Override
    public void readMetadata(byte[] bytes) {
        if(bytes == null) {
            return;
        }
        int i = 0;
        address = HashFactory.ADDRESS.create(bytes, i, Hash.SIZE_IN_BYTES);
        i += Hash.SIZE_IN_BYTES;
        bundle = HashFactory.BUNDLE.create(bytes, i, Hash.SIZE_IN_BYTES);
        i += Hash.SIZE_IN_BYTES;
        trunk = HashFactory.TRANSACTION.create(bytes, i, Hash.SIZE_IN_BYTES);
        i += Hash.SIZE_IN_BYTES;
        branch = HashFactory.TRANSACTION.create(bytes, i, Hash.SIZE_IN_BYTES);
        i += Hash.SIZE_IN_BYTES;
        obsoleteTag = HashFactory.OBSOLETETAG.create(bytes, i, Hash.SIZE_IN_BYTES);
        i += Hash.SIZE_IN_BYTES;
        value = Serializer.getLong(bytes, i);
        i += Long.BYTES;
        currentIndex = Serializer.getLong(bytes, i);
        i += Long.BYTES;
        lastIndex = Serializer.getLong(bytes, i);
        i += Long.BYTES;
        timestamp = Serializer.getLong(bytes, i);
        i += Long.BYTES;

        tag = HashFactory.TAG.create(bytes, i, Hash.SIZE_IN_BYTES);
        i += Hash.SIZE_IN_BYTES;
        attachmentTimestamp = Serializer.getLong(bytes, i);
        i += Long.BYTES;
        attachmentTimestampLowerBound = Serializer.getLong(bytes, i);
        i += Long.BYTES;
        attachmentTimestampUpperBound = Serializer.getLong(bytes, i);
        i += Long.BYTES;

        validity = Serializer.getInteger(bytes, i);
        i += Integer.BYTES;
        type.set(Serializer.getInteger(bytes, i));
        i += Integer.BYTES;
        arrivalTime = Serializer.getLong(bytes, i);
        i += Long.BYTES;
        height.set(Serializer.getLong(bytes, i));
        i += Long.BYTES;
        /*
        confirmed = bytes[i] == 1;
        i++;
        */

        // decode the boolean byte by checking the bitmasks
        solid.set((bytes[i] & IS_SOLID_BITMASK) != 0);
        milestone.set((bytes[i] & IS_MILESTONE_BITMASK) != 0);
        i++;

        snapshot.set(Serializer.getInteger(bytes, i));
        i += Integer.BYTES;
        byte[] senderBytes = new byte[bytes.length - i];
        if (senderBytes.length != 0) {
            System.arraycopy(bytes, i, senderBytes, 0, senderBytes.length);
        }
        sender.set(new String(senderBytes));
        parsed.set(true);
    }



    @Override
    public boolean canMerge() {
        return false;
    }

    @Override
    public Persistable mergeInto(Persistable source) throws OperationNotSupportedException {
        throw new OperationNotSupportedException("This object is not mergeable");
    }
    @Override
    public boolean exists() {
        return !(bytes == null || bytes.length == 0);
    }
}
