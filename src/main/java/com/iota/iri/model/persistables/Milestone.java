package com.iota.iri.model.persistables;

import com.iota.iri.model.Hash;
import com.iota.iri.model.HashFactory;
import com.iota.iri.model.IntegerIndex;
import com.iota.iri.storage.Persistable;
import com.iota.iri.utils.Serializer;
import org.apache.commons.lang3.ArrayUtils;
import javax.naming.OperationNotSupportedException;

/**
 * This stores a hash identifier for a <tt>Milestone</tt> transaction, indexed by a unique <tt>IntegerIndex</tt>
 * generated from the serialized byte input.
 *
 * <p>
 *     Milestones are issued by the coordinator to direct and verify the tangle.
 *     The coordinator issues milestones to help confirm valid transactions. Milestones
 *     are also verified by other nodes to ensure that they are not malicious in nature,
 *     and do not confirm transactions that it should not (i.e. double spending).
 * </p>
 */
public class Milestone implements Persistable {

    /**Represents the integer index value of the milestone identifier*/
    public IntegerIndex index;
    /**Represents the hash identifier of the milestone*/
    public Hash hash;

    /**@return The milestone index and associated hash identifier*/
    @Override
    public byte[] bytes() {
        return ArrayUtils.addAll(index.bytes(), hash.bytes());
    }

    /**
     * Reads the input bytes and assigns an <tt>IntegerIndex</tt> to store a <tt>Milestone</tt> transaction
     * hash identifier
     * @param bytes The input bytes of the milestone transaction
     */
    @Override
    public void read(byte[] bytes) {
        if(bytes != null) {
            index = new IntegerIndex(Serializer.getInteger(bytes));
            hash = HashFactory.TRANSACTION.create(bytes, Integer.BYTES, Hash.SIZE_IN_BYTES);
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
    public boolean canMerge() {
        return false;
    }
    @Override
    public Persistable mergeInto(Persistable source) throws OperationNotSupportedException{
        throw new OperationNotSupportedException("This object is not mergeable");
    }

    @Override
    public boolean exists() {
        return !(hash == null || index == null);
    }
}
