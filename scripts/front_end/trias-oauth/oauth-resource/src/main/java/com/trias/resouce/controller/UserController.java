package com.trias.resouce.controller;

import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import com.trias.resouce.body.CommonResponse;
import com.trias.resouce.body.request.OauthLoginRequestBody;
import com.trias.resouce.body.request.RegisterOauthRequest;
import com.trias.resouce.body.response.UserResourceResponseBody;
import com.trias.resouce.exception.IllegalRoleException;
import com.trias.resouce.exception.OauthNoResposeException;
import com.trias.resouce.service.UserService;

@Controller
@RequestMapping("user")
public class UserController {

	private static final Logger logger = LoggerFactory.getLogger(UserController.class);

	@Autowired
	private UserService userService;

	@RequestMapping(value = "/getUserInfo", method = RequestMethod.GET)
	@ResponseBody
	public CommonResponse getUserInfo(HttpServletRequest request) {
		try {
			UserResourceResponseBody responseBody = userService
					.getUserResources(SecurityContextHolder.getContext().getAuthentication());
			return CommonResponse.CreateResponse("success", 1, responseBody);
		} catch (IllegalRoleException ie) {
			return CommonResponse.CreateResponse("User role is not exist", 0, null);
		} catch (Exception e) {
			logger.error("getUserInfo happens an error:{}", new Object[] { e });
			return CommonResponse.CreateResponse("Illegal user", 0, null);
		}

	}

	@RequestMapping(value = "/oauthLogin", method = RequestMethod.POST)
	@ResponseBody
	public CommonResponse oauthLogin(@RequestBody OauthLoginRequestBody request) {
		try {
			String token = userService.oauthLogin(request);
			if (StringUtils.isEmpty(token)) {
				return CommonResponse.CreateResponse("User not exists", 0, null);
			} else {
				return CommonResponse.CreateResponse("Login success", 1, token);
			}
		} catch (OauthNoResposeException oe) {
			logger.error("oauthLogin happens an error:{}", new Object[] { oe });
			return CommonResponse.CreateResponse("System error", 0, oe.getMessage());
		} catch (Exception e) {
			logger.error("oauthLogin happens an error:{}", new Object[] { e });
			return CommonResponse.CreateResponse("System error", 0, null);
		}
	}

	@RequestMapping(value = "/register", method = RequestMethod.POST)
	@ResponseBody
	public CommonResponse register(@RequestBody RegisterOauthRequest request) {
		if (StringUtils.isEmpty(request.getUsername()) || StringUtils.isEmpty(request.getPassword())) {
			return CommonResponse.CreateResponse("error,username or password is empty", 0, null);
		}
		try {
			return userService.registerOauthUser(request);
		} catch (Exception e) {
			logger.error("register happens an error:'{}'", new Object[] { e });
			return CommonResponse.CreateResponse("System error", 0, null);
		}
	}

	@RequestMapping(value = "/addition", method = RequestMethod.POST)
	@ResponseBody
	public CommonResponse addition(@RequestBody RegisterOauthRequest request) {
		request.setUsername(SecurityContextHolder.getContext().getAuthentication().getName());
		try {
			return userService.addition(request);
		} catch (Exception e) {
			logger.error("addition happens an error:'{}'", new Object[] { e });
			return CommonResponse.CreateResponse("System error", 0, null);
		}
	}
}

