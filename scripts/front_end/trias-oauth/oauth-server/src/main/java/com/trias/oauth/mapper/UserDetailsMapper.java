package com.trias.oauth.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.trias.oauth.model.vo.LocalUserRoleVO;

public interface UserDetailsMapper {

	List<LocalUserRoleVO> findUserByName(@Param("userName") String userName);
}
