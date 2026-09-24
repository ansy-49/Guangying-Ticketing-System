package com.guangying.biz.cinema;

import com.guangying.domain.model.dto.CinemaQueryDTO;
import com.guangying.domain.model.vo.CinemaVO;
import com.guangying.service.CinemaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 影院列表业务编排
 */
@Slf4j
@Service
public class CinemaListBiz {

    @Resource
    private CinemaService cinemaService;

    /**
     * 获取影院列表接口数据
     *
     * @return { cinemas: CinemaVO[] }
     */
    public Map<String, Object> getCinemaListData(CinemaQueryDTO query) {
        List<CinemaVO> cinemas = cinemaService.getCinemaList(query);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cinemas", cinemas);
        return result;
    }

    /**
     * 获取影院筛选项接口数据
     *
     * @param cityId 城市ID
     * @return { brand, hallType, service, district, subway }
     */
    public Map<String, Object> getFilterData(Long cityId) {
        return cinemaService.getFilterOptions(cityId);
    }
}
