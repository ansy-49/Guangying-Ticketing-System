package com.guangying.service.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.guangying.common.constants.CacheConstants;
import com.guangying.service.infrastructure.DistributedLockService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * 多级缓存服务（L1 Caffeine + L2 Redis）
 *
 * <h3>设计要点（面试加分项）：</h3>
 * <ol>
 *   <li><b>L1（进程内 Caffeine）</b>：容量小、TTL短（60s），减少网络IO</li>
 *   <li><b>L2（Redis）</b>：容量大、TTL长（10min），跨进程共享</li>
 *   <li><b>缓存穿透防护</b>：数据库返回空值时写入短 TTL 标记，拦截不存在 Key 的重复查询</li>
 *   <li><b>缓存击穿防护</b>：Redisson Key 级分布式锁 + 双重检查，同 key 只放一个实例回源</li>
 *   <li><b>缓存雪崩防护</b>：TTL 加随机偏移，避免大批 key 同时过期</li>
 * </ol>
 *
 * <p>降级策略：当 Redis 不可用时自动退化为仅 L1 本地缓存</p>
 */
@Slf4j
@Service
public class MultiLevelCacheService {

    /** 可跨 JVM/Redis 序列化的内部空值标记；业务数据不得使用该保留值。 */
    private static final String NULL_CACHE_VALUE = "__GUANGYING_INTERNAL_NULL_CACHE_VALUE__";

    /** 压测或故障诊断时可关闭缓存，默认开启。 */
    @Value("${guangying.cache.enabled:true}")
    private boolean cacheEnabled;

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    @Autowired(required = false)
    private DistributedLockService distributedLockService;

    /** Redis/Redisson 故障时仍按 Key 合并单实例并发回源，避免本机线程同时打向数据库。 */
    private final ReentrantLock[] localRebuildLocks = createLocalRebuildLocks(64);

    /** L1 本地缓存 — 短 TTL、小容量 */
    private final Cache<String, Object> l1Cache = Caffeine.newBuilder()
            .maximumSize(500)
            .expireAfterWrite(CacheConstants.L1_EXPIRE_SECONDS, TimeUnit.SECONDS)
            .recordStats()
            .build();

    /** 空值使用独立短 TTL，避免新增数据长期被旧的“不存在”结果遮挡。 */
    private final Cache<String, Boolean> l1NullCache = Caffeine.newBuilder()
            .maximumSize(500)
            .expireAfterWrite(CacheConstants.CACHE_NULL_EXPIRE_SECONDS, TimeUnit.SECONDS)
            .build();

    /** 长期本地缓存（城市列表等低频变更数据） */
    private final Cache<String, Object> l1LongTermCache = Caffeine.newBuilder()
            .maximumSize(100)
            .expireAfterWrite(CacheConstants.CITY_EXPIRE_HOURS, TimeUnit.HOURS)
            .build();

