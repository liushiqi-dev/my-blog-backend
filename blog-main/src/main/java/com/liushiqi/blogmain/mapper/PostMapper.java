package com.liushiqi.blogmain.mapper;

import com.liushiqi.blogmain.entity.Posts;
import com.liushiqi.blogmain.vo.PostVo;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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

    /**
     * 修改文章
     */
    @Update("update posts set title=#{title},summary=#{summary},content=#{content} where id=#{id}")
    void update(Posts posts);

    @Select("select title,summary,content,create_time,update_time from posts where is_deleted!='1'")
    PostVo selectAll();

}