package com.liushiqi.blogmain.service;

import com.liushiqi.blogmain.vo.DashboardVo;

/**
 * 管理后台统计看板业务接口
 */
public interface DashboardService {

    /**
     * 聚合全站统计指标：文章数量与状态分布、浏览量、点赞数、正文字数、分类分布、近期新增、热门文章。
     *
     * @return 一次性返回全部指标的看板数据
     */
    DashboardVo getStatistics();
}
