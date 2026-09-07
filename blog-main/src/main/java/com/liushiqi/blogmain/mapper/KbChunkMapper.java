package com.liushiqi.blogmain.mapper;

import com.liushiqi.blogmain.entity.KbChunk;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * RAG 知识库分块数据访问层
 * <p>
 * kb_chunk 表为向量索引的事实源：分块原文、SHA-256 摘要与向量字节均在此持久化，
 * 支撑 Redis 索引丢失后零 token 重建、content_hash 增量索引，以及未来迁移外部向量库重灌。
 */
@Mapper
public interface KbChunkMapper {

    /**
     * 幂等写入分块：依赖 uk_post_chunk(post_id, chunk_index) 唯一键，
     * 命中已存在分块时更新 content、content_hash、embedding 与 update_time，实现重复 reindex 的幂等。
     *
     * @param chunk 分块实体（需提供 postId、chunkIndex、content、contentHash、embedding、createTime、updateTime）
     * @return 受影响行数（插入为1，更新为2）
     */
    @Insert("insert into kb_chunk(post_id,chunk_index,content,content_hash,embedding,create_time,update_time) " +
            "values(#{postId},#{chunkIndex},#{content},#{contentHash},#{embedding},#{createTime},#{updateTime}) " +
            "on duplicate key update content=values(content),content_hash=values(content_hash)," +
            "embedding=values(embedding),update_time=values(update_time)")
    int upsert(KbChunk chunk);

    /**
     * 删除指定文章的全部分块（文章删除或整体重建索引前清理旧分块）
     *
     * @param postId 文章ID
     * @return 删除行数
     */
    @Delete("delete from kb_chunk where post_id = #{postId}")
    int deleteByPostId(Long postId);

    /**
     * 查询指定文章的全部分块（含 content、content_hash、embedding），按分块序号升序
     *
     * @param postId 文章ID
     * @return 分块列表
     */
    @Select("select id,post_id,chunk_index,content,content_hash,embedding,create_time,update_time " +
            "from kb_chunk where post_id = #{postId} order by chunk_index")
    List<KbChunk> selectByPostId(Long postId);

    /**
     * 按分块ID批量取分块内容（供问答阶段拼接 Prompt 上下文），使用 foreach 展开 IN 查询
     *
     * @param ids 分块ID列表
     * @return 命中分块列表（仅取 id、post_id、chunk_index、content）
     */
    @Select("<script>" +
            "select id,post_id,chunk_index,content from kb_chunk where id in " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</script>")
    List<KbChunk> selectContentByIds(List<Long> ids);

    /**
     * 取全部分块的 id、post_id、embedding（不取 content 以减少数据传输），
     * 供 Redis 向量索引丢失后零 token 重建
     *
     * @return 仅含向量重建所需字段的分块列表
     */
    @Select("select id,post_id,embedding from kb_chunk")
    List<KbChunk> selectAllForRebuild();

    /**
     * 统计分块总数
     *
     * @return 分块总条数
     */
    @Select("select count(*) from kb_chunk")
    long countAll();
}
