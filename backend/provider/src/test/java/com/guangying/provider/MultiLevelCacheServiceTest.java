package com.guangying.provider;

import com.guangying.common.constants.CacheConstants;
import com.guangying.service.cache.MultiLevelCacheService;
import com.guangying.service.infrastructure.DistributedLockService;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.atomic.AtomicInteger;
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
        when(valueOperations.get("movie:hot")).thenReturn(null, "rebuilt-by-peer");

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
