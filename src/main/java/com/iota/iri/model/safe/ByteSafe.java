package com.iota.iri.model.safe;

/**
 *Utilises the HashSafeObject class to generate a null safe byte array with retrievable hash code index
 */
public class ByteSafe extends HashSafeObject {
	public ByteSafe(byte[] bytes) {
		super(bytes, "ByteSafe is attempted to be initialized with a null byte array");
	}
}