    /**
     * 多级缓存读取 — 核心方法
     *
     * <pre>
     * 流程: L1 → L2 → Redisson Key 锁 → 双重检查 → DB loader → 回填 L2/L1
     * </pre>
     *
     * @param key    缓存 Key
     * @param loader 数据库回源函数（仅在 L1/L2 都未命中时调用）
     * @return 缓存或数据库中的值
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Supplier<T> loader) {
        if (!cacheEnabled) {
            return loader.get();
        }

        // 1. L1 查询
        Object l1Val = l1Cache.getIfPresent(key);
        if (l1Val != null) {
            log.debug("[Cache] L1 HIT: {}", key);
            return (T) l1Val;
        }
        if (l1NullCache.getIfPresent(key) != null) {
            log.debug("[Cache] L1 NULL HIT: {}", key);
            return null;
        }

        // 2. L2 查询 (Redis)
        Object l2Val = getFromL2AndFillL1(key);
        if (l2Val != null) {
            return unwrapCachedValue(l2Val);
        }

        // 3. 先在单实例内合并并发，再用 Redisson 跨实例互斥。
        ReentrantLock localLock = localRebuildLocks[Math.floorMod(key.hashCode(), localRebuildLocks.length)];
        localLock.lock();
        try {
            Object cachedAfterLocalWait = getCachedValue(key);
            if (cachedAfterLocalWait != null) {
                return unwrapCachedValue(cachedAfterLocalWait);
            }

            // 热点 Key 回源：跨实例争抢 Redisson 锁，持锁者二次检查后才能访问 DB。
            if (distributedLockService != null) {
                CacheLoadResult<T> result = distributedLockService.executeWithWatchdogLock(
                        "cache:rebuild:" + key,
                        CacheConstants.CACHE_REBUILD_LOCK_WAIT_SECONDS,
                        () -> new CacheLoadResult<>(loadAfterDoubleCheck(key, loader)));
                if (result != null) {
                    return result.value();
                }

                // 锁等待超时通常意味着其他实例正在重建；先复查缓存，避免无谓回源。
                Object cached = getCachedValue(key);
                if (cached != null) {
                    return unwrapCachedValue(cached);
                }
                log.warn("[Cache] Rebuild lock timeout for key={}, fallback to DB for availability", key);
            }

            // Redisson 不可用或等待超时且缓存仍为空时，单实例仍保持 single-flight。
            return loadAndFill(key, loader);
        } finally {
            localLock.unlock();
        }
    }

    private static ReentrantLock[] createLocalRebuildLocks(int stripes) {
        ReentrantLock[] locks = new ReentrantLock[stripes];
        for (int i = 0; i < stripes; i++) {
            locks[i] = new ReentrantLock();
        }
        return locks;
    }

    @SuppressWarnings("unchecked")
    private <T> T loadAfterDoubleCheck(String key, Supplier<T> loader) {
        Object cached = getCachedValue(key);
        if (cached != null) {
            log.debug("[Cache] HIT after acquiring rebuild lock: {}", key);
            return unwrapCachedValue(cached);
        }
        return loadAndFill(key, loader);
    }

    private Object getCachedValue(String key) {
        Object l1Val = l1Cache.getIfPresent(key);
        if (l1Val != null) {
            return l1Val;
        }
        if (l1NullCache.getIfPresent(key) != null) {
            return NULL_CACHE_VALUE;
        }
        return getFromL2AndFillL1(key);
    }

    private Object getFromL2AndFillL1(String key) {
        if (redisTemplate == null) {
            return null;
        }
        try {
            Object value = redisTemplate.opsForValue().get(key);
            if (value != null) {
                log.debug("[Cache] L2 HIT: {}", key);
                if (isNullCacheValue(value)) {
                    l1NullCache.put(key, Boolean.TRUE);
                } else {
                    l1NullCache.invalidate(key);
                    l1Cache.put(key, value);
                }
            }
            return value;
        } catch (Exception e) {
            log.warn("[Cache] L2 read error for key={}, fallback to loader: {}", key, e.getMessage());
            return null;
        }
    }

    private <T> T loadAndFill(String key, Supplier<T> loader) {
        log.debug("[Cache] MISS, loading from DB: {}", key);
        T value = loader.get();

        if (value == null) {
            l1Cache.invalidate(key);
            l1NullCache.put(key, Boolean.TRUE);
            if (redisTemplate != null) {
                try {
                    redisTemplate.opsForValue().set(
                            key, NULL_CACHE_VALUE,
                            CacheConstants.CACHE_NULL_EXPIRE_SECONDS, TimeUnit.SECONDS);
                    log.debug("[Cache] NULL CACHED: {}", key);
                } catch (Exception e) {
                    log.warn("[Cache] L2 null write error for key={}: {}", key, e.getMessage());
                }
            }
        } else {
            // 回填 L1
            l1NullCache.invalidate(key);
            l1Cache.put(key, value);
            // 回填 L2（TTL 加随机偏移防雪崩）
            if (redisTemplate != null) {
                try {
                    long ttlMinutes = CacheConstants.L2_EXPIRE_MINUTES + randomOffset();
                    redisTemplate.opsForValue().set(key, value, ttlMinutes, TimeUnit.MINUTES);
                } catch (Exception e) {
                    log.warn("[Cache] L2 write error for key={}: {}", key, e.getMessage());
                }
            }
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private <T> T unwrapCachedValue(Object value) {
        return isNullCacheValue(value) ? null : (T) value;
    }

    private boolean isNullCacheValue(Object value) {
        return NULL_CACHE_VALUE.equals(value);
    }

    /** 区分“成功持锁但业务结果为空”和“未获取到锁”。 */
    private record CacheLoadResult<T>(T value) {
    }

