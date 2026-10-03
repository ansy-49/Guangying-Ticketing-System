package com.guangying.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.guangying.common.constants.CacheConstants;
import com.guangying.common.constants.MQConstants;
import com.guangying.dao.mapper.MovieMapper;
import com.guangying.dao.mapper.UserWishMapper;
import com.guangying.domain.model.event.WishEvent;
import com.guangying.domain.model.po.MoviePO;
import com.guangying.domain.model.po.UserWishPO;
import com.guangying.service.cache.MultiLevelCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 想看服务 — 实时处理（面试核心亮点）
 *
 * <h3>架构设计：</h3>
 * <pre>
 * ┌──────────┐     ┌──────────────┐     ┌────────────┐     ┌──────────┐
 * │  用户点击  │ ──→ │ Redis INCR   │ ──→ │  RocketMQ  │ ──→ │  DB 写回  │
 * │  "想看"   │     │ (实时计数)     │     │ (异步解耦)  │     │ (最终一致) │
 * └──────────┘     └──────────────┘     └────────────┘     └──────────┘
 * </pre>
 *
 * <h3>技术要点：</h3>
 * <ol>
 *   <li><b>Redis HINCRBY</b>：原子操作，高并发下保证计数准确</li>
 *   <li><b>Redis Set</b>：用户去重（每个用户对同一电影只能想看一次）</li>
 *   <li><b>MQ 异步写回</b>：削峰填谷，不阻塞主流程</li>
 *   <li><b>最终一致性</b>：Redis 实时展示 + DB 最终持久化</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WishService {

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;
    @Autowired(required = false)
    private RocketMQTemplate rocketMQTemplate;

    private final UserWishMapper userWishMapper;
    private final MovieMapper movieMapper;
    private final MultiLevelCacheService cacheService;

    private static final DefaultRedisScript<Long> ADD_WISH_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[2]) == 0 then
                redis.call('SET', KEYS[2], ARGV[2])
                if ARGV[3] == '1' then
                    redis.call('SADD', KEYS[1], ARGV[1])
                    return -1
                end
            end
            if redis.call('SISMEMBER', KEYS[1], ARGV[1]) == 1 then
                return -1
            end
            redis.call('SADD', KEYS[1], ARGV[1])
            return redis.call('INCRBY', KEYS[2], 1)
            """, Long.class);

    /**
     * 用户点击"想看"（核心方法）
     *
     * @return 操作后的总想看数
     */
    public long addWish(Long userId, Long movieId) {
        // 当 Redis 可用时：Redis Set 去重 + Hash 计数 + MQ 异步写回
        if (stringRedisTemplate != null) {
            try {
                String usersKey = CacheConstants.movieKey(CacheConstants.MOVIE_WISH_USERS_PREFIX, movieId);
                String countKey = CacheConstants.movieKey(CacheConstants.MOVIE_WISH_COUNT_PREFIX, movieId);
                boolean countInitialized = Boolean.TRUE.equals(stringRedisTemplate.hasKey(countKey));
                long baseline = countInitialized ? 0 : databaseWishCount(movieId);
                boolean alreadyInDatabase = !countInitialized && databaseHasWished(userId, movieId);
                Long count = stringRedisTemplate.execute(ADD_WISH_SCRIPT,
                        List.of(usersKey, countKey), String.valueOf(userId), String.valueOf(baseline),
                        alreadyInDatabase ? "1" : "0");
                if (count != null && count == -1L) {
                    log.info("[Wish] User {} already wished movie {}", userId, movieId);
                    return getWishCount(movieId);
                }
                if (rocketMQTemplate != null) {
                    WishEvent event = new WishEvent(userId, movieId, 1, System.currentTimeMillis());
                    String destination = MQConstants.WISH_TOPIC + ":" + MQConstants.TAG_WISH_WRITEBACK;
                    rocketMQTemplate.syncSend(destination, event);
                    log.info("[Wish] Event sent: userId={}, movieId={}, newCount={}", userId, movieId, count);
                } else {
                    syncWriteBack(userId, movieId);
                }
                return count != null ? count : baseline;
            } catch (Exception e) {
                log.warn("[Wish] Redis/MQ path failed, falling back to idempotent DB write", e);
                syncWriteBack(userId, movieId);
                return getWishCount(movieId);
            }
        }

        // 降级模式：无 Redis，直接 DB 操作
        LambdaQueryWrapper<UserWishPO> check = new LambdaQueryWrapper<>();
        check.eq(UserWishPO::getUserId, userId).eq(UserWishPO::getMovieId, movieId);
        if (userWishMapper.selectCount(check) > 0) {
            log.info("[Wish] User {} already wished movie {} (DB check)", userId, movieId);
            return getWishCount(movieId);
        }
        syncWriteBack(userId, movieId);
        return getWishCount(movieId);
    }

    /** 同步写回 DB（降级或 MQ 不可用时） */
    private void syncWriteBack(Long userId, Long movieId) {
        boolean inserted = false;
        try {
            UserWishPO wish = new UserWishPO();
            wish.setUserId(userId);
            wish.setMovieId(movieId);
            wish.setCreateTime(LocalDateTime.now());
            userWishMapper.insert(wish);
            inserted = true;
        } catch (Exception e) {
            log.debug("[Wish] Wish record already exists: userId={}, movieId={}", userId, movieId);
        }
        if (!inserted) return;
        MoviePO movie = movieMapper.selectById(movieId);
        if (movie != null) {
            movie.setWish(movie.getWish() + 1);
            movieMapper.updateById(movie);
            invalidateMovieCaches(movieId);
        }
    }

    /**
     * 获取电影实时想看数（优先从 Redis 读取）
     */
    public long getWishCount(Long movieId) {
        if (stringRedisTemplate != null) {
            try {
                String val = stringRedisTemplate.opsForValue().get(
                        CacheConstants.movieKey(CacheConstants.MOVIE_WISH_COUNT_PREFIX, movieId));
                if (val != null) return Long.parseLong(val.toString());
            } catch (Exception e) {
                log.warn("[Wish] Redis read failed for movieId={}", movieId, e);
            }
        }
        // 降级：从 DB 查询
        return databaseWishCount(movieId);
    }

    /**
     * 检查用户是否已想看
     */
    public boolean hasWished(Long userId, Long movieId) {
        if (stringRedisTemplate != null) {
            try {
                return Boolean.TRUE.equals(
                        stringRedisTemplate.opsForSet().isMember(
                                CacheConstants.movieKey(CacheConstants.MOVIE_WISH_USERS_PREFIX, movieId),
                                String.valueOf(userId)
                        )
                );
            } catch (Exception e) {
                log.warn("[Wish] Redis check failed, fallback to DB", e);
            }
        }
        return userWishMapper.selectCount(
                new LambdaQueryWrapper<UserWishPO>()
                        .eq(UserWishPO::getUserId, userId)
                        .eq(UserWishPO::getMovieId, movieId)
        ) > 0;
    }

    private long databaseWishCount(Long movieId) {
        MoviePO movie = movieMapper.selectById(movieId);
        return movie != null && movie.getWish() != null ? movie.getWish() : 0;
    }

    private boolean databaseHasWished(Long userId, Long movieId) {
        return userWishMapper.selectCount(new LambdaQueryWrapper<UserWishPO>()
                .eq(UserWishPO::getUserId, userId)
                .eq(UserWishPO::getMovieId, movieId)) > 0;
    }

    private void invalidateMovieCaches(Long movieId) {
        cacheService.evict(CacheConstants.MOVIE_DETAIL_PREFIX + movieId);
        cacheService.evict(CacheConstants.HOT_MOVIES);
        cacheService.evict(CacheConstants.COMING_MOVIES);
        cacheService.evict(CacheConstants.MOST_EXPECTED);
    }
}
