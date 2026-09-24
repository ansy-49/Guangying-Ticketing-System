package com.guangying.provider.controller;

import com.guangying.biz.cinema.CinemaListBiz;
import com.guangying.domain.model.dto.CinemaQueryDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import java.util.Map;

/**
 * 影院接口控制器
 * <p>
 * - GET /ajax/cinemaList     → 影院列表（多维过滤+分页）
 * - GET /ajax/filterCinemas  → 影院筛选项
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/ajax")
public class CinemaController {

    @Resource
    private CinemaListBiz cinemaListBiz;

    /**
     * 影院列表
     * 返回格式: { cinemas: [...] }
     */
    @GetMapping("/cinemaList")
    public Map<String, Object> getCinemaList(
            @RequestParam(defaultValue = "0") Integer offset,
            @RequestParam(required = false) String day,
            @RequestParam(required = false, defaultValue = "0") Long cityId,
            @RequestParam(required = false) Long brandId,
            @RequestParam(required = false) Long serviceId,
            @RequestParam(required = false) Long hallType,
            @RequestParam(required = false) Long areaId,
            @RequestParam(required = false) Long districtId,
            @RequestParam(required = false, defaultValue = "0") Long ci
    ) {
        CinemaQueryDTO query = new CinemaQueryDTO();
        query.setOffset(offset);
        query.setDay(day != null ? day : "");
        // ci 和 cityId 互兼容
        query.setCityId(cityId != null && cityId > 0 ? cityId : (ci != null && ci > 0 ? ci : 1L));
        query.setBrandId(brandId);
        query.setServiceId(serviceId);
        query.setHallType(hallType);
        query.setAreaId(areaId);
        query.setDistrictId(districtId);
        query.setLimit(20);

        return cinemaListBiz.getCinemaListData(query);
    }

    /**
     * 影院筛选项
     * 返回格式: { brand: [...], hallType: {...}, service: {...}, district: {...}, subway: {...} }
     */
    @GetMapping("/filterCinemas")
    public Map<String, Object> getFilterCinemas(@RequestParam("ci") Long cityId) {
        return cinemaListBiz.getFilterData(cityId);
    }
}
