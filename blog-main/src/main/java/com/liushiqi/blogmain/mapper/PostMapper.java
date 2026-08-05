package com.liushiqi.blogmain.mapper;

import com.liushiqi.blogmain.entity.Posts;
import com.liushiqi.blogmain.vo.PostVo;
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
    void insert(Posts posts);

    /**
     * 修改文章
     */
    @Update("update posts set title=#{title},summary=#{summary},content=#{content} where id=#{id}")
    void update(Posts posts);

    /**
     * 获取文章详情
     * @param id 文章ID
     * @return 文章VO
     */
    @Select("select p.title,p.summary,p.content,users.username authorName,p.create_time,p.update_time " +
            "from posts p inner JOIN users on users.id=p.author_id " +
            "where is_deleted='0' and status='PUBLISHED' and p.id=#{id}")
    PostVo selectAll(Long id);

    @Select("select title,summary,content,create_time,update_time from posts where is_deleted='0' and id=#{id}")
    PostVo ceshi(Long id);
   }
