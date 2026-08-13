package com.liushiqi.blogmain.mapper;

import com.liushiqi.blogmain.dto.request.PostRequest;
import com.liushiqi.blogmain.vo.PageVo;
import com.liushiqi.blogmain.vo.PostVo;
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
            "       ,p.status,p.create_time,p.update_time" +
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
}
