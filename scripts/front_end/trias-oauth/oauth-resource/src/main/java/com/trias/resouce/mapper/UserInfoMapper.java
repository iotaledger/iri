package com.trias.resouce.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.trias.resouce.model.Resource;

public interface UserInfoMapper {

	List<Resource> getResourceByUserRole(@Param("roleList")List<String> roleList);
}
