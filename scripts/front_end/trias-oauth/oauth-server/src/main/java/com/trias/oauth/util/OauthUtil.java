package com.trias.oauth.util;

import java.nio.charset.Charset;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;

import com.trias.oauth.exception.UserInfoEmptyException;


public class OauthUtil {

	public static String EncodePassword(String username, String password) {
		if (StringUtils.isEmpty(username) || StringUtils.isEmpty(password)) {
			throw new UserInfoEmptyException("username or password is empty");
		}
		return MD5Util
				.MD5(new String(Base64.encodeBase64((username + password).getBytes(Charset.forName("US-ASCII")))));
	}
}
