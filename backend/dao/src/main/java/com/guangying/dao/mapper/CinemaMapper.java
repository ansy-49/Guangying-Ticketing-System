package com.guangying.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.guangying.domain.model.po.CinemaPO;
import com.guangying.dao.provider.CinemaSqlProvider;
import com.guangying.domain.model.dto.CinemaQueryDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectProvider;

import java.util.List;

/**
 * 影院 Mapper
 */
@Mapper
public interface CinemaMapper extends BaseMapper<CinemaPO> {

    /**
     * 根据复合条件查询影院列表（支持品牌/服务/厅型/商圈多维过滤）
     */
    @SelectProvider(type = CinemaSqlProvider.class, method = "selectCinemaList")
    List<CinemaPO> selectCinemaList(CinemaQueryDTO query);

    /**
     * 搜索影院 (模糊匹配名称/地址)
     */
    @Select("""
        SELECT * FROM cinema
        WHERE deleted = 0
          AND city_id = #{cityId}
          AND (nm LIKE CONCAT('%', #{kw}, '%')
               OR addr LIKE CONCAT('%', #{kw}, '%'))
        ORDER BY sort_order, id
        LIMIT 20
    """)
    List<CinemaPO> searchByKeyword(@Param("kw") String keyword, @Param("cityId") Long cityId);
}
