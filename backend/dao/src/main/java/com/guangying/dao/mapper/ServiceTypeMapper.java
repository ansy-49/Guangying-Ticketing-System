package com.guangying.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.guangying.domain.model.po.ServiceTypePO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 影院服务类型 Mapper
 */
@Mapper
public interface ServiceTypeMapper extends BaseMapper<ServiceTypePO> {
}
