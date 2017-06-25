package com.iota.iri.service.dto;

public class ErrorResponse extends AbstractResponse {
	
	private String error;

	public static AbstractResponse create(String error) {
		ErrorResponse res = new ErrorResponse();
		res.error = error;
		return res;
	}

	@SuppressWarnings("unused") // used in the API
	public String getError() {
		return error;
	}
}
