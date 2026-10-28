package com.liushiqi.blogmain.service.impl;

import com.liushiqi.blogmain.common.result.PageResult;
import com.liushiqi.blogmain.common.util.RedisUtils;
import com.liushiqi.blogmain.mapper.PostMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 缓存与数据库使用 Mock，不启动 Spring 或连接外部服务。 */
class PostServiceImplCacheTest {

    private static final String KEY = "posts:PUBLISHED:1:10";
    private final PostServiceImpl service = new PostServiceImpl();
    private final PostMapper mapper = mock(PostMapper.class);
    private final RedisUtils redisUtils = mock(RedisUtils.class);
    @SuppressWarnings("unchecked")
    private final RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, Object> values = mock(ValueOperations.class);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "postMapper", mapper);
        ReflectionTestUtils.setField(service, "redisTemplate", redis);
        ReflectionTestUtils.setField(service, "redisUtils", redisUtils);
        when(redis.opsForValue()).thenReturn(values);
    }

    @Test
    void cacheHitDoesNotQueryDatabase() {
        PageResult<?> cached = new PageResult<>(List.of(), 0, 1, 10);
        when(values.get(KEY)).thenReturn(cached);

        assertSame(cached, service.listPosts(1, 10, null));

        verifyNoInteractions(mapper, redisUtils);
    }

    @Test
    void concurrentCacheMissesRebuildOnlyOnce() throws Exception {
        int requests = 20;
        CountDownLatch initialReads = new CountDownLatch(requests);
        AtomicInteger reads = new AtomicInteger();
        AtomicReference<Object> cache = new AtomicReference<>();
        when(values.get(KEY)).thenAnswer(invocation -> {
            if (reads.incrementAndGet() <= requests) {
                // 确保所有请求第一次读取均未命中，再允许任何请求进入回源过程。
                initialReads.countDown();
                assertTrue(initialReads.await(5, TimeUnit.SECONDS));
                return null;
            }
            return cache.get();
        });
        when(mapper.findPage(0, 10, "PUBLISHED")).thenReturn(List.of());
        when(mapper.getTotal("PUBLISHED")).thenReturn(0);
        doAnswer(invocation -> {
            cache.set(invocation.getArgument(1));
            return null;
        }).when(redisUtils).setWithRandomTtl(eq(KEY), any(), eq(30L), eq(5L), eq(TimeUnit.MINUTES));

        ExecutorService executor = Executors.newFixedThreadPool(requests);
        try {
            List<Future<PageResult>> results = new ArrayList<>();
            for (int i = 0; i < requests; i++) {
                results.add(executor.submit(() -> service.listPosts(1, 10, null)));
            }
            for (Future<PageResult> result : results) {
                assertSame(result.get(10, TimeUnit.SECONDS), cache.get());
            }
            verify(mapper, times(1)).findPage(0, 10, "PUBLISHED");
            verify(mapper, times(1)).getTotal("PUBLISHED");
            verify(redisUtils, times(1)).setWithRandomTtl(
                    eq(KEY), any(), eq(30L), eq(5L), eq(TimeUnit.MINUTES));
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void databaseFailureReleasesLockForAnotherThread() throws Exception {
        when(values.get(KEY)).thenReturn(null);
        when(mapper.findPage(0, 10, "PUBLISHED"))
                .thenThrow(new IllegalStateException("database unavailable"))
                .thenReturn(List.of());
        when(mapper.getTotal("PUBLISHED")).thenReturn(0);

        assertThrows(IllegalStateException.class, () -> service.listPosts(1, 10, null));

        // 使用另一线程，避免可重入特性掩盖锁未释放的问题。
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            PageResult result = executor.submit(() -> service.listPosts(1, 10, null))
                    .get(5, TimeUnit.SECONDS);
            assertEquals(0, result.getTotal());
            verify(mapper, times(2)).findPage(0, 10, "PUBLISHED");
            verify(redisUtils).setWithRandomTtl(
                    eq(KEY), any(), eq(30L), eq(5L), eq(TimeUnit.MINUTES));
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }
}
