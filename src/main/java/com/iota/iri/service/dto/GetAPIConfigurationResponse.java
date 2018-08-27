package com.iota.iri.service.dto;

import com.iota.iri.service.API;

public class GetAPIConfigurationResponse extends AbstractResponse {

	private int minRandomWalks;
	private int maxRandomWalks;
	private int maxFindTxs;
	private int maxRequestList;
	private int maxGetTrytes;
	private int maxBodyLength;

	public static AbstractResponse create(API api) {
		GetAPIConfigurationResponse response = new GetAPIConfigurationResponse();
		response.setMaxBodyLength(api.getMaxBodyLength());
		response.setMaxGetTrytes(api.getMaxGetTrytes());
		response.setMaxRequestList(api.getMaxRequestList());
		response.setMaxFindTxs(api.getMaxFindTxs());
		response.setMaxRandomWalks(api.getMaxRandomWalks());
		response.setMinRandomWalks(api.getMinRandomWalks());

		return response;
	}

	public void setMinRandomWalks(int minRandomWalks) {
		this.minRandomWalks = minRandomWalks;
	}

	public void setMaxRandomWalks(int maxRandomWalks) {
		this.maxRandomWalks = maxRandomWalks;
	}

	public void setMaxFindTxs(int maxFindTxs) {
		this.maxFindTxs = maxFindTxs;
	}

	public void setMaxRequestList(int maxRequestList) {
		this.maxRequestList = maxRequestList;
	}

	public void setMaxGetTrytes(int maxGetTrytes) {
		this.maxGetTrytes = maxGetTrytes;
	}

	public void setMaxBodyLength(int maxBodyLength) {
		this.maxBodyLength = maxBodyLength;
	}

	public int getMinRandomWalks() {
		return minRandomWalks;
	}

	public int getMaxRandomWalks() {
		return maxRandomWalks;
	}

	public int getMaxFindTxs() {
		return maxFindTxs;
	}

	public int getMaxRequestList() {
		return maxRequestList;
	}

	public int getMaxGetTrytes() {
		return maxGetTrytes;
	}

	public int getMaxBodyLength() {
		return maxBodyLength;
	}
}
