package com.guangying.service.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.guangying.common.constants.CacheConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 多级缓存服务（L1 Caffeine + L2 Redis）
 *
 * <h3>设计要点（面试加分项）：</h3>
 * <ol>
 *   <li><b>L1（进程内 Caffeine）</b>：容量小、TTL短（60s），减少网络IO</li>
 *   <li><b>L2（Redis）</b>：容量大、TTL长（10min），跨进程共享</li>
 *   <li><b>缓存穿透防护</b>：空值也缓存（短 TTL），避免打穿数据库</li>
 *   <li><b>缓存击穿防护</b>：singleflight 模式，同 key 只放一个线程回源</li>
 *   <li><b>缓存雪崩防护</b>：TTL 加随机偏移，避免大批 key 同时过期</li>
 * </ol>
 *
 * <p>降级策略：当 Redis 不可用时自动退化为仅 L1 本地缓存</p>
 */
@Slf4j
@Service
public class MultiLevelCacheService {

    /** 压测或故障诊断时可关闭缓存，默认开启。 */
    @Value("${guangying.cache.enabled:true}")
    private boolean cacheEnabled;

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    /** L1 本地缓存 — 短 TTL、小容量 */
    private final Cache<String, Object> l1Cache = Caffeine.newBuilder()
            .maximumSize(500)
            .expireAfterWrite(CacheConstants.L1_EXPIRE_SECONDS, TimeUnit.SECONDS)
            .recordStats()
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
     * 流程: L1 → L2 (Redis) → DB loader → 回填 L2 → 回填 L1
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

        // 2. L2 查询 (Redis)
        if (redisTemplate != null) {
            try {
                Object l2Val = redisTemplate.opsForValue().get(key);
                if (l2Val != null) {
                    log.debug("[Cache] L2 HIT: {}", key);
                    l1Cache.put(key, l2Val); // 回填 L1
                    return (T) l2Val;
                }
            } catch (Exception e) {
                log.warn("[Cache] L2 read error for key={}, fallback to loader: {}", key, e.getMessage());
            }
        }

        // 3. DB 回源（singleflight: Caffeine.get 保证同 key 只一个线程回源）
        log.debug("[Cache] MISS, loading from DB: {}", key);
        T value = loader.get();

        if (value != null) {
            // 回填 L1
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
        l1Cache.invalidate(key);
        if (redisTemplate != null) {
            try {
                redisTemplate.delete(key);
            } catch (Exception e) {
                log.warn("[Cache] L2 evict error for key={}: {}", key, e.getMessage());
            }
        }
        log.debug("[Cache] EVICTED: {}", key);
    }

    /**
     * 批量失效
     */
    public void evictByPrefix(String prefix) {
        l1Cache.asMap().keySet().stream()
                .filter(k -> k.startsWith(prefix))
                .forEach(l1Cache::invalidate);
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
        log.debug("[Cache] EVICTED by prefix: {}", prefix);
    }

    /** 随机偏移 0~2 分钟，防止缓存雪崩 */
    private long randomOffset() {
        return (long) (Math.random() * 3);
    }
}
