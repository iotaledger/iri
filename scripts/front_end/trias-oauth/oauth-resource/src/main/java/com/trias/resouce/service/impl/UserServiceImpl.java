package com.trias.resouce.service.impl;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import com.google.gson.Gson;
import com.trias.resouce.body.request.OauthLoginRequestBody;
import com.trias.resouce.body.response.UserInfo;
import com.trias.resouce.body.response.UserResourceResponseBody;
import com.trias.resouce.config.LocalConfig;
import com.trias.resouce.exception.OauthNoResposeException;
import com.trias.resouce.mapper.UserInfoMapper;
import com.trias.resouce.model.Resource;
import com.trias.resouce.service.UserService;
import com.trias.resouce.util.OauthUtil;

@Service
public class UserServiceImpl implements UserService {

	private static Gson GSON = new Gson();

	private static final String ACCESS_TOKEN_KEY = "access_token";

	private static final String OAUTH_SERVER = "http://localhost:8080/trias";

	private static final String CLIENT_ID = "client";

	private static final String SECRET = "secret";

	private static final String SCOPE = "all";

	private static final String OAUTH_ACTION = "/oauth/token";

	private static final String GRANT_TYPE = "password";

	@Autowired
	private UserInfoMapper userInfoMapper;
	
	@Autowired
	private LocalConfig localConfig;

	@Override
	public UserResourceResponseBody getUserResources(Authentication authentication) {
		Collection<SimpleGrantedAuthority> authorities = (Collection<SimpleGrantedAuthority>) authentication.getAuthorities();
		UserResourceResponseBody responseBody = new UserResourceResponseBody();
		List<String> nameList = new ArrayList<String>();
		Iterator<SimpleGrantedAuthority> it = authorities.iterator();
		while (it.hasNext()) {
			nameList.add(it.next().getAuthority());
		}
		List<Resource> resourceList = userInfoMapper.getResourceByUserRole(nameList);
		responseBody.setResourceList(resourceList);
		UserInfo userInfo = new UserInfo();
		userInfo.setUsername(authentication.getName());
		responseBody.setUserInfo(userInfo);
		return responseBody;
	}

	@Override
	public String oauthLogin(OauthLoginRequestBody request) {
		String result = "";
		try {
			String response = sendOauthUser(request.getUsername(), request.getPassword());
			Map<String, String> responseBody = GSON.fromJson(response, Map.class);
			if (CollectionUtils.isEmpty(responseBody)) {
				throw new OauthNoResposeException("Oauth Server has not any response");
			}
			result = responseBody.get(ACCESS_TOKEN_KEY);
		} catch (Exception e) {
			throw e;
		}
		return result;
	}

	public String sendOauthUser(String username, String password) {
		String result = "";
		try {
			HttpClientBuilder httpClientBuilder = HttpClientBuilder.create();
			CloseableHttpClient closeableHttpClient = httpClientBuilder.build();
			HttpPost httpPost = new HttpPost(localConfig.getOuathServerUrl() + OAUTH_ACTION);
			httpPost.addHeader("Authorization", getHeader());
			MultipartEntityBuilder builder = MultipartEntityBuilder.create();
			builder.addPart("scope", new StringBody(SCOPE, ContentType.MULTIPART_FORM_DATA));
			builder.addPart("grant_type", new StringBody(GRANT_TYPE, ContentType.MULTIPART_FORM_DATA));
			builder.addPart("username", new StringBody(username, ContentType.MULTIPART_FORM_DATA));
			builder.addPart("password", new StringBody(OauthUtil.EncodePassword(username, password), ContentType.MULTIPART_FORM_DATA));
			HttpEntity postEntity = builder.build();
			httpPost.setEntity(postEntity);
			HttpResponse httpResponse = null;
			HttpEntity entity = null;
			try {
				httpResponse = closeableHttpClient.execute(httpPost);
				entity = httpResponse.getEntity();
				if (entity != null) {
					result = EntityUtils.toString(entity);
				}
			} catch (ClientProtocolException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
			closeableHttpClient.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return result;
	}

	private String getHeader() {
		String auth = CLIENT_ID + ":" + SECRET;
		byte[] encodedAuth = Base64.encodeBase64(auth.getBytes(Charset.forName("US-ASCII")));
		String authHeader = "Basic " + new String(encodedAuth);
		return authHeader;
	}

}
