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
     * @param posts 文章内容
     */
    void insert(Posts posts);

    /**
     * 更新文章
     * @param posts 文章内容
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
    PostVo findById(Long id);

   }
