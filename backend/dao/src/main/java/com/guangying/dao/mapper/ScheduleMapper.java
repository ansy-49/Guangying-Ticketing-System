package com.guangying.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.guangying.domain.model.po.SchedulePO;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 场次 Mapper（MyBatis 高级用法示范：XML + 注解混用、原子扣减库存）
 */
public interface ScheduleMapper extends BaseMapper<SchedulePO> {

    /**
     * 查询电影在指定日期的场次（按影院分组展示）
     */
    @Select("SELECT * FROM movie_schedule WHERE movie_id = #{movieId} AND show_date = #{showDate} AND status = 1 AND deleted = 0 ORDER BY show_time ASC")
    List<SchedulePO> selectByMovieAndDate(@Param("movieId") Long movieId, @Param("showDate") String showDate);

    /**
     * 原子扣减库存 — 防超卖核心 SQL
     *
     * <p>由数据库在同一条 UPDATE 中完成库存校验和扣减。并发请求会在行锁上串行，
     * 不会因为无关座位更新了 version 就误报库存不足；available_seats 条件仍可防止超卖。</p>
     */
    @Update("UPDATE movie_schedule SET available_seats = available_seats - #{seatCount}, version = version + 1, update_time = CURRENT_TIMESTAMP WHERE id = #{scheduleId} AND available_seats >= #{seatCount} AND deleted = 0")
    int deductStock(@Param("scheduleId") Long scheduleId, @Param("seatCount") int seatCount);

    /**
     * 回滚库存（订单取消/退款时）
     */
    @Update("UPDATE movie_schedule SET available_seats = available_seats + #{seatCount}, version = version + 1, update_time = CURRENT_TIMESTAMP WHERE id = #{scheduleId} AND deleted = 0")
    int rollbackStock(@Param("scheduleId") Long scheduleId, @Param("seatCount") int seatCount);

    /**
     * 查询指定电影在某日全部影院的场次（购票选影院页）
     */
    @Select("""
        SELECT ms.*, c.nm as cinema_name FROM movie_schedule ms
        LEFT JOIN cinema c ON ms.cinema_id = c.id
        WHERE ms.movie_id = #{movieId} AND ms.show_date = #{showDate} AND ms.status = 1 AND ms.deleted = 0
        ORDER BY c.sort_order ASC, ms.show_time ASC
    """)
    @Results({
        @Result(property = "id", column = "id"),
        @Result(property = "movieId", column = "movie_id"),
        @Result(property = "cinemaId", column = "cinema_id"),
        @Result(property = "hallName", column = "hall_name"),
        @Result(property = "showDate", column = "show_date"),
        @Result(property = "showTime", column = "show_time"),
        @Result(property = "endTime", column = "end_time"),
        @Result(property = "lang", column = "lang"),
        @Result(property = "totalSeats", column = "total_seats"),
        @Result(property = "availableSeats", column = "available_seats"),
        @Result(property = "price", column = "price"),
        @Result(property = "status", column = "status"),
        @Result(property = "version", column = "version")
    })
    List<SchedulePO> selectByMovieAndDateAllCinemas(@Param("movieId") Long movieId, @Param("showDate") String showDate);

    /**
     * 查询某场次的影院名
     */
    @Select("SELECT c.nm FROM movie_schedule ms LEFT JOIN cinema c ON ms.cinema_id = c.id WHERE ms.id = #{scheduleId}")
    String selectCinemaNameByScheduleId(@Param("scheduleId") Long scheduleId);

    /**
     * 查询某影院有排片的电影ID列表（今天及之后）
     */
    @Select("SELECT DISTINCT movie_id FROM movie_schedule WHERE cinema_id = #{cinemaId} AND show_date >= #{today} AND status = 1 AND deleted = 0")
    List<Long> selectMovieIdsByCinema(@Param("cinemaId") Long cinemaId, @Param("today") String today);

    /**
     * 查询某影院某电影某日的场次
     */
    @Select("SELECT * FROM movie_schedule WHERE cinema_id = #{cinemaId} AND movie_id = #{movieId} AND show_date = #{showDate} AND status = 1 AND deleted = 0 ORDER BY show_time ASC")
    List<SchedulePO> selectByCinemaAndMovieAndDate(@Param("cinemaId") Long cinemaId, @Param("movieId") Long movieId, @Param("showDate") String showDate);

    /**
     * 查询某影院某电影有排片的日期列表
     */
    @Select("SELECT DISTINCT show_date FROM movie_schedule WHERE cinema_id = #{cinemaId} AND movie_id = #{movieId} AND show_date >= #{today} AND status = 1 AND deleted = 0 ORDER BY show_date ASC")
    List<String> selectAvailableDatesByCinemaAndMovie(@Param("cinemaId") Long cinemaId, @Param("movieId") Long movieId, @Param("today") String today);

    // ==================== 启动时日期刷新（演示数据永不过期） ====================

    /**
     * 获取最早的排片日期
     */
    @Select("SELECT MIN(show_date) FROM movie_schedule WHERE deleted = 0 AND status = 1")
    String selectMinShowDate();

    /**
     * 将所有排片日期前移 daysDiff 天，并重置库存和版本号
     * 这样无论何时启动，排片数据都表现为"今天/明天"
     */
    @Update("UPDATE movie_schedule SET show_date = DATE_ADD(show_date, INTERVAL #{daysDiff} DAY), available_seats = total_seats, version = 0, update_time = CURRENT_TIMESTAMP WHERE deleted = 0")
    int refreshAllScheduleDates(@Param("daysDiff") long daysDiff);
}
