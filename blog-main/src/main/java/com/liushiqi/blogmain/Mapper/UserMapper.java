package com.liushiqi.blogmain.Mapper;

import com.liushiqi.blogmain.entity.Users;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper {

    // 根据用户名查询用户（用于登录验证）
    @Select("select username, password from users where username=#{username}")
    Users findByUsername(String username);

    // 注册
    @Select("select count(*) > 0 from users where username=#{username}")
    boolean existsByUsername(String username);
    void insert(Users user);

    // 根据id查询用户（用于获取用户信息）
    @Select("select id, username, role, email from users where id=#{id}")
    Users findById(Long id);
}
