package com.trias.oauth.model;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;
import java.util.Collections;

public class CustomUserDetails extends User {

	private LocalUser user;

	public CustomUserDetails(LocalUser user) {
		super(user.getUsername(), user.getPassword(), Collections.EMPTY_SET);
		this.user = user;
	}
	
	public CustomUserDetails(LocalUser user, Collection<? extends GrantedAuthority> authorities) {
		super(user.getUsername(), user.getPassword(), authorities);
		this.user = user;
	}

	public LocalUser getUser() {
		return user;
	}

	public void setUser(LocalUser user) {
		this.user = user;
	}

}
