package com.liushiqi.blogmain.Mapper;

import com.liushiqi.blogmain.entity.Users;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper {

    // 登录
    @Select("select id,username from users where username=#{username} and password=#{password}")
    Users getByUsernameAndPassword(Users users);

    // 注册
    @Select("select count(*) > 0 from users where username=#{username}")
    boolean existsByUsername(String username);

    void insert(Users user);


}
