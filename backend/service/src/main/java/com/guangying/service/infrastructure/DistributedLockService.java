package com.guangying.service.infrastructure;

import com.guangying.common.constants.CacheConstants;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 分布式锁服务（Redisson 实现）
 *
 * <h3>面试加分项：</h3>
 * <ul>
 *   <li>基于 Redisson 的可重入锁（内部使用 Redis Hash + Lua 脚本）</li>
 *   <li>自动续期（Watchdog 机制，默认 30s 续期）</li>
 *   <li>tryLock 非阻塞获取，避免死锁</li>
 *   <li>支持超时自动释放，兜底保证安全</li>
 * </ul>
 */
@Slf4j
@Service
public class DistributedLockService {

    @Autowired(required = false)
    private RedissonClient redissonClient;

    /**
     * 在分布式锁保护下执行任务
     *
     * @param lockKey  锁资源标识
     * @param waitTime 等待获取锁的最大时间(秒)
     * @param leaseTime 持有锁的最大时间(秒)，超时自动释放
     * @param task     需要加锁执行的任务
     * @return 任务执行结果，获取锁失败返回 null
     */
    public <T> T executeWithLock(String lockKey, long waitTime, long leaseTime, Supplier<T> task) {
        if (redissonClient == null) {
            log.debug("[Lock] Redisson unavailable, executing task without lock: {}", lockKey);
            return task.get();
        }
        String fullKey = CacheConstants.LOCK_PREFIX + lockKey;
        RLock lock = redissonClient.getLock(fullKey);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(waitTime, leaseTime, TimeUnit.SECONDS);
            if (!acquired) {
                log.warn("[Lock] Failed to acquire lock: {}", fullKey);
                return null;
            }
            log.debug("[Lock] Acquired: {}", fullKey);
            return task.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[Lock] Interrupted while acquiring: {}", fullKey);
            return null;
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("[Lock] Released: {}", fullKey);
            }
        }
    }

    /**
     * Execute with Redisson watchdog renewal enabled.
     *
     * <p>Redisson only enables watchdog auto-renewal when no explicit lease time is supplied.
     * This variant is used by the seat/order critical path so long DB work does not expire the
     * lock prematurely.</p>
     */
    public <T> T executeWithWatchdogLock(String lockKey, long waitTime, Supplier<T> task) {
        if (redissonClient == null) {
            log.debug("[Lock] Redisson unavailable, executing task without lock: {}", lockKey);
            return task.get();
        }
        String fullKey = CacheConstants.LOCK_PREFIX + lockKey;
        RLock lock = redissonClient.getLock(fullKey);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(waitTime, TimeUnit.SECONDS);
            if (!acquired) {
                log.warn("[Lock] Failed to acquire watchdog lock: {}", fullKey);
                return null;
            }
            log.debug("[Lock] Acquired watchdog lock: {}", fullKey);
            return task.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[Lock] Interrupted while acquiring watchdog lock: {}", fullKey);
            return null;
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("[Lock] Released watchdog lock: {}", fullKey);
            }
        }
    }

    /**
     * Execute with a bounded lease time.
     *
     * <p>The watchdog variant is useful to avoid premature expiry during normal work, but it can
     * keep renewing forever when the owner thread is alive and stuck. Critical ticketing paths use
     * this bounded lock so a wedged thread cannot monopolize a hot show indefinitely. Database
     * optimistic updates and unique indexes still provide the final consistency guard if the lease
     * expires while an old transaction is blocked.</p>
     */
    public <T> T executeWithBoundedLock(String lockKey, long waitTime, long maxLeaseTime, Supplier<T> task) {
        if (redissonClient == null) {
            log.debug("[Lock] Redisson unavailable, executing task without lock: {}", lockKey);
            return task.get();
        }
        String fullKey = CacheConstants.LOCK_PREFIX + lockKey;
        RLock lock = redissonClient.getLock(fullKey);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(waitTime, maxLeaseTime, TimeUnit.SECONDS);
            if (!acquired) {
                log.warn("[Lock] Failed to acquire bounded lock: {}", fullKey);
                return null;
            }
            log.debug("[Lock] Acquired bounded lock: {}, lease={}s", fullKey, maxLeaseTime);
            return task.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[Lock] Interrupted while acquiring bounded lock: {}", fullKey);
            return null;
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("[Lock] Released bounded lock: {}", fullKey);
            }
        }
    }

    /**
     * 快速获取锁执行（等2秒，持10秒）
     */
    public <T> T executeWithLock(String lockKey, Supplier<T> task) {
        return executeWithLock(lockKey, 2, 10, task);
    }

    /**
     * 无返回值版本
     */
    public boolean executeWithLock(String lockKey, long waitTime, long leaseTime, Runnable task) {
        if (redissonClient == null) {
            log.debug("[Lock] Redisson unavailable, executing task without lock: {}", lockKey);
            task.run();
            return true;
        }
        String fullKey = CacheConstants.LOCK_PREFIX + lockKey;
        RLock lock = redissonClient.getLock(fullKey);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(waitTime, leaseTime, TimeUnit.SECONDS);
            if (!acquired) {
                log.warn("[Lock] Failed to acquire lock: {}", fullKey);
                return false;
            }
            task.run();
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
