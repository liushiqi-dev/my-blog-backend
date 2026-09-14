package com.liushiqi.blogmain.controller;

import com.liushiqi.blogmain.common.result.Result;
import com.liushiqi.blogmain.service.DashboardService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/admin")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /**
     * 全站统计指标聚合，仅管理员可访问
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/dashboard")
    public Result dashboard() {
        return Result.success(dashboardService.getStatistics());
    }
}
