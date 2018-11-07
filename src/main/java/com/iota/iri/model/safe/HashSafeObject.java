package com.iota.iri.model.safe;

import java.util.Arrays;

/**
 *Creates a null safe Hash object.
 */
public class HashSafeObject extends SafeObject {

	/**A hash code index to be assigned to each Hash object*/
	private Integer hashcode;

	/**
	 * Constructor for safe Hash object from a byte array
	 *
	 * @param obj the byte array that the object will be generated from
	 * @param messageIfUnsafe error message for instantiation failure
	 */

	public HashSafeObject(byte[] obj, String messageIfUnsafe) {
		super(obj, messageIfUnsafe);
		
        this.hashcode = Arrays.hashCode(getData());
	}

	/**
	 * Returns the hash code from the contained data
	 *
	 * @return the hash code index of the current object
	 */
	public Integer getHashcode() {
		return hashcode;
	}
}
