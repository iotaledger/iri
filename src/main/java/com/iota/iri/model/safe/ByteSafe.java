package com.iota.iri.model.safe;

public class ByteSafe extends HashSafeObject {
	public ByteSafe(byte[] bytes) {
		super(bytes, "ByteSafe is attempted to be initialized with a null byte array");
	}
}
