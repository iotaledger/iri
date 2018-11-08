package com.iota.iri.service.dto;

/**
 * This class represents the core API error response.
 **/
public class ErrorResponse extends AbstractResponse {
	
    /**
     * The error string is a readable message identifying what caused this Error response.
     */
	private String error;

	/**
	 * Creates a new {@link ErrorResponse}
	 * 
	 * @param error {@link #error}
	 * @return an {@link ErrorResponse} filled with the error message
	 */
	public static AbstractResponse create(String error) {
		ErrorResponse res = new ErrorResponse();
		res.error = error;
		return res;
	}
    
    /**
     * 
     * @return {@link #error}
     */
	public String getError() {
		return error;
	}
}
