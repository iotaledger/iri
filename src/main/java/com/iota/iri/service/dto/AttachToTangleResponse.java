package com.iota.iri.service.dto;

import java.util.List;

public class AttachToTangleResponse extends AbstractResponse {

	private List<String> trytes;
	
	public static AbstractResponse create(List<String> elements) {
		AttachToTangleResponse res = new AttachToTangleResponse();
		res.trytes = elements;
		return res;
	}
	
	public List<String> getTrytes() {
		return trytes;
	}
}
