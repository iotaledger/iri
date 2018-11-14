package com.iota.iri.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.iota.iri.model.persistables.Address;
import com.iota.iri.model.persistables.Approvee;
import com.iota.iri.model.persistables.Bundle;
import com.iota.iri.model.persistables.ObsoleteTag;
import com.iota.iri.model.persistables.Tag;
import com.iota.iri.model.persistables.Transaction;
import com.iota.iri.storage.Persistable;
import com.iota.iri.utils.Converter;

/**
 * Generates an appropriate Hash model from a set of source data. The available models that
 * can be instantiated include:
 * <ul>
 *     <li>Transaction Hash</li>
 *     <li>Address Hash</li>
 *     <li>Bundle Hash</li>
 *     <li>Tag Hash</li>
 *     <li>Obsolete Tag Hash</li>
 * </ul>
 */
public enum HashFactory {
    TRANSACTION(Transaction.class),
    ADDRESS(Address.class),
    BUNDLE(Bundle.class),
    TAG(Tag.class),
    OBSOLETETAG(ObsoleteTag.class),
    
    /**
     * Creates from generic class, should be passed in the create() function. 
     * Will return NULL_HASH if other functions are used
     */
	GENERIC;
    
    private static final Logger log = LoggerFactory.getLogger(HashFactory.class);
    
    private Class<? extends Persistable> clazz;

    HashFactory(Class<? extends Persistable> clazz) {
        this.clazz = clazz;
    }
    
    HashFactory() {
        
    }
    
    /**
     * Creates a Hash using the provided trytes
     * @param trytes The source data as a string of trytes
     * @return The hash
     */
    public Hash create(String trytes) {
        byte[] trits = new byte[Hash.SIZE_IN_TRITS];
        Converter.trits(trytes, trits, 0);
        return create(clazz, trits, 0, Hash.SIZE_IN_TRITS);
    }

    /**
     * Creates a Hash using the provided source data
     * @param source The source data
     * @param sourceOffset The offset we start reading from
     * @param sourceSize The size this hash is in bytes, starting from offset
     * @return The hash
     */
    public Hash create(byte[] source, int sourceOffset, int sourceSize) {
        return create(clazz, source, sourceOffset, sourceSize);
    }
    
    /**
     * Creates a Hash using the provided trits
     * @param trits The source data as an array of trits
     * @param sourceOffset The offset we start reading from
     * @return The hash
     */
    public Hash create(byte[] trits, int sourceOffset) {
        return create(clazz, trits, sourceOffset, Hash.SIZE_IN_TRITS);
    }
    
    /**
     * Creates a Hash using the provided source.
     * Starts from the beginning, source size is based on source length
     * @param source The source data
     * @return The hash
     */
  	public Hash create(byte[] source) {
  		return create(clazz, source, 0, source.length == Hash.SIZE_IN_TRITS ? Hash.SIZE_IN_TRITS : Hash.SIZE_IN_BYTES);
  	}

    /**
     * Instantiates the creation of a specified Hash type. The source offset is set to 0.
     *
     * @param modelClass The model this Hash represents
     * @param source The source data, bytes or trits. Based on the length of source data
     * @return The hash of the correct type
     */
    public Hash create(Class<?> modelClass, byte[] source) {
        return create(modelClass, source, 0, source.length == AbstractHash.SIZE_IN_TRITS ? AbstractHash.SIZE_IN_TRITS : AbstractHash.SIZE_IN_BYTES);
    }

    /**
     * Generates the specified hash type.
     *
     * @param modelClass The model this Hash represents
     * @param source The source data, bytes or trits
     * @param sourceOffset The offset in the source that the hash will be created from
     * @param sourceSize The size this hash is in bytes, starting from offset
     * @return The hash of the correct type
     */
    public Hash create(Class<?> modelClass, byte[] source, int sourceOffset, int sourceSize) {
        
        //Transaction is first since its the most used
        if (modelClass.equals(Transaction.class) || modelClass.equals(Approvee.class)) {
            return new TransactionHash(source, sourceOffset, sourceSize);
            
        } else if (modelClass.equals(Address.class)) {
            return new AddressHash(source, sourceOffset, sourceSize);
            
        } else if (modelClass.equals(Bundle.class)) {
            return new BundleHash(source, sourceOffset, sourceSize);
            
        } else if (modelClass.equals(Tag.class)) {
            return new TagHash(source, sourceOffset, sourceSize);
            
        } else if (modelClass.equals(ObsoleteTag.class)) {
            return new ObsoleteTagHash(source, sourceOffset, sourceSize);  
            
        } else {
            log.warn("Tried to construct hash from unknown class " + modelClass);
            //Default to transaction hash or NULL_HASH?
            return new TransactionHash(source, sourceOffset, sourceSize);
        }
    }
}
