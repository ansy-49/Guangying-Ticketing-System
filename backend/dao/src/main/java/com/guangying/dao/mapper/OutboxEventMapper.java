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
    @Select("SELECT * FROM outbox_event WHERE status = 'PENDING' ORDER BY create_time ASC LIMIT #{limit}")
    List<OutboxEventPO> selectPending(@Param("limit") int limit);

    /**
     * 标记事件已发送
     */
    @Update("UPDATE outbox_event SET status = 'SENT', sent_time = #{now} WHERE id = #{id} AND status = 'PENDING'")
    int markSent(@Param("id") Long id, @Param("now") LocalDateTime now);

    /**
     * 标记发送失败（重试次数 +1）
     */
    @Update("UPDATE outbox_event SET status = 'FAILED', retries = retries + 1 WHERE id = #{id}")
    int markFailed(@Param("id") Long id);

    /**
     * 删除 7 天前已发送的事件（清理）
     */
    @Update("DELETE FROM outbox_event WHERE status = 'SENT' AND sent_time < #{before}")
    int deleteSentBefore(@Param("before") LocalDateTime before);
}
