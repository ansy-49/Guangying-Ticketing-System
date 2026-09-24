package com.guangying.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guangying.common.constants.CacheConstants;
import com.guangying.dao.mapper.*;
import com.guangying.domain.model.dto.CinemaQueryDTO;
import com.guangying.domain.model.po.*;
import com.guangying.domain.model.vo.CinemaVO;
import com.guangying.domain.model.vo.FilterItemVO;
import com.guangying.service.cache.MultiLevelCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 影院原子服务
 */
@Slf4j
@Service
public class CinemaService {

    @Resource
    private CinemaMapper cinemaMapper;
    @Resource
    private CinemaBrandMapper brandMapper;
    @Resource
    private DistrictMapper districtMapper;
    @Resource
    private SubwayMapper subwayMapper;
    @Resource
    private ServiceTypeMapper serviceTypeMapper;
    @Resource
    private HallTypeMapper hallTypeMapper;
    @Resource
    private MultiLevelCacheService cacheService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 查询影院列表（支持多维过滤 + 分页）
     */
    public List<CinemaVO> getCinemaList(CinemaQueryDTO query) {
        log.info("查询影院列表: cityId={}, offset={}, brandId={}, districtId={}",
                query.getCityId(), query.getOffset(), query.getBrandId(), query.getDistrictId());

        if (query.getLimit() == null || query.getLimit() <= 0) {
            query.setLimit(20);
        }
        if (query.getOffset() == null || query.getOffset() < 0) {
            query.setOffset(0);
        }

        List<CinemaPO> poList = cinemaMapper.selectCinemaList(query);
        return poList.stream().map(this::toVO).toList();
    }

    /**
     * 获取影院筛选项（带缓存）
     */
    public Map<String, Object> getFilterOptions(Long cityId) {
        String cacheKey = CacheConstants.CINEMA_FILTER_PREFIX + cityId;
        return cacheService.get(cacheKey, () -> {
            log.info("从数据库加载影院筛选项: cityId={}", cityId);
            Map<String, Object> result = new LinkedHashMap<>();

            // 1. 品牌
            List<CinemaBrandPO> brands = brandMapper.selectList(null);
            List<FilterItemVO> brandItems = brands.stream()
                    .map(b -> new FilterItemVO(b.getId().intValue(), b.getName(), b.getCount()))
                    .collect(Collectors.toList());
            // 添加"全部"选项
            brandItems.add(0, new FilterItemVO(-1, "全部", 0));
            result.put("brand", brandItems);

            // 2. 厅型
            List<HallTypePO> hallTypes = hallTypeMapper.selectList(null);
            List<FilterItemVO> hallItems = hallTypes.stream()
                    .map(h -> new FilterItemVO(h.getId().intValue(), h.getName(), h.getCount()))
                    .collect(Collectors.toList());
            hallItems.add(0, new FilterItemVO(-1, "全部", 0));
            result.put("hallType", new FilterItemVO("特殊厅", hallItems));

            // 3. 服务
            List<ServiceTypePO> services = serviceTypeMapper.selectList(null);
            List<FilterItemVO> serviceItems = services.stream()
                    .map(s -> new FilterItemVO(s.getId().intValue(), s.getName(), s.getCount()))
                    .collect(Collectors.toList());
            serviceItems.add(0, new FilterItemVO(-1, "全部", 0));
            result.put("service", new FilterItemVO("服务", serviceItems));

            // 4. 行政区（含子商圈）
            result.put("district", buildHierarchicalFilter("商圈", districtMapper, cityId));

            // 5. 地铁（含子站点）
            result.put("subway", buildSubwayFilter("地铁站", cityId));

            return result;
        });
    }

    /**
     * 搜索影院
     */
    public List<CinemaVO> searchCinemas(String keyword, Long cityId) {
        return cinemaMapper.searchByKeyword(keyword, cityId).stream()
                .map(this::toVO)
                .toList();
    }

    // ========== 私有方法 ==========

