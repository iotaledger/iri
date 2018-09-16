package com.iota.iri.service.dto;

/**
 * This class represents the core API error response.
 **/
public class ErrorResponse extends AbstractResponse {
	
	private String error;

	public static AbstractResponse create(String error) {
		ErrorResponse res = new ErrorResponse();
		res.error = error;
		return res;
	}
    
    /**
     * Gets the error
     *
     * @return The error.
     */
	public String getError() {
		return error;
	}
}
