package com.trias.oauth.util;

import org.apache.commons.codec.digest.DigestUtils;

public class MD5Util {

	public static String MD5(String str) {
		String encodeStr = DigestUtils.md5Hex(str);
		return encodeStr;
	}
}
