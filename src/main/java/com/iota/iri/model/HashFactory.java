package com.iota.iri.model;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.iota.iri.model.persistables.Address;
import com.iota.iri.model.persistables.Approvee;
import com.iota.iri.model.persistables.Bundle;
import com.iota.iri.model.persistables.ObsoleteTag;
import com.iota.iri.model.persistables.Tag;
import com.iota.iri.model.persistables.Transaction;

public enum HashFactory {
    TRANSACTION(TransactionHash.class),
    ADDRESS(AddressHash.class),
    BUNDLE(BundleHash.class),
    TAG(TagHash.class),
    OBSOLETETAG(ObsoleteTagHash.class),
    
    /**
     * Creates from generic class, should be passed in the create() function. 
     * Will return NULL_HASH if other functions are used
     */
	GENERIC;
    
    private static final Logger log = LoggerFactory.getLogger(HashFactory.class);
    
    //byte[], offset, size
    private Constructor<Hash> constructor;
    
    //trits string
    private Constructor<Hash> stringConstructor;

    @SuppressWarnings("unchecked")
    private HashFactory(Class<?> clazz) {
        try {
            this.constructor = (Constructor<Hash>) clazz.getConstructor(byte[].class, int.class, int.class);
            this.stringConstructor = (Constructor<Hash>) clazz.getConstructor(String.class);
        } catch (NoSuchMethodException | SecurityException e) {
            e.printStackTrace();
            this.constructor = null;
        }
    }
    
    private HashFactory() {
        this.constructor = null;
    }

    private Hash createFromReflection(byte[] source, int sourceOffset, int sourceSize) {
        if (constructor == null) {
            return Hash.NULL_HASH;
        }
        
        try {
            return constructor.newInstance(source, sourceOffset, sourceSize);
        } catch(RuntimeException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
            return Hash.NULL_HASH;
        }
    }
    
    /**
     * Creates a Hash using the provided trits
     * @param trits the source data
     * @param sourceOffset the offset we start reading from
     * @return the hash
     */
    public Hash create(String trits) {
        if (stringConstructor == null) {
            return Hash.NULL_HASH;
        }
      
        try {
            return stringConstructor.newInstance(trits);
        } catch(RuntimeException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
            return Hash.NULL_HASH;
        }
    }

    /**
     * Creates a Hash using the provided trits
     * @param trits the source data
     * @param sourceOffset the offset we start reading from
     * @param sourceSize the size this hash is in bytes, starting from offset
     * @return the hash
     */
    public Hash create(byte[] source, int sourceOffset, int sourceSize) {
        return createFromReflection(source, sourceOffset, sourceSize);
    }
    
    /**
     * Creates a Hash using the provided trits
     * @param trits the source data
     * @param sourceOffset the offset we start reading from
     * @return the hash
     */
    public Hash create(byte[] trits, int sourceOffset) {
        return createFromReflection(trits, sourceOffset, Hash.SIZE_IN_TRITS);
    }
    
    /**
     * Creates a Hash using the provided source.
     * Starts from the beginning, source size is based on source length
     * @param source the source data
     * @return the hash
     */
  	public Hash create(byte[] source) {
  		return createFromReflection(source, 0, source.length == Hash.SIZE_IN_TRITS ? Hash.SIZE_IN_TRITS : Hash.SIZE_IN_BYTES);
  	}
  	
  	//
  	// These are used by RocksDB
  	//

    /**
     * 
     * @param modelClass The model this Hash represents
     * @param source the source data, bytes or trits. Based on the length of source data
     * @return the hash of the correct type
     */
    public Hash create(Class<?> modelClass, byte[] source) {
        return create(modelClass, source, 0, source.length == AbstractHash.SIZE_IN_TRITS ? AbstractHash.SIZE_IN_TRITS : AbstractHash.SIZE_IN_BYTES);
    }

    /**
     * 
     * @param modelClass The model this Hash represents
     * @param source the source data, bytes or trits
     * @param sourceOffset the offset in the source
     * @param sourceSize the size this hash is in bytes, starting from offset
     * @return the hash of the correct type
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
