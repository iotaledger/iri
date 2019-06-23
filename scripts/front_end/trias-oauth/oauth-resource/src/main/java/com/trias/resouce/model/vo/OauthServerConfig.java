package com.trias.resouce.model.vo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OauthServerConfig {
	
	@Value("${auth-server}")
	private String oauthServer;
	
	@Value("${security.oauth2.client.client-id}")
	private String clientId;
	
	@Value("${security.oauth2.client.client-secret}")
	private String sercret;
	
	@Value("${security.oauth2.client.scope}")
	private String scope;

	public String getOauthServer() {
		return oauthServer;
	}

	public void setOauthServer(String oauthServer) {
		this.oauthServer = oauthServer;
	}

	public String getClientId() {
		return clientId;
	}

	public void setClientId(String clientId) {
		this.clientId = clientId;
	}

	public String getSercret() {
		return sercret;
	}

	public void setSercret(String sercret) {
		this.sercret = sercret;
	}

	public String getScope() {
		return scope;
	}

	public void setScope(String scope) {
		this.scope = scope;
	}
	
}
