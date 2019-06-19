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
import com.trias.oauth.service.OauthUserService;

@Service
public class OauthUserServiceImpl implements OauthUserService {
	private static final String NOOP = "{noop}";

	@Autowired
	private UserDetailsMapper userDetailsMapper;
	
	@Override
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		LocalUserRoleVO userVo = userDetailsMapper.findUserByName(username);
		if(userVo == null) {
			throw new UsernameNotFoundException(username);
		}else {
			List<GrantedAuthority> grantedAuthorityList = new ArrayList<>();
			grantedAuthorityList.add(new SimpleGrantedAuthority(userVo.getRoleName()==null?"ROLE_USER":userVo.getRoleName()));
			userVo.setUsername(username);
			userVo.setPassword(NOOP+userVo.getPassword());
			return new CustomUserDetails(userVo, grantedAuthorityList);
		}
		
	}
}
