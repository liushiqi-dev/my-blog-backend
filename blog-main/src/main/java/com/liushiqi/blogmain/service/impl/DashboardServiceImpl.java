package com.liushiqi.blogmain.service.impl;

import com.liushiqi.blogmain.mapper.CategoryMapper;
import com.liushiqi.blogmain.mapper.PostMapper;
import com.liushiqi.blogmain.service.DashboardService;
import com.liushiqi.blogmain.vo.CategoryVo;
import com.liushiqi.blogmain.vo.DashboardVo;
import com.liushiqi.blogmain.vo.PageVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 管理后台统计看板业务实现。
 * <p>
 * 九项指标来自互相独立的聚合查询，任意一项都不依赖另一项的结果，因此提交到 dashboardExecutor 并发执行，
 * 总耗时从「各项之和」收敛到「最长单项」。实测 2 万篇文章规模下串行约 91ms，其中
 * sum(char_length(content)) 单项约 45ms 占 48%，它决定了并行耗时的下限。
 * <p>
 * 不加 {@code @Transactional}：九项都是只读查询，无需事务；且事务上下文绑定在提交任务的线程上，
 * 池内工作线程拿不到它，并行任务本来就无法共享同一个事务。
 */
@Slf4j
@Service
public class DashboardServiceImpl implements DashboardService {

    /** 近期新增的统计窗口天数 */
    private static final int RECENT_DAYS = 7;

    /** 热门文章返回条数上限 */
    private static final int HOT_LIMIT = 10;

    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final String STATUS_DRAFT = "DRAFT";

    /**
     * 单项查询的超时上限。正常最长单项约 45ms，2 秒是数量级以上的余量，
     * 只在数据库卡死或连接池耗尽时才会触发。
     */
    private static final long TASK_TIMEOUT_SECONDS = 2;

    private final PostMapper postMapper;
    private final CategoryMapper categoryMapper;

    /**
     * 看板专用线程池。必须用 {@code @Qualifier} 指名注入：容器里 Executor 类型的 Bean 不止一个
     * （Spring 还会为 @Scheduled 创建 taskScheduler），按类型注入会因歧义启动失败。
     */
    private final ThreadPoolTaskExecutor dashboardExecutor;

    public DashboardServiceImpl(PostMapper postMapper,
                                CategoryMapper categoryMapper,
                                @Qualifier("dashboardExecutor") ThreadPoolTaskExecutor dashboardExecutor) {
        this.postMapper = postMapper;
        this.categoryMapper = categoryMapper;
        this.dashboardExecutor = dashboardExecutor;
    }

