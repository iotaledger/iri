package com.trias.oauth.mapper;

import org.apache.ibatis.annotations.Param;

import com.trias.oauth.model.vo.LocalUserRoleVO;

public interface UserDetailsMapper {

	LocalUserRoleVO findUserByName(@Param("username") String userName);

	void saveUser(@Param("username")String username, @Param("password")String password);
}
