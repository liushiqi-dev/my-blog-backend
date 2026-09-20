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
    @Update("update posts set title=#{title},summary=#{summary},content=#{content},status=#{status},version=version+1 where id=#{id} and version=#{version}")
    int update(PostRequest req);

    /**
     * 获取文章详情
     * @param id 文章ID
     * @return 文章VO
     */
    @Select("select p.id,p.title,p.summary,p.content,users.username authorName," +
            "       group_concat(categories.name) categoryNames" +
            "       ,p.status,p.create_time,p.update_time,p.view_count,p.like_count,p.version" +
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
     * 查询用户是否点赞过文章（Redis 故障降级时兜底）
     * @return 大于0表示已点赞
     */
    @Select("select count(*) from post_likes where post_id = #{postId} and user_id = #{userId}")
    int countLike(Long postId, Long userId);

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

    /**
     * 全站文章浏览量总和。
     * <p>
     * SUM 在无匹配行时返回 NULL，用 ifnull 兜底为 0，避免映射到 Long 后在调用侧拆箱报空指针。
     */
    @Select("select ifnull(sum(view_count), 0) from posts where is_deleted = 0")
    Long sumViewCount();

    /**
     * 全站点赞总数。
     * <p>
     * 统计 post_likes 明细行数，而不用 posts.like_count 冗余计数列：明细表是事实源，
     * 冗余列由异步链路维护，两者可能短暂不一致。
     */
    @Select("select count(*) from post_likes")
    Long countLikes();

    /**
     * 统计最近 days 天内新增的文章数。
     * <p>
     * 时间下界用 date_sub(now(), interval #{days} day) 在数据库侧算：create_time 由 MySQL 的
     * CURRENT_TIMESTAMP 生成，与 now() 同源同时区，比较不会因应用与数据库时区不一致而漂移。
     * <p>
     * 函数只作用在常量侧，create_time 列本身裸露在比较运算符左边，仍可走索引；
     * 若写成 date(create_time) > 'xxxx-xx-xx' 则是对列使用函数，会导致索引失效转全表扇描。
     *
     * @param days 回溯天数，由调用侧传入（看板固定传 7）
     */
    @Select("select count(*) from posts" +
            " where is_deleted = 0 and create_time > date_sub(now(), interval #{days} day)")
    Long countRecentPosts(int days);

    /**
     * 按浏览量倒序取已发布的热门文章，用于看板 Top N。
     * <p>
     * 与 findPage 分开实现：findPage 按 create_time 倒序服务于首页文章列表，
     * 改它的 order by 会直接变更首页的现有行为。
     * <p>
     * 复用 PageVo 承接结果，不 join users 与不取 summary，故 authorName / summary 为 null。
     *
     * @param limit 返回条数上限
     */
    @Select("select p.id, p.title, p.status, p.view_count, p.create_time" +
            " from posts p" +
            " where p.is_deleted = 0 and p.status = 'PUBLISHED'" +
            " order by p.view_count desc limit #{limit}")
    List<PageVo> findHot(int limit);

    /**
     * 全站正文字符数总和。
     * <p>
     * 由数据库用 char_length 聚合完成，不把 content 拉到应用内存再累加；
     * char_length 算字符数，length 算字节数，中文场景下两者差三倍，此处要的是前者。
     */
    @Select("select ifnull(sum(char_length(content)), 0) from posts where is_deleted = 0")
    Long sumContentLength();
}
