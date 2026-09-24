package com.guangying.provider.controller;

import com.guangying.biz.movie.MovieDetailBiz;
import com.guangying.biz.movie.MovieListBiz;
import com.guangying.service.MovieService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import java.util.Map;

/**
 * 电影接口控制器
 * <p>
 * 所有接口路径与前端 api.ts 完全对齐：
 * - GET /ajax/movieOnInfoList  → 热映列表
 * - GET /ajax/comingList       → 即将上映列表
 * - GET /ajax/mostExpected     → 最受期待
 * - GET /ajax/moreComingList   → 加载更多
 * - GET /ajax/detailmovie      → 电影详情
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/ajax")
public class MovieController {

    @Resource
    private MovieListBiz movieListBiz;

    @Resource
    private MovieDetailBiz movieDetailBiz;

    @Resource
    private MovieService movieService;

    /**
     * 正在热映列表
     * 返回格式: { movieList: [...], movieIds: [...] }
     */
    @GetMapping("/movieOnInfoList")
    public Map<String, Object> getHotMovies() {
        return movieListBiz.getHotMovieListData();
    }

    /**
     * 即将上映列表
     * 返回格式: { coming: [...], movieIds: [...] }
     */
    @GetMapping("/comingList")
    public Map<String, Object> getComingList() {
        return movieListBiz.getComingListData();
    }

    /**
     * 最受期待电影列表
     * 返回格式: { coming: [...] }
     */
    @GetMapping("/mostExpected")
    public Map<String, Object> getMostExpected() {
        return movieListBiz.getMostExpectedData();
    }

    /**
     * 加载更多电影（按ID分批）
     * 返回格式: { coming: [...] }
     */
    @GetMapping("/moreComingList")
    public Map<String, Object> getMoreList(@RequestParam("movieIds") String movieIds) {
        return movieListBiz.getMoreMovies(movieIds);
    }

    /**
     * 电影详情
     * 返回格式: { detailMovie: {...} }
     */
    @GetMapping("/detailmovie")
    public Map<String, Object> getMovieDetail(@RequestParam("movieId") Long movieId) {
        return movieDetailBiz.getMovieDetailData(movieId);
    }

    /**
     * 电影筛选（按类型/地区/年份/状态，支持排序+分页）
     * 返回格式: { movies: [...], total: N, hasMore: bool }
     */
    @GetMapping("/filterMovies")
    public Map<String, Object> filterMovies(
            @RequestParam(required = false) Integer movieStatus,
            @RequestParam(required = false) String cat,
            @RequestParam(required = false) String src,
            @RequestParam(required = false) Integer year,
            @RequestParam(defaultValue = "hot") String sortBy,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "30") int pageSize) {
        return movieService.filterMovies(movieStatus, cat, src, year, sortBy, page, pageSize);
    }
}
