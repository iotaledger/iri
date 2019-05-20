package com.trias.resouce.controller;

import java.util.Collection;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import com.trias.resouce.body.CommonResponse;
import com.trias.resouce.body.request.OauthLoginRequestBody;
import com.trias.resouce.body.response.UserResourceResponseBody;
import com.trias.resouce.exception.IllegalRoleException;
import com.trias.resouce.exception.OauthNoResposeException;
import com.trias.resouce.model.Resource;
import com.trias.resouce.service.UserService;

@Controller
@RequestMapping("user")
public class UserController {

	Logger logger = LoggerFactory.getLogger(UserController.class);

	@Autowired
	private UserService userService;

	@RequestMapping(value = "getUserInfo", method = RequestMethod.GET)
	@ResponseBody
	public CommonResponse getUserInfo(HttpServletRequest request) {
		try {
			UserResourceResponseBody responseBody = userService
					.getUserResources(SecurityContextHolder.getContext()
							.getAuthentication());
			return CommonResponse.CreateSuccessResponse("success", responseBody);
		} catch (IllegalRoleException ie) {
			return CommonResponse.CreateErrorResponse("User role is not exist", null);
		} catch (Exception e) {
			logger.error("getUserInfo happens a error:{}", new Object[] { e });
			return CommonResponse.CreateErrorResponse("Illegal user", null);
		}

	}

	@RequestMapping(value = "/oauthLogin", method = RequestMethod.POST)
	@ResponseBody
	public CommonResponse oauthLogin(@RequestBody OauthLoginRequestBody request) {
		try {
			String token = userService.oauthLogin(request);
			if (StringUtils.isEmpty(token)) {
				return CommonResponse.CreateErrorResponse("User not exists", null);
			} else {
				return CommonResponse.CreateSuccessResponse("Login success", token);
			}
		} catch(OauthNoResposeException oe) {
			logger.error("oauthLogin happens a error:{}", new Object[] { oe });
			return CommonResponse.CreateErrorResponse("System error", oe.getMessage());
		} catch (Exception e) {
			logger.error("oauthLogin happens a error:{}", new Object[] { e });
			return CommonResponse.CreateErrorResponse("System error", null);
		}

	}
}
