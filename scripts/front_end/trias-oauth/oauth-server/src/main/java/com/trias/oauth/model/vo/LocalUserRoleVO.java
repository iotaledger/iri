package com.trias.oauth.model.vo;

import com.trias.oauth.model.LocalUser;

public class LocalUserRoleVO extends LocalUser{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private String roleName;

	public String getRoleName() {
		return roleName;
	}

	public void setRoleName(String roleName) {
		this.roleName = roleName;
	}
	
	
}
