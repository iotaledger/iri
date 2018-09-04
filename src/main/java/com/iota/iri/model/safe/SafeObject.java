package com.iota.iri.model.safe;

import java.util.Objects;

public class SafeObject {
	
	byte[] safeObj;

	SafeObject(byte[] obj, String messageIfUnsafe){
		this.safeObj = obj;
		
		checkSafe(messageIfUnsafe);
	}
	
	public byte[] getData() {
		return safeObj;
	}
	
	protected void checkSafe(String messageIfUnsafe) {
		Objects.requireNonNull(safeObj, messageIfUnsafe);
	}
}
