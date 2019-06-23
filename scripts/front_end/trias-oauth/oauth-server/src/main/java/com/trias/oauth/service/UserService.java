package com.trias.oauth.service;

import com.trias.oauth.body.CommonResponse;

public interface UserService {

	CommonResponse registerUser(String username, String password);

}
