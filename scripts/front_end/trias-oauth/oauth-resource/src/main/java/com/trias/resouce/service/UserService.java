package com.trias.resouce.service;

import org.springframework.security.core.Authentication;

import com.trias.resouce.body.CommonResponse;
import com.trias.resouce.body.request.OauthLoginRequestBody;
import com.trias.resouce.body.request.RegisterOauthRequest;
import com.trias.resouce.body.response.UserResourceResponseBody;

public interface UserService {

	UserResourceResponseBody getUserResources(Authentication authentication);

	String oauthLogin(OauthLoginRequestBody request);

	CommonResponse registerOauthUser(RegisterOauthRequest request);

	CommonResponse addition(RegisterOauthRequest request);

}
