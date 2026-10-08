package com.guangying.provider;

import com.guangying.common.constants.CacheConstants;
import com.guangying.service.cache.MultiLevelCacheService;
import com.guangying.service.infrastructure.DistributedLockService;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MultiLevelCacheServiceTest {

    @Test
    void checksRedisAgainAfterAcquiringRebuildLock() {
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        ValueOperations<String, Object> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        // 首次 L2、单机 single-flight 二次检查均未命中；拿到跨实例锁后由对端完成重建。
        when(valueOperations.get("movie:hot")).thenReturn(null, null, "rebuilt-by-peer");

        ExecutingLockService lockService = new ExecutingLockService();
        MultiLevelCacheService cacheService = newCacheService(redisTemplate, lockService);
        AtomicInteger databaseLoads = new AtomicInteger();

        String value = cacheService.get("movie:hot", () -> {
            databaseLoads.incrementAndGet();
            return "from-database";
        });

        assertEquals("rebuilt-by-peer", value);
        assertEquals(0, databaseLoads.get());
        assertEquals("cache:rebuild:movie:hot", lockService.lockKey);
        assertEquals(CacheConstants.CACHE_REBUILD_LOCK_WAIT_SECONDS, lockService.waitSeconds);
    }

    @Test
    void fallsBackToDatabaseWhenRebuildLockTimesOutAndCacheIsStillEmpty() {
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        ValueOperations<String, Object> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("movie:coming")).thenReturn(null);

        MultiLevelCacheService cacheService = newCacheService(redisTemplate, new TimeoutLockService());
        AtomicInteger databaseLoads = new AtomicInteger();

        String value = cacheService.get("movie:coming", () -> {
            databaseLoads.incrementAndGet();
            return "available-result";
        });

        assertEquals("available-result", value);
        assertEquals(1, databaseLoads.get());
        verify(valueOperations).set(eq("movie:coming"), eq("available-result"), anyLong(), eq(java.util.concurrent.TimeUnit.MINUTES));
    }

    @Test
    void cachesNullValueSoMissingIdDoesNotRepeatedlyReachDatabase() {
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        ValueOperations<String, Object> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("movie:detail:404")).thenReturn(null);

        MultiLevelCacheService cacheService = newCacheService(redisTemplate, new ExecutingLockService());
        AtomicInteger databaseLoads = new AtomicInteger();
        Supplier<String> missingLoader = () -> {
            databaseLoads.incrementAndGet();
            return null;
        };

        assertNull(cacheService.get("movie:detail:404", missingLoader));
        assertNull(cacheService.get("movie:detail:404", missingLoader));

        assertEquals(1, databaseLoads.get());
        verify(valueOperations).set(
                eq("movie:detail:404"),
                eq("__GUANGYING_INTERNAL_NULL_CACHE_VALUE__"),
                eq(CacheConstants.CACHE_NULL_EXPIRE_SECONDS),
                eq(java.util.concurrent.TimeUnit.SECONDS));
    }

    @Test
    void pubSubInvalidationRemovesOnlyTheLocalL1Entry() {
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        ValueOperations<String, Object> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("movie:detail:1")).thenReturn(null);

        MultiLevelCacheService cacheService = newCacheService(redisTemplate, new ExecutingLockService());
        AtomicInteger databaseLoads = new AtomicInteger();
        Supplier<String> loader = () -> "version-" + databaseLoads.incrementAndGet();

        assertEquals("version-1", cacheService.get("movie:detail:1", loader));
        assertEquals("version-1", cacheService.get("movie:detail:1", loader));

        cacheService.onInvalidationMessage("KEY:movie:detail:1");

        assertEquals("version-2", cacheService.get("movie:detail:1", loader));
        assertEquals(2, databaseLoads.get());
    }

    @Test
    void localSingleFlightCollapsesConcurrentLoadsWhenDistributedLockIsUnavailable() throws Exception {
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        ValueOperations<String, Object> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("movie:hot:single-flight")).thenReturn(null);

        MultiLevelCacheService cacheService = newCacheService(redisTemplate, null);
        AtomicInteger databaseLoads = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Future<String>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < 8; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return cacheService.get("movie:hot:single-flight", () -> {
                        databaseLoads.incrementAndGet();
                        try {
                            Thread.sleep(40);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        return "one-result";
                    });
                }));
            }
            start.countDown();
            for (Future<String> future : futures) {
                assertEquals("one-result", future.get());
            }
        } finally {
            pool.shutdownNow();
        }

        assertEquals(1, databaseLoads.get());
    }

    @SuppressWarnings("unchecked")
    private MultiLevelCacheService newCacheService(
            RedisTemplate<String, Object> redisTemplate,
            DistributedLockService lockService) {
        MultiLevelCacheService service = new MultiLevelCacheService();
        ReflectionTestUtils.setField(service, "cacheEnabled", true);
        ReflectionTestUtils.setField(service, "redisTemplate", redisTemplate);
        ReflectionTestUtils.setField(service, "distributedLockService", lockService);
        return service;
    }

    private static final class ExecutingLockService extends DistributedLockService {
        private String lockKey;
        private long waitSeconds;

        @Override
        public <T> T executeWithWatchdogLock(String lockKey, long waitTime, Supplier<T> task) {
            this.lockKey = lockKey;
            this.waitSeconds = waitTime;
            return task.get();
        }
    }

    private static final class TimeoutLockService extends DistributedLockService {
        @Override
        public <T> T executeWithWatchdogLock(String lockKey, long waitTime, Supplier<T> task) {
            return null;
        }
    }
}
