package com.guangying.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.guangying.domain.model.po.OutboxEventPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Outbox 事件 Mapper
 */
@Mapper
public interface OutboxEventMapper extends BaseMapper<OutboxEventPO> {

    /**
     * 查询待发送的事件（按创建时间升序，limit 防止一次拉太多）
     */
    @Select("""
            SELECT * FROM outbox_event
            WHERE (status = 'PENDING' AND (next_retry_time IS NULL OR next_retry_time <= #{now}))
               OR (status = 'PROCESSING' AND claimed_until < #{now})
            ORDER BY create_time ASC
            LIMIT #{limit}
            """)
    List<OutboxEventPO> selectClaimable(@Param("now") LocalDateTime now,
                                        @Param("limit") int limit);

    /** 多实例竞争时，只有一个实例能拿到事件的发送租约。 */
    @Update("""
            UPDATE outbox_event
            SET status = 'PROCESSING', claim_token = #{claimToken}, claimed_until = #{claimedUntil}
            WHERE id = #{id}
              AND ((status = 'PENDING' AND (next_retry_time IS NULL OR next_retry_time <= #{now}))
                OR (status = 'PROCESSING' AND claimed_until < #{now}))
            """)
    int tryClaim(@Param("id") Long id,
                 @Param("claimToken") String claimToken,
                 @Param("now") LocalDateTime now,
                 @Param("claimedUntil") LocalDateTime claimedUntil);

    /**
     * 标记事件已发送
     */
    @Update("""
            UPDATE outbox_event
            SET status = 'SENT', sent_time = #{now}, claim_token = NULL,
                claimed_until = NULL, last_error = NULL
            WHERE id = #{id} AND status = 'PROCESSING' AND claim_token = #{claimToken}
            """)
    int markSent(@Param("id") Long id,
                 @Param("claimToken") String claimToken,
                 @Param("now") LocalDateTime now);

    /**
     * 标记发送失败（重试次数 +1）
     */
    @Update("""
            UPDATE outbox_event
            SET status = CASE WHEN retries + 1 >= #{maxRetries} THEN 'DEAD' ELSE 'PENDING' END,
                retries = retries + 1,
                next_retry_time = #{nextRetryTime},
                last_error = #{lastError},
                claim_token = NULL,
                claimed_until = NULL
            WHERE id = #{id} AND status = 'PROCESSING' AND claim_token = #{claimToken}
            """)
    int markRetry(@Param("id") Long id,
                  @Param("claimToken") String claimToken,
                  @Param("maxRetries") int maxRetries,
                  @Param("nextRetryTime") LocalDateTime nextRetryTime,
                  @Param("lastError") String lastError);

    /** 管理端查看需要人工处理的事件。 */
    @Select("SELECT * FROM outbox_event WHERE status = 'DEAD' ORDER BY create_time ASC LIMIT #{limit}")
    List<OutboxEventPO> selectDead(@Param("limit") int limit);

    /** 人工重放只能作用于 DEAD，避免把已发送事件再次投入队列。 */
    @Update("""
            UPDATE outbox_event
            SET status = 'PENDING', retries = 0, next_retry_time = #{now},
                claim_token = NULL, claimed_until = NULL, last_error = NULL
            WHERE id = #{id} AND status = 'DEAD'
            """)
    int requeueDead(@Param("id") Long id, @Param("now") LocalDateTime now);

    /**
     * 删除 7 天前已发送的事件（清理）
     */
    @Update("DELETE FROM outbox_event WHERE status = 'SENT' AND sent_time < #{before}")
    int deleteSentBefore(@Param("before") LocalDateTime before);
}
