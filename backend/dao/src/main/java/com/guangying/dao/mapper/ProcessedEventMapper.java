package com.guangying.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.guangying.domain.model.po.ProcessedEventPO;
import org.apache.ibatis.annotations.Mapper;

/** 消费幂等记录 Mapper，event_id 主键保证并发消费只落一份完成记录。 */
@Mapper
public interface ProcessedEventMapper extends BaseMapper<ProcessedEventPO> {
}
