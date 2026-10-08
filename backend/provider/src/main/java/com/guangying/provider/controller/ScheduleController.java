package com.guangying.provider.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guangying.common.annotation.RateLimit;
import com.guangying.common.enums.RateLimitDimension;
import com.guangying.domain.model.po.CinemaPO;
import com.guangying.domain.model.vo.MovieVO;
import com.guangying.domain.model.vo.Result;
import com.guangying.domain.model.vo.ScheduleVO;
import com.guangying.service.MovieService;
import com.guangying.service.ScheduleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 场次 & 影院详情接口
 */
@Slf4j
@RestController
@RequestMapping("/ajax")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;
    private final MovieService movieService;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 获取电影某日场次列表
     */
    @GetMapping("/schedules")
    @RateLimit(key = "schedules", maxRequests = 120, windowSeconds = 60)
    @RateLimit(key = "schedules", dimension = RateLimitDimension.GLOBAL,
            maxRequests = 60000, windowSeconds = 60)
    public Result<List<ScheduleVO>> getSchedules(
            @RequestParam Long movieId,
            @RequestParam(required = false) String showDate) {
        return Result.ok(scheduleService.getSchedules(movieId, showDate));
    }

    /**
     * 获取电影某日场次列表 — 按影院分组（购票选影院页）
     */
    @GetMapping("/schedulesByCinema")
    public Result<List<Map<String, Object>>> getSchedulesByCinema(
            @RequestParam Long movieId,
            @RequestParam(required = false) String showDate) {
        return Result.ok(scheduleService.getSchedulesByCinema(movieId, showDate));
    }

    /**
     * 获取电影有场次的日期列表（未来7天）
     */
    @GetMapping("/availableDates")
    public Result<List<String>> getAvailableDates(@RequestParam Long movieId) {
        return Result.ok(scheduleService.getAvailableDates(movieId));
    }

    // ==================== 影院详情页 ====================

    /**
     * 获取影院详情
     */
    @GetMapping("/cinemaDetail")
    public Result<Map<String, Object>> getCinemaDetail(@RequestParam Long cinemaId) {
        CinemaPO cinema = scheduleService.getCinemaById(cinemaId);
        if (cinema == null) {
            return Result.ok(Collections.emptyMap());
        }
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("id", cinema.getId());
        info.put("nm", cinema.getNm());
        info.put("addr", cinema.getAddr());
        info.put("allowRefund", cinema.getAllowRefund() != null && cinema.getAllowRefund() == 1);
        info.put("endorse", cinema.getEndorse() != null && cinema.getEndorse() == 1);
        info.put("snack", cinema.getSnack() != null && cinema.getSnack() == 1);
        info.put("vipTag", cinema.getVipTag());
        // 解析厅型JSON
        List<String> hallTypes = Collections.emptyList();
        if (cinema.getHallTypesJson() != null && !cinema.getHallTypesJson().isEmpty()) {
            try {
                hallTypes = objectMapper.readValue(cinema.getHallTypesJson(), new TypeReference<>() {});
            } catch (Exception e) {
                log.warn("解析影院厅型JSON失败, cinemaId={}", cinemaId, e);
            }
        }
        info.put("hallTypes", hallTypes);
        return Result.ok(info);
    }

    /**
     * 获取影院正在排片的电影列表
     */
    @GetMapping("/cinemaMovies")
    public Result<List<MovieVO>> getCinemaMovies(@RequestParam Long cinemaId) {
        List<Long> movieIds = scheduleService.getMovieIdsByCinema(cinemaId);
        if (movieIds.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        return Result.ok(movieService.getMoviesByIds(movieIds));
    }

    /**
     * 获取影院某电影某日的场次
     */
    @GetMapping("/cinemaSchedules")
    public Result<List<ScheduleVO>> getCinemaSchedules(
            @RequestParam Long cinemaId,
            @RequestParam Long movieId,
            @RequestParam(required = false) String showDate) {
        return Result.ok(scheduleService.getCinemaSchedules(cinemaId, movieId, showDate));
    }

    /**
     * 获取影院某电影有排片的日期列表
     */
    @GetMapping("/cinemaAvailableDates")
    public Result<List<String>> getCinemaAvailableDates(
            @RequestParam Long cinemaId,
            @RequestParam Long movieId) {
        return Result.ok(scheduleService.getCinemaAvailableDates(cinemaId, movieId));
    }
}
