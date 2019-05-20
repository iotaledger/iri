package com.trias.resouce.service;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.trias.resouce.body.request.OauthLoginRequestBody;
import com.trias.resouce.body.response.UserResourceResponseBody;
import com.trias.resouce.model.Resource;

public interface UserService {

	UserResourceResponseBody getUserResources(Authentication authentication);

	String oauthLogin(OauthLoginRequestBody request);

}
