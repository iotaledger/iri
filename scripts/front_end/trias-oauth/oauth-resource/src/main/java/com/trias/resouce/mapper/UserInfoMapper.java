package com.trias.resouce.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.trias.resouce.model.Resource;
import com.trias.resouce.model.User;

public interface UserInfoMapper {

	List<Resource> getResourceByUserRole(@Param("roleList") List<String> roleList);

	User getUserByName(@Param("username") String username);

	void insertUser(@Param("user")User user);

	void updateUserByName(@Param("user")User user);
}
