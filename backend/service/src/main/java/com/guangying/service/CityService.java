package com.guangying.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.guangying.common.constants.CacheConstants;
import com.guangying.dao.mapper.CityMapper;
import com.guangying.domain.model.po.CityPO;
import com.guangying.domain.model.vo.CityVO;
import com.guangying.service.cache.MultiLevelCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * 城市原子服务（多级缓存）
 */
@Slf4j
@Service
public class CityService {

    @Resource
    private CityMapper cityMapper;

    @Resource
    private MultiLevelCacheService cacheService;

    /**
     * 获取全部城市列表（长期缓存，24小时过期）
     */
    public List<CityVO> getAllCities() {
        return cacheService.getLongTerm(CacheConstants.CITIES, () -> {
            log.info("从数据库加载城市列表");
            LambdaQueryWrapper<CityPO> wrapper = new LambdaQueryWrapper<>();
            wrapper.orderByAsc(CityPO::getPy);
            return cityMapper.selectList(wrapper).stream()
                    .map(this::toVO)
                    .toList();
        });
    }

    private CityVO toVO(CityPO po) {
        CityVO vo = new CityVO();
        vo.setId(po.getId());
        vo.setNm(po.getNm());
        vo.setPy(po.getPy());
        return vo;
    }
}
