package com.liushiqi.blogmain.mapper;

import com.liushiqi.blogmain.entity.Posts;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文章数据访问层
 */
@Mapper
public interface PostMapper {
    /**
     * 插入文章
     */
    @Insert("insert into posts (title,summary,content,is_deleted,author_id) " +
            "values (#{title},#{summary},#{content},#{isDeleted},#{authorId})")
    void insert(Posts posts);
}