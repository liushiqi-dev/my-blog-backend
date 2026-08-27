package com.liushiqi.blogmain.mapper;

import com.liushiqi.blogmain.dto.request.PostRequest;
import com.liushiqi.blogmain.vo.PageVo;
import com.liushiqi.blogmain.vo.PostVo;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 文章数据访问层
 */
@Mapper
public interface PostMapper {

    Integer countCategoriesByIds(List<Integer> categoryIds);
    /**
     * 插入文章 插入成功后将文章ID赋值给posts id
     */
    void insert(Long authorId, PostRequest req);

    void insertCategories(Long postId, List<Integer> categoryIds);


    void deleteCategoriesByPostId(Long id);
    /**
     * 文章详情
     */
    @Update("update posts set title=#{title},summary=#{summary},content=#{content},status=#{status} where id=#{id}")
    void update(PostRequest req);

    /**
     * 获取文章详情
     * @param id 文章ID
     * @return 文章VO
     */
    @Select("select p.id,p.title,p.summary,p.content,users.username authorName," +
            "       group_concat(categories.name) categoryNames" +
            "       ,p.status,p.create_time,p.update_time,p.view_count,p.like_count" +
            "            from posts p" +
            "            inner join users on users.id=p.author_id" +
            "            inner join post_categories on p.id = post_categories.post_id" +
            "            inner join categories on post_categories.category_id=categories.id" +
            "            where is_deleted='0' and p.id=#{p.id}" +
            "            group by p.id,users.id")
    PostVo findById(Long id);

    int deleteById(Long id);


    Integer getTotal(String status);

    List<PageVo> findPage(Integer page, Integer size, String status);

    /**
     * 插入点赞记录（insert ignore：记录已存在时忽略并返回0）
     */
    @Insert("insert ignore into post_likes (post_id, user_id) values (#{postId}, #{userId})")
    int insertLike(Long postId, Long userId);

    /**
     * 删除点赞记录（记录不存在时返回0）
     */
    @Delete("delete from post_likes where post_id = #{postId} and user_id = #{userId}")
    int deleteLike(Long postId, Long userId);

    /**
     * 更新文章点赞数（delta为增量：点赞+1 取消-1）
     */
    @Update("update posts set like_count = like_count + #{delta} where id = #{postId}")
    void updateLikeCount(Long postId, int delta);

    /**
     * 更新文章浏览量（delta为定时任务从Redis取回的累计增量）
     */
    @Update("update posts set view_count = view_count + #{delta} where id = #{postId}")
    void updateViewCount(Long postId, int delta);
}
