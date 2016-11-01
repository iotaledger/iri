package com.iota.iri.service.dto;

import java.util.List;

public class AttachToTangleResponse extends AbstractResponse {

	private List<String> elements;
	
	public static AbstractResponse create(List<String> elements) {
		AttachToTangleResponse res = new AttachToTangleResponse();
		res.elements = elements;
		return res;
	}
}
