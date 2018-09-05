package com.iota.iri.service.dto;

/**
 * This class represents the core API exception response.
 **/
public class ExceptionResponse extends AbstractResponse {
	
	private String exception;

	public static AbstractResponse create(String exception) {
		ExceptionResponse res = new ExceptionResponse();
		res.exception = exception;
		return res;
	}
    
    /**
     * Gets the exception
     *
     * @return The exception.
     */
	public String getException() {
		return exception;
	}
}
