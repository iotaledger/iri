package com.iota.iri.service.dto;

/**
 * This class represents the core API exception response.
 **/
public class ExceptionResponse extends AbstractResponse {
	
    /**
     * Contains a readable message as to why an exception has been thrown.
     * This either due to invalid payload of a API request, or the message of an uncaught exception.
     */
	private String exception;

	/**
	 * 
	 * Creates a new {@link ExceptionResponse}
     * 
     * @param exception {@link #exception}
     * @return an {@link ExceptionResponse} filled with the exception
	 */
	public static AbstractResponse create(String exception) {
		ExceptionResponse res = new ExceptionResponse();
		res.exception = exception;
		return res;
	}
    
    /**
     * 
     * @return {@link #exception}
     */
	public String getException() {
		return exception;
	}
}
