package com.guangying.service.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

/**
 * 本地缓存服务
 * <p>
 * 使用 Caffeine 高性能缓存 + ReadWriteLock 实现双重锁检查（DCL）防止缓存击穿。
 * 读多写少场景下，ReadWriteLock 允许多个线程并发读取，仅在缓存 miss 时串行加载。
 * </p>
 */
@Slf4j
@Service
public class LocalCacheService {

    /**
     * 通用缓存实例（TTL 10分钟，最大500条目）
     */
    private final Cache<String, Object> cache = Caffeine.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .maximumSize(500)
            .recordStats()
            .build();

    /**
     * 长期缓存实例（TTL 24小时，用于城市等不常变化数据）
     */
    private final Cache<String, Object> longTermCache = Caffeine.newBuilder()
            .expireAfterWrite(24, TimeUnit.HOURS)
            .maximumSize(100)
            .build();

    /**
     * 读写锁 — 防止缓存击穿时多线程同时加载相同数据
     */
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();

    /**
     * 从缓存获取数据，不存在则通过 loader 加载并放入缓存
     * <p>
     * 使用 DCL（Double-Check Locking）模式：
     * 1. 读锁检查缓存是否存在
     * 2. 不存在时升级到写锁，再次检查防止并发重复加载
     * 3. 执行 loader 加载数据并放入缓存
     * </p>
     *
     * @param key    缓存键
     * @param loader 数据加载函数
     * @return 缓存数据
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Supplier<T> loader) {
        // 1. 读锁快速检查
        rwLock.readLock().lock();
        try {
            Object cached = cache.getIfPresent(key);
            if (cached != null) {
                return (T) cached;
            }
        } finally {
            rwLock.readLock().unlock();
        }

        // 2. 缓存 miss，写锁加载
        rwLock.writeLock().lock();
        try {
            // DCL: 再次检查，可能其他线程已经加载
            Object cached = cache.getIfPresent(key);
            if (cached != null) {
                return (T) cached;
            }

            log.info("缓存 miss，加载数据: key={}", key);
            T data = loader.get();
            if (data != null) {
                cache.put(key, data);
            }
            return data;
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * 从长期缓存获取（用于城市等低频变更数据）
     */
    @SuppressWarnings("unchecked")
    public <T> T getLongTerm(String key, Supplier<T> loader) {
        rwLock.readLock().lock();
        try {
            Object cached = longTermCache.getIfPresent(key);
            if (cached != null) {
                return (T) cached;
            }
        } finally {
            rwLock.readLock().unlock();
        }

        rwLock.writeLock().lock();
        try {
            Object cached = longTermCache.getIfPresent(key);
            if (cached != null) {
                return (T) cached;
            }

            log.info("长期缓存 miss，加载数据: key={}", key);
            T data = loader.get();
            if (data != null) {
                longTermCache.put(key, data);
            }
            return data;
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * 主动失效缓存
     */
    public void evict(String key) {
        cache.invalidate(key);
        longTermCache.invalidate(key);
        log.debug("缓存已失效: key={}", key);
    }

    /**
     * 清空全部缓存
     */
    public void evictAll() {
        cache.invalidateAll();
        longTermCache.invalidateAll();
        log.info("全部缓存已清空");
    }
}