    @Override
    public DashboardVo getStatistics() {
        // 各任务耗时的收集容器。必须是方法内的局部变量：若做成成员字段，多个请求会往同一个 Map 里写，
        // 互相污染计时数据。用 ConcurrentHashMap 是因为九个任务在不同线程上并发写入。
        Map<String, Long> costs = new ConcurrentHashMap<>();
        long start = System.nanoTime();

        // 提交顺序按实测耗时降序排列（LPT，最长任务优先）。
        // 工作线程是从 FIFO 队列按序取任务的，长任务越晚提交就越晚开始，完成时间 = 它的开始时刻 + 它自身耗时。
        // 实测对比：按代码书写顺序提交，4 线程完成时间 48.7ms；按此降序提交，3 线程即可触底 45.0ms。
        CompletableFuture<Long> totalWords =
                supply("全站字数", postMapper::sumContentLength, 0L, costs);
        CompletableFuture<Long> totalViews =
                supply("总浏览量", postMapper::sumViewCount, 0L, costs);
        CompletableFuture<List<CategoryVo>> categories =
                supply("分类分布", categoryMapper::findAll, List.of(), costs);
        CompletableFuture<Long> draftPosts =
                supply("草稿数", () -> toLong(postMapper.getTotal(STATUS_DRAFT)), 0L, costs);
        CompletableFuture<Long> totalLikes =
                supply("点赞总数", postMapper::countLikes, 0L, costs);
        CompletableFuture<Long> totalPosts =
                supply("文章总数", () -> toLong(postMapper.getTotal(null)), 0L, costs);
        CompletableFuture<List<PageVo>> hotPosts =
                supply("热门Top10", () -> postMapper.findHot(HOT_LIMIT), List.of(), costs);
        CompletableFuture<Long> recentPosts =
                supply("近7天新增", () -> postMapper.countRecentPosts(RECENT_DAYS), 0L, costs);
        CompletableFuture<Long> publishedPosts =
                supply("已发布数", () -> toLong(postMapper.getTotal(STATUS_PUBLISHED)), 0L, costs);

        // 阻塞等待全部完成。少了这一行方法会在任务还没跑完时就返回，组装出的 VO 全是默认值。
        // 异常已在 supply() 内部消化、超时已由 completeOnTimeout 兜底，所以这里的 join() 不会抛异常。
        CompletableFuture.allOf(totalWords, totalViews, categories, draftPosts, totalLikes,
                totalPosts, hotPosts, recentPosts, publishedPosts).join();

        // allOf 已保证九个 future 全部完成，下面这些 join() 都是立即返回，不会阻塞
        DashboardVo vo = new DashboardVo();
        vo.setTotalWords(totalWords.join());
        vo.setTotalViews(totalViews.join());
        vo.setCategories(categories.join());
        vo.setDraftPosts(draftPosts.join());
        vo.setTotalLikes(totalLikes.join());
        vo.setTotalPosts(totalPosts.join());
        vo.setHotPosts(hotPosts.join());
        vo.setRecentPosts(recentPosts.join());
        vo.setPublishedPosts(publishedPosts.join());

        // nanoTime 是单调递增的相对时间，专用于测量间隔；currentTimeMillis 取墙上时钟，
        // 会受 NTP 校时影响（可能往回跳），且精度只有 10ms 量级，测几十毫秒的耗时误差过大。
        log.info("看板并行总耗时 {} ms，各任务耗时(ms) {}", (System.nanoTime() - start) / 1_000_000, costs);
        return vo;
    }

    /**
     * 统一的子任务提交入口：绑定执行器、就地消化异常、记录单项耗时、附加超时兜底。
     * <p>
     * 执行器必须显式传入 supplyAsync 的第二个参数。单参数版用的是 ForkJoinPool.commonPool()：
     * 它是 JVM 全局共享的，并行度默认只有 CPU 核数-1，被所有 parallelStream 与未指定执行器的
     * CompletableFuture 共用，阻塞式数据库查询会把它占满并饿死其他使用方；它是 daemon 线程，
     * 应用关闭时不等任务完成；配置类里设的队列容量、拒绝策略、线程名也全部失效。
     *
     * @param name     任务名，用于日志与耗时统计的键
     * @param task     实际查询逻辑
     * @param fallback 该任务失败或超时时的降级值，保证单项异常不会让整个看板 500
     * @param costs    耗时收集容器，由调用方每次请求新建
     * @param <T>      任务返回值类型
     * @return 已附加降级与超时保护的 future
     */
    private <T> CompletableFuture<T> supply(String name, Supplier<T> task, T fallback, Map<String, Long> costs) {
        return CompletableFuture.supplyAsync(() -> {
            long taskStart = System.nanoTime();
            try {
                return task.get();
            } catch (Exception e) {
                // 降级容错不抛出异常
                log.error("看板子任务[{}]执行失败，降级为默认值", name, e);
                return fallback;
            } finally {
                costs.put(name, (System.nanoTime() - taskStart) / 1_000_000);
            }
        }, dashboardExecutor)
                // 超时兜底：超过上限就用 fallback 提前完成 future，避免一条卡死的查询拖垮整个接口。
                // 注意它只是让「调用方」不再等，并不会取消或中断底层任务——查询仍在池线程上跑到自然结束，
                // 期间继续占用一条数据库连接。要真正中断需要 JDBC 层支持，而驱动对 interrupt 的响应并不可靠。
                // 副作用：被超时兜底的任务，其耗时会在这行日志打印之后才写进 costs，所以那次统计里看不到它。
                .completeOnTimeout(fallback, TASK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * getTotal 声明返回 Integer，统一转成 Long 以保持看板各计数字段类型一致；
     * count(*) 实际不会返回 null，此处判空只为与声明类型对齐。
     */
    private static Long toLong(Integer value) {
        return value == null ? 0L : value.longValue();
    }
}
