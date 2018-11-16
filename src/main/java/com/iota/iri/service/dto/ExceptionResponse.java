package com.iota.iri.service.dto;

import com.iota.iri.service.API;

/**
 * 
 * This class represents the response the API returns when an exception has occurred.
 * This can be returned for various reasons, see {@link API#process} for the cases.
 * 
 **/
public class ExceptionResponse extends AbstractResponse {
	
    /**
     * Contains a readable message as to why an exception has been thrown.
     * This is either due to invalid payload of an API request, or the message of an uncaught exception.
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
