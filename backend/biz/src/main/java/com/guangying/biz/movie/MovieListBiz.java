package com.guangying.biz.movie;

import com.guangying.common.constants.CommonConstants;
import com.guangying.domain.model.vo.MovieVO;
import com.guangying.service.MovieService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 电影列表业务编排
 * <p>
 * 组合 MovieService 的原子操作，编排电影列表相关的复杂业务逻辑。
 * 使用线程池并行加载多个数据源，提升接口响应速度。
 * </p>
 */
@Slf4j
@Service
public class MovieListBiz {

    @Resource
    private MovieService movieService;

    @Resource(name = "bizTaskExecutor")
    private ThreadPoolExecutor bizTaskExecutor;

    /**
     * 获取热映电影列表接口数据
     * <p>
     * 并行加载电影列表和ID列表，前端首次只展示前10部。
     * </p>
     *
     * @return { movieList: MovieVO[], movieIds: Long[] }
     */
    public Map<String, Object> getHotMovieListData() {
        // 并行加载电影列表和 ID 列表
        CompletableFuture<List<MovieVO>> moviesFuture =
                CompletableFuture.supplyAsync(() -> movieService.getHotMovies(), bizTaskExecutor);
        CompletableFuture<List<Long>> idsFuture =
                CompletableFuture.supplyAsync(() -> movieService.getHotMovieIds(), bizTaskExecutor);

        CompletableFuture.allOf(moviesFuture, idsFuture).join();

        List<MovieVO> allMovies = moviesFuture.join();
        List<Long> allIds = idsFuture.join();

        // 首次返回前10部 + 全部ID
        List<MovieVO> firstBatch = allMovies.size() > CommonConstants.MOVIE_BATCH_SIZE
                ? allMovies.subList(0, CommonConstants.MOVIE_BATCH_SIZE)
                : allMovies;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("movieList", firstBatch);
        result.put("movieIds", allIds);
        return result;
    }

    /**
     * 获取即将上映电影列表接口数据
     *
     * @return { coming: MovieVO[], movieIds: Long[] }
     */
    public Map<String, Object> getComingListData() {
        CompletableFuture<List<MovieVO>> moviesFuture =
                CompletableFuture.supplyAsync(() -> movieService.getComingMovies(), bizTaskExecutor);
        CompletableFuture<List<Long>> idsFuture =
                CompletableFuture.supplyAsync(() -> movieService.getComingMovieIds(), bizTaskExecutor);

        CompletableFuture.allOf(moviesFuture, idsFuture).join();

        List<MovieVO> allMovies = moviesFuture.join();
        List<Long> allIds = idsFuture.join();

        List<MovieVO> firstBatch = allMovies.size() > CommonConstants.MOVIE_BATCH_SIZE
                ? allMovies.subList(0, CommonConstants.MOVIE_BATCH_SIZE)
                : allMovies;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("coming", firstBatch);
        result.put("movieIds", allIds);
        return result;
    }

    /**
     * 获取最受期待电影数据
     *
     * @return { coming: MovieVO[] }
     */
    public Map<String, Object> getMostExpectedData() {
        List<MovieVO> movies = movieService.getMostExpected();
        return Map.of("coming", movies);
    }

    /**
     * 根据ID列表加载更多电影
     *
     * @param movieIdsStr 逗号分隔的电影ID字符串
     * @return { coming: MovieVO[] }
     */
    public Map<String, Object> getMoreMovies(String movieIdsStr) {
        if (movieIdsStr == null || movieIdsStr.isBlank()) {
            return Map.of("coming", Collections.emptyList());
        }

        List<Long> ids = Arrays.stream(movieIdsStr.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::parseLong)
                .toList();

        List<MovieVO> movies = movieService.getMoviesByIds(ids);
        return Map.of("coming", movies);
    }
}
