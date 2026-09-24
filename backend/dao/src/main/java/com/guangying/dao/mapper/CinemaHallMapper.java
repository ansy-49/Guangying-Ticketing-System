package com.guangying.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.guangying.domain.model.po.CinemaHallPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 影院影厅 Mapper
 */
@Mapper
public interface CinemaHallMapper extends BaseMapper<CinemaHallPO> {

    /**
     * 根据影院ID和影厅名查找座位布局
     */
    @Select("SELECT * FROM cinema_hall WHERE cinema_id = #{cinemaId} AND hall_name = #{hallName} AND deleted = 0 LIMIT 1")
    CinemaHallPO selectByCinemaAndHall(@Param("cinemaId") Long cinemaId, @Param("hallName") String hallName);

    /**
     * 查询影院所有影厅
     */
    @Select("SELECT * FROM cinema_hall WHERE cinema_id = #{cinemaId} AND deleted = 0 ORDER BY id")
    List<CinemaHallPO> selectByCinemaId(@Param("cinemaId") Long cinemaId);
}