    /**
     * 构建层级筛选项（行政区 → 商圈）
     */
    private FilterItemVO buildHierarchicalFilter(String name, DistrictMapper mapper, Long cityId) {
        LambdaQueryWrapper<DistrictPO> parentWrapper = new LambdaQueryWrapper<>();
        parentWrapper.eq(DistrictPO::getCityId, cityId)
                .eq(DistrictPO::getParentId, 0)
                .orderByAsc(DistrictPO::getId);
        List<DistrictPO> parents = mapper.selectList(parentWrapper);

        List<FilterItemVO> items = new ArrayList<>();
        items.add(new FilterItemVO(-1, "全部", 0));

        for (DistrictPO parent : parents) {
            LambdaQueryWrapper<DistrictPO> childWrapper = new LambdaQueryWrapper<>();
            childWrapper.eq(DistrictPO::getCityId, cityId)
                    .eq(DistrictPO::getParentId, parent.getId())
                    .orderByAsc(DistrictPO::getId);
            List<DistrictPO> children = mapper.selectList(childWrapper);

            List<FilterItemVO> childItems = new ArrayList<>();
            childItems.add(new FilterItemVO(-1, "全部", parent.getCount()));
            children.forEach(c -> childItems.add(
                    new FilterItemVO(c.getId().intValue(), c.getName(), c.getCount())
            ));

            items.add(new FilterItemVO(parent.getName(), childItems));
        }
        return new FilterItemVO(name, items);
    }

    /**
     * 构建地铁层级筛选项（线路 → 站点）
     */
    private FilterItemVO buildSubwayFilter(String name, Long cityId) {
        LambdaQueryWrapper<SubwayPO> lineWrapper = new LambdaQueryWrapper<>();
        lineWrapper.eq(SubwayPO::getCityId, cityId)
                .eq(SubwayPO::getParentId, 0)
                .orderByAsc(SubwayPO::getId);
        List<SubwayPO> lines = subwayMapper.selectList(lineWrapper);

        List<FilterItemVO> items = new ArrayList<>();
        items.add(new FilterItemVO(-1, "全部", 0));

        for (SubwayPO line : lines) {
            LambdaQueryWrapper<SubwayPO> stationWrapper = new LambdaQueryWrapper<>();
            stationWrapper.eq(SubwayPO::getCityId, cityId)
                    .eq(SubwayPO::getParentId, line.getId())
                    .orderByAsc(SubwayPO::getId);
            List<SubwayPO> stations = subwayMapper.selectList(stationWrapper);

            List<FilterItemVO> stationItems = new ArrayList<>();
            stationItems.add(new FilterItemVO(-1, "全部", line.getCount()));
            stations.forEach(s -> stationItems.add(
                    new FilterItemVO(s.getId().intValue(), s.getName(), s.getCount())
            ));

            items.add(new FilterItemVO(line.getName(), stationItems));
        }
        return new FilterItemVO(name, items);
    }

    /**
     * PO → VO
     */
    private CinemaVO toVO(CinemaPO po) {
        CinemaVO vo = new CinemaVO();
        vo.setId(po.getId());
        vo.setNm(po.getNm());
        vo.setAddr(po.getAddr());
        vo.setDistance(po.getDistance());

        // 构建 tag
        CinemaVO.Tag tag = new CinemaVO.Tag();
        tag.setAllowRefund(po.getAllowRefund() != null && po.getAllowRefund() == 1);
        tag.setEndorse(po.getEndorse() != null && po.getEndorse() == 1);
        tag.setSnack(po.getSnack() != null && po.getSnack() == 1);
        tag.setVipTag(po.getVipTag());

        // 解析厅型JSON
        if (po.getHallTypesJson() != null && !po.getHallTypesJson().isEmpty()) {
            try {
                tag.setHallType(objectMapper.readValue(po.getHallTypesJson(), new TypeReference<>() {}));
            } catch (JsonProcessingException e) {
                log.warn("解析影院厅型JSON失败, cinemaId={}", po.getId(), e);
                tag.setHallType(Collections.emptyList());
            }
        } else {
            tag.setHallType(Collections.emptyList());
        }
        vo.setTag(tag);

        // 构建 promotion
        CinemaVO.Promotion promotion = new CinemaVO.Promotion();
        promotion.setCardPromotionTag(po.getCardPromotionTag());
        vo.setPromotion(promotion);

        return vo;
    }
}
