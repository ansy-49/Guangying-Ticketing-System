package com.guangying.service.mq;

import com.guangying.common.constants.MQConstants;
import com.guangying.common.constants.CacheConstants;
import com.guangying.dao.mapper.MovieMapper;
import com.guangying.dao.mapper.UserWishMapper;
import com.guangying.domain.model.event.WishEvent;
import com.guangying.domain.model.po.MoviePO;
import com.guangying.domain.model.po.UserWishPO;
import com.guangying.service.cache.MultiLevelCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 想看写回消费者 — RocketMQ 版
 *
 * <p>订阅 WISH_TOPIC，异步写回 DB，保证最终一致性</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rocketmq.name-server")
@RocketMQMessageListener(
        topic = MQConstants.WISH_TOPIC,
        consumerGroup = MQConstants.WISH_CONSUMER_GROUP
)
public class WishWriteBackConsumer implements RocketMQListener<WishEvent> {

    private final MovieMapper movieMapper;
    private final UserWishMapper userWishMapper;
    private final MultiLevelCacheService cacheService;

    @Override
    public void onMessage(WishEvent event) {
        try {
            boolean inserted = false;
            try {
                UserWishPO wish = new UserWishPO();
                wish.setUserId(event.getUserId());
                wish.setMovieId(event.getMovieId());
                wish.setCreateTime(LocalDateTime.now());
                userWishMapper.insert(wish);
                inserted = true;
            } catch (Exception e) {
                log.debug("[WishConsumer] Duplicate event ignored: userId={}, movieId={}",
                        event.getUserId(), event.getMovieId());
            }

            // RocketMQ 至少一次投递：唯一索引冲突说明该业务事件已经落库，不得重复累加。
            if (!inserted) return;

            MoviePO movie = movieMapper.selectById(event.getMovieId());
            if (movie != null) {
                movie.setWish(movie.getWish() + event.getDelta());
                movieMapper.updateById(movie);
                cacheService.evict(CacheConstants.MOVIE_DETAIL_PREFIX + event.getMovieId());
                cacheService.evict(CacheConstants.HOT_MOVIES);
                cacheService.evict(CacheConstants.COMING_MOVIES);
                cacheService.evict(CacheConstants.MOST_EXPECTED);
            }
            log.debug("[WishConsumer] Writeback success: userId={}, movieId={}", event.getUserId(), event.getMovieId());
        } catch (Exception e) {
            log.error("[WishConsumer] Writeback failed: userId={}, movieId={}", event.getUserId(), event.getMovieId(), e);
            throw new RuntimeException("想看写回处理失败，触发重试", e);
        }
    }
}
