package com.trias.resouce.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LocalConfig {

	@Value("${auth-server}")
	private String ouathServerUrl;
	
	@Value("${security.oauth2.client.client-id}")
	private String clientId;
	
	@Value("${security.oauth2.client.client-secret}")
	private String secret;
	

	public String getOuathServerUrl() {
		return ouathServerUrl;
	}

	public void setOuathServerUrl(String ouathServerUrl) {
		this.ouathServerUrl = ouathServerUrl;
	}

	public String getClientId() {
		return clientId;
	}

	public void setClientId(String clientId) {
		this.clientId = clientId;
	}

	public String getSecret() {
		return secret;
	}

	public void setSecret(String secret) {
		this.secret = secret;
	}
	
}
