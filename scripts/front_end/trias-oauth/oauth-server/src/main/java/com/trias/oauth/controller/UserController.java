package com.trias.oauth.controller;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.trias.oauth.body.CommonResponse;
import com.trias.oauth.body.request.RegesterUserRequestBody;
import com.trias.oauth.service.UserService;

@Controller
@RequestMapping("user")
public class UserController {

	private static final Logger logger = LoggerFactory.getLogger(UserController.class);
	
	@Autowired
	private UserService userService;

	@RequestMapping("/register")
	@ResponseBody
	public CommonResponse registerUser(@RequestBody RegesterUserRequestBody request) {
		String username = request.getUsername();
		String password = request.getPassword();
		if (StringUtils.isBlank(username) || StringUtils.isBlank(password)) {
			return CommonResponse.CreateResponse("error,username or password is empty", 0, null);
		}
		try {
			return userService.registerUser(username, password);
		} catch (Exception e) {
			logger.error("register user happens a error:'{}'",e);
			return CommonResponse.CreateResponse("System error", 0, null);
		}
		
	}

}
