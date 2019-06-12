package com.trias.oauth.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.trias.oauth.body.CommonResponse;
import com.trias.oauth.body.request.RegesterUserRequestBody;


@Controller
@RequestMapping("user")
public class UserController {

	@RequestMapping("/register")
	@ResponseBody
	public CommonResponse registerUser(@RequestBody RegesterUserRequestBody request) {
		
		
		return null;
	}
	
}
