package com.iota.iri.model.safe;

import java.util.Objects;

public class SafeObject {
	
	byte[] safeObj;

	/**
	 * Creates an object which is verified during instantiation.
	 * @param obj the array of bytes we check against
	 * @param messageIfUnsafe The message we emit when it is not safe (inside an exception)
	 */
	SafeObject(byte[] obj, String messageIfUnsafe){
		this.safeObj = obj;
		
		checkSafe(messageIfUnsafe);
	}
	
	/**
	 * This data is checked against its "save" conditions.
	 * @return the data this object guards
	 */
	public byte[] getData() {
		return safeObj;
	}
	
	protected void checkSafe(String messageIfUnsafe) {
		Objects.requireNonNull(safeObj, messageIfUnsafe);
	}
}
