package com.iota.iri.service.dto;

public class BooleanResponse extends AbstractResponse {
	
	private boolean state;

	public static AbstractResponse create(boolean b) {
		BooleanResponse res = new BooleanResponse();
		res.state = b;
		return res;
	}

	public Boolean getState() {
		return state;
	}
}
