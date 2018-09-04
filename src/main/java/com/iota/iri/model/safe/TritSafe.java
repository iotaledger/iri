package com.iota.iri.model.safe;

public class TritSafe extends SafeObject {
	public TritSafe(byte[] bytes) {
		super(bytes, "TritSafe is attempted to be initialized with a null byte array");
	}
}
