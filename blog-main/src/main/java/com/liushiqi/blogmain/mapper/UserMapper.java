package com.liushiqi.blogmain.mapper;

import com.liushiqi.blogmain.entity.Users;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper {

    @Select("select id, username, password, role from users where username=#{username}")
    Users findByUsername(String username);

    @Select("select count(*) > 0 from users where username=#{username}")
    boolean existsByUsername(String username);

    @Select("select count(*) > 0 from users where email=#{email}")
    boolean existsByEmail(String email);

    void insert(Users user);

    @Select("select username, role, email from users where id=#{id}")
    Users findById(Long id);
}