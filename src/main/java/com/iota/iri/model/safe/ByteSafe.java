package com.iota.iri.model.safe;

/**
 * Extends the HashSafeObject class to generate a null safe byte array with retrievable hash code index. This uses
 * Double-Checked Locking to ensure that the byte object is initialised correctly when referenced, and does not
 * return a null value.
 */
public class ByteSafe extends HashSafeObject {
	public ByteSafe(byte[] bytes) {
		super(bytes, "ByteSafe is attempted to be initialized with a null byte array");
	}
}
