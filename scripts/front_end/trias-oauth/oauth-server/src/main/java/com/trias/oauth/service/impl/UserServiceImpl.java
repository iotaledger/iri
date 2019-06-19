package com.trias.oauth.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.trias.oauth.body.CommonResponse;
import com.trias.oauth.mapper.UserDetailsMapper;
import com.trias.oauth.model.Content;
import com.trias.oauth.model.vo.LocalUserRoleVO;
import com.trias.oauth.service.UserService;
import com.trias.oauth.util.OauthUtil;

@Service
public class UserServiceImpl implements UserService {

	private static final Logger logger = LoggerFactory.getLogger(UserServiceImpl.class);

	@Autowired
	private UserDetailsMapper userDetailsMapper;

	@Override
	public CommonResponse registerUser(String username, String password) {
		LocalUserRoleVO userVo = userDetailsMapper.findUserByName(username);
		if (userVo == null) {
			try {
				String encodePassword = OauthUtil.EncodePassword(username, password);
				userDetailsMapper.saveUser(username, encodePassword);
				return CommonResponse.CreateResponse("Create user success", 1, null);
			} catch (Exception e) {
				logger.error("Create user error :{}", new Object[] { e });
				return CommonResponse.CreateResponse("Create user fail.", 0, null);
			}
		} else {
			return CommonResponse.CreateResponse("User exists.", Content.USER_EXIST_CODE, userVo);
		}
	}
}
