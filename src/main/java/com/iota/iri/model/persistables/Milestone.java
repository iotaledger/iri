package com.iota.iri.model.persistables;

import com.iota.iri.model.Hash;
import com.iota.iri.model.HashFactory;
import com.iota.iri.model.IntegerIndex;
import com.iota.iri.storage.Persistable;
import com.iota.iri.utils.Serializer;
import org.apache.commons.lang3.ArrayUtils;

/**
 * This is a collection of hash identifiers that can change the value of a <tt>Milestone</tt>
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

    /**@returns The collection of milestone indexes and associated hash identifiers*/
    public byte[] bytes() {
        return ArrayUtils.addAll(index.bytes(), hash.bytes());
    }

    /**
     * Reads the input bytes and assigns an index and hash to the <tt>Milestone</tt> collection
     * @param bytes The input bytes to be added to the collection
     */
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
    public boolean merge() {
        return false;
    }
}
