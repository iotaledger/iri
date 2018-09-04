package com.iota.iri.model.safe;

import java.util.Arrays;

public class HashSafeObject extends SafeObject {

	private Integer hashcode;

	public HashSafeObject(byte[] obj, String messageIfUnsafe) {
		super(obj, messageIfUnsafe);
		
        this.hashcode = Arrays.hashCode(getData());
	}
	
	public Integer getHashcode() {
		return hashcode;
	}
}
