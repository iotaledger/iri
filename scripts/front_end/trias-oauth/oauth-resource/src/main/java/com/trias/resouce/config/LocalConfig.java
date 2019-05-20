package com.trias.resouce.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LocalConfig {

	@Value("${auth-server}")
	private String ouathServerUrl;

	public String getOuathServerUrl() {
		return ouathServerUrl;
	}

	public void setOuathServerUrl(String ouathServerUrl) {
		this.ouathServerUrl = ouathServerUrl;
	}
	
}
