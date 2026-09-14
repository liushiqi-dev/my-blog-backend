package com.liushiqi.blogmain.vo;

import lombok.Data;

import java.util.List;

/**
 * 管理后台统计看板的聚合结果，一次返回全部指标。
 * <p>
 * 各字段来自互相独立的聚合查询，彼此无依赖关系，因此可以在服务层并行获取后组装。
 */
@Data
public class DashboardVo {

    private Long totalPosts;

    private Long publishedPosts;

    private Long draftPosts;

    private Long recentPosts;

    private Long totalViews;

    private Long totalLikes;

    private Long totalWords;

    private List<CategoryVo> categories;

    private List<PageVo> hotPosts;
}
