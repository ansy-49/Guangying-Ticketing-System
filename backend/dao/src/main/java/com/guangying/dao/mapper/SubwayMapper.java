package com.guangying.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.guangying.domain.model.po.SubwayPO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 地铁线路/站点 Mapper
 */
@Mapper
public interface SubwayMapper extends BaseMapper<SubwayPO> {
}