    /**
     * 长期缓存读取（仅 L1，适用于城市列表等极低频变更数据）
     */
    @SuppressWarnings("unchecked")
    public <T> T getLongTerm(String key, Supplier<T> loader) {
        if (!cacheEnabled) {
            return loader.get();
        }

        Object val = l1LongTermCache.getIfPresent(key);
        if (val != null) return (T) val;

        T value = loader.get();
        if (value != null) {
            l1LongTermCache.put(key, value);
        }
        return value;
    }

    /**
     * 主动失效（写操作后调用）
     */
    public void evict(String key) {
        evictLocal(key);
        if (redisTemplate != null) {
            try {
                redisTemplate.delete(key);
            } catch (Exception e) {
                log.warn("[Cache] L2 evict error for key={}: {}", key, e.getMessage());
            }
        }
        publishInvalidation("KEY:" + key);
        log.debug("[Cache] EVICTED: {}", key);
    }

    /**
     * 批量失效
     */
    public void evictByPrefix(String prefix) {
        evictLocalByPrefix(prefix);
        try {
            if (redisTemplate != null) {
                List<String> keys = redisTemplate.execute((RedisConnection connection) -> {
                    List<String> matched = new ArrayList<>();
                    try (var cursor = connection.scan(ScanOptions.scanOptions()
                            .match(prefix + "*")
                            .count(500)
                            .build())) {
                        while (cursor.hasNext()) {
                            matched.add(new String(cursor.next(), StandardCharsets.UTF_8));
                        }
                    }
                    return matched;
                });
                if (keys != null && !keys.isEmpty()) {
                    redisTemplate.delete(keys);
                }
            }
        } catch (Exception e) {
            log.warn("[Cache] L2 evict by prefix error for prefix={}: {}", prefix, e.getMessage());
        }
        publishInvalidation("PREFIX:" + prefix);
        log.debug("[Cache] EVICTED by prefix: {}", prefix);
    }

    public void evictLocal(String key) {
        l1Cache.invalidate(key);
        l1NullCache.invalidate(key);
        l1LongTermCache.invalidate(key);
    }

    public void evictLocalByPrefix(String prefix) {
        l1Cache.asMap().keySet().stream().filter(k -> k.startsWith(prefix)).toList()
                .forEach(l1Cache::invalidate);
        l1NullCache.asMap().keySet().stream().filter(k -> k.startsWith(prefix)).toList()
                .forEach(l1NullCache::invalidate);
        l1LongTermCache.asMap().keySet().stream().filter(k -> k.startsWith(prefix)).toList()
                .forEach(l1LongTermCache::invalidate);
    }

    public void onInvalidationMessage(String message) {
        if (message == null) return;
        if (message.startsWith("KEY:")) {
            evictLocal(message.substring(4));
        } else if (message.startsWith("PREFIX:")) {
            evictLocalByPrefix(message.substring(7));
        }
    }

    private void publishInvalidation(String message) {
        if (stringRedisTemplate == null) return;
        try {
            stringRedisTemplate.convertAndSend(CacheConstants.CACHE_INVALIDATION_CHANNEL, message);
        } catch (Exception e) {
            log.warn("[Cache] Invalidation broadcast failed: {}", e.getMessage());
        }
    }

    /** 随机偏移 0~2 分钟，防止缓存雪崩 */
    private long randomOffset() {
        return (long) (Math.random() * 3);
    }
}
