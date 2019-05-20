package com.trias.oauth.service.impl;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import com.trias.oauth.mapper.UserDetailsMapper;
import com.trias.oauth.model.CustomUserDetails;
import com.trias.oauth.model.vo.LocalUserRoleVO;
import com.trias.oauth.service.TriasUserDetailsService;

@Service
public class TriasUserDetailsServiceImpl implements TriasUserDetailsService {
	private static final String NOOP = "{noop}";

	@Autowired
	private UserDetailsMapper userDetailsMapper;
	
	@Override
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		List<LocalUserRoleVO> userList = userDetailsMapper.findUserByName(username);
		if(CollectionUtils.isEmpty(userList)) {
			throw new UsernameNotFoundException(username);
		}else {
			LocalUserRoleVO user = userList.get(0);
			List<GrantedAuthority> grantedAuthorityList = new ArrayList<>();
			grantedAuthorityList.add(new SimpleGrantedAuthority(user.getRoleName()));
			user.setUsername(username);
			user.setPassword(NOOP+user.getPassword());
			return new CustomUserDetails(user, grantedAuthorityList);
		}
		
	}
}
