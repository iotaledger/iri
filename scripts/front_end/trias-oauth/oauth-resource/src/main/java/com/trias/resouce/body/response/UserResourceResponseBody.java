package com.trias.resouce.body.response;

import java.io.Serializable;
import java.util.List;

import com.trias.resouce.model.Resource;
import com.trias.resouce.model.User;

public class UserResourceResponseBody implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private List<Resource> resourceList;
	private User userInfo;

	public List<Resource> getResourceList() {
		return resourceList;
	}

	public void setResourceList(List<Resource> resourceList) {
		this.resourceList = resourceList;
	}

	public User getUserInfo() {
		return userInfo;
	}

	public void setUserInfo(User userInfo) {
		this.userInfo = userInfo;
	}

}
