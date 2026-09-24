package com.guangying.provider.controller;

import com.guangying.domain.model.vo.CityVO;
import com.guangying.service.CityService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * 城市接口控制器
 * <p>
 * - GET /dianying/cities.json → 城市列表
 * </p>
 */
@Slf4j
@RestController
public class CityController {

    @Resource
    private CityService cityService;

    /**
     * 获取全部城市列表
     * 返回格式: { cts: [...] }
     * <p>
     * 路径为 /dianying/cities.json，与前端约定一致
     * </p>
     */
    @GetMapping("/dianying/cities.json")
    public Map<String, Object> getCities() {
        List<CityVO> cities = cityService.getAllCities();
        return Map.of("cts", cities);
    }
}
