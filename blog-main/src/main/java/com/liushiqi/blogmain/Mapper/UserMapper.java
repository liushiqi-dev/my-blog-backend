package com.liushiqi.blogmain.Mapper;

import com.liushiqi.blogmain.entity.Users;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper {

    // 登录
    @Select("select id,username from users where username=#{username} and password=#{password}")
    Users login(Users users);
}
