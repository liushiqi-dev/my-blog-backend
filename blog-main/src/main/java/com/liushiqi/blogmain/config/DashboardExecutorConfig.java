package com.liushiqi.blogmain.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 统计看板并行聚合的线程池装配。
 * <p>
 * 看板的九项指标来自互相独立的聚合查询，串行执行时总耗时等于各项之和；本池让九项并发跑，
 * 总耗时收敛到最长单项。任务是阻塞式的数据库 IO，线程大部分时间在等 MySQL 返回，本地 CPU 空闲。
 */
@Configuration
public class DashboardExecutorConfig {

    /**
     * 看板专用线程池。
     * <p>
     * 参数取值依据（基于 2 万篇文章规模下的实测）：九项查询总耗时约 91ms，其中最长的
     * sum(char_length(content)) 单项约 45ms、占 48%。并行完成时间的下限就是这一项，
     * 按最长任务优先的调度顺序，3 个线程即可触底，第 4 个用于吸收并发请求，再多无收益。
     * <p>
     * 上限还受 Hikari 连接池约束：默认最多 10 条连接，每个并行任务执行期间独占一条，
     * 4 个线程意味着单次看板请求最多占用 4 条，剩余 6 条留给面向用户的接口。
     * 若把线程数放大到 8，两个管理员同时刷新看板就能吃掉 8 条连接，首页文章列表会开始排队等连接。
     *
     * @param coreSize      核心线程数，同时作为最大线程数
     * @param queueCapacity 有界队列容量
     * @return 已配好并发上限、拒绝策略与优雅关闭的线程池
     */
    @Bean("dashboardExecutor")
    public ThreadPoolTaskExecutor dashboardExecutor(
            @Value("${dashboard.executor.core-size:4}") int coreSize,
            @Value("${dashboard.executor.queue-capacity:8}") int queueCapacity) {

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // core 与 max 取同值：这里要的是硬性并发上限，不是弹性伸缩。
        // 任务提交顺序是「核心线程 → 队列 → 扩容到 max → 拒绝」，core=max 意味着跳过扩容这一步，
        // 队列满了直接走拒绝策略，行为可预测。
        executor.setCorePoolSize(coreSize);
        executor.setMaxPoolSize(coreSize);

        // 队列必须有界。无界队列会让 maxPoolSize 永远不生效，且任务无限堆积最终耗尽堆内存。
        // 容量取 8 是因为单次请求提交 9 个任务，4 个被线程接走后剩 5 个入队，8 留了余量。
        executor.setQueueCapacity(queueCapacity);

        // 超出 core 的线程空闲多久后回收。core=max 时此参数实际不生效，保留是为了将来放开 max 时行为正确。
        executor.setKeepAliveSeconds(60);

        // 线程命名：日志与线程 dump 里会显示 dashboard-1 / dashboard-2，
        // 不设的话全是 pool-1-thread-3，无法判断任务到底跑在哪个池里。
        executor.setThreadNamePrefix("dashboard-");

        // 拒绝策略选 CallerRuns：队列满且线程满时，让提交任务的线程自己执行这一项。
        // 看板任务不可丢弃（丢了那张卡片就是空的），且调用者是 Tomcat 请求线程，
        // 被拉去执行一条几十毫秒的查询代价可接受，同时天然形成背压拖慢提交速度。
        // 注意：若任务本身是秒级的，这个策略会把用户请求线程长时间占住，那时就不能用它。
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        // 优雅关闭：应用停机时等在途任务跑完，最多等 30 秒。
        // 不配的话停机瞬间正在执行的查询被硬掐，接口返回半成品数据。
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);

        // 不调用 executor.initialize()：ThreadPoolTaskExecutor 实现了 InitializingBean，
        // 容器会自动触发 afterPropertiesSet 完成初始化。手工再调一次会创建第二个线程池并泄漏第一个。
        return executor;
    }
}
