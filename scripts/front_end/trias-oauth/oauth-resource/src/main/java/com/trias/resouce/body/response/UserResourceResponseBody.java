package com.trias.resouce.body.response;

import java.io.Serializable;
import java.util.List;

import com.trias.resouce.model.Resource;

public class UserResourceResponseBody implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private List<Resource> resourceList;
	private UserInfo userInfo;

	public List<Resource> getResourceList() {
		return resourceList;
	}

	public void setResourceList(List<Resource> resourceList) {
		this.resourceList = resourceList;
	}

	public UserInfo getUserInfo() {
		return userInfo;
	}

	public void setUserInfo(UserInfo userInfo) {
		this.userInfo = userInfo;
	}

}
