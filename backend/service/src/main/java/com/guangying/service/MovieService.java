package com.guangying.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guangying.common.constants.CacheConstants;
import com.guangying.dao.mapper.MovieMapper;
import com.guangying.domain.enums.MovieStatusEnum;
import com.guangying.domain.model.po.MoviePO;
import com.guangying.domain.model.vo.MovieVO;
import com.guangying.service.cache.MultiLevelCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 电影原子服务（使用多级缓存 L1 Caffeine + L2 Redis）
 */
@Slf4j
@Service
public class MovieService {

    @Resource
    private MovieMapper movieMapper;

    @Resource
    private MultiLevelCacheService cacheService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 获取热映电影列表（带缓存）
     */
    public List<MovieVO> getHotMovies() {
        return cacheService.get(CacheConstants.HOT_MOVIES, () -> {
            log.info("从数据库加载热映电影列表");
            LambdaQueryWrapper<MoviePO> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(MoviePO::getMovieStatus, MovieStatusEnum.HOT.getCode())
                    .eq(MoviePO::getDeleted, 0)
                    .orderByAsc(MoviePO::getSortOrder)
                    .orderByAsc(MoviePO::getId);
            return movieMapper.selectList(wrapper).stream()
                    .map(this::toVO)
                    .toList();
        });
    }

    /**
     * 获取热映电影ID列表（带缓存）
     */
    public List<Long> getHotMovieIds() {
        return cacheService.get(CacheConstants.HOT_MOVIES + ":ids", () -> {
            log.info("从数据库加载热映电影ID列表");
            return movieMapper.selectHotMovieIds();
        });
    }

    /**
     * 获取即将上映电影列表（带缓存）
     */
    public List<MovieVO> getComingMovies() {
        return cacheService.get(CacheConstants.COMING_MOVIES, () -> {
            log.info("从数据库加载即将上映电影列表");
            LambdaQueryWrapper<MoviePO> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(MoviePO::getMovieStatus, MovieStatusEnum.COMING.getCode())
                    .eq(MoviePO::getDeleted, 0)
                    .orderByAsc(MoviePO::getSortOrder)
                    .orderByAsc(MoviePO::getId);
            return movieMapper.selectList(wrapper).stream()
                    .map(this::toVO)
                    .toList();
        });
    }

    /**
     * 获取即将上映电影ID列表（带缓存）
     */
    public List<Long> getComingMovieIds() {
        return cacheService.get(CacheConstants.COMING_MOVIES + ":ids", () -> {
            log.info("从数据库加载即将上映电影ID列表");
            return movieMapper.selectComingMovieIds();
        });
    }

    /**
     * 获取最受期待电影列表（按想看人数排序前10）
     */
    public List<MovieVO> getMostExpected() {
        return cacheService.get(CacheConstants.MOST_EXPECTED, () -> {
            log.info("从数据库加载最受期待电影列表");
            LambdaQueryWrapper<MoviePO> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(MoviePO::getMovieStatus, MovieStatusEnum.COMING.getCode())
                    .eq(MoviePO::getDeleted, 0)
                    .orderByDesc(MoviePO::getWish)
                    .last("LIMIT 10");
            return movieMapper.selectList(wrapper).stream()
                    .map(this::toVO)
                    .toList();
        });
    }

    /**
     * 根据ID列表批量查询电影
     */
    public List<MovieVO> getMoviesByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        List<MoviePO> poList = movieMapper.selectByIds(ids);
        // 按传入的 ID 顺序排序（替代 MySQL FIELD() 函数，兼容 H2）
        Map<Long, MoviePO> poMap = poList.stream()
                .collect(Collectors.toMap(MoviePO::getId, p -> p, (a, b) -> a));
        return ids.stream()
                .map(poMap::get)
                .filter(Objects::nonNull)
                .map(this::toVO)
                .toList();
    }

    /**
     * 获取电影详情
     */
    public MovieVO getMovieDetail(Long movieId) {
        if (movieId == null) return null;
        return cacheService.get(CacheConstants.MOVIE_DETAIL_PREFIX + movieId, () -> {
            MoviePO po = movieMapper.selectById(movieId);
            if (po == null || po.getDeleted() == 1) {
                return null;
            }
            return toDetailVO(po);
        });
    }

    /**
     * 搜索电影
     */
    public List<MovieVO> searchMovies(String keyword) {
        return movieMapper.searchByKeyword(keyword).stream()
                .map(this::toVO)
                .toList();
    }

    /**
     * 电影筛选（按类型/地区/年份/状态，支持排序+分页）
     *
     * @return { movies: MovieVO[], total: long, hasMore: boolean }
     */
    public Map<String, Object> filterMovies(Integer movieStatus, String cat, String src, Integer year,
                                             String sortBy, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        List<MoviePO> poList = movieMapper.filterMovies(movieStatus, cat, src, year, sortBy, offset, pageSize + 1);
        long total = movieMapper.countFilterMovies(movieStatus, cat, src, year);

        boolean hasMore = poList.size() > pageSize;
        if (hasMore) {
            poList = poList.subList(0, pageSize);
        }

        List<MovieVO> voList = poList.stream().map(this::toListVO).toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("movies", voList);
        result.put("total", total);
        result.put("hasMore", hasMore);
        return result;
    }

    // ========== PO → VO 转换 ==========

    /** 列表页VO（包含筛选所需的cat/src/releaseYear） */
    private MovieVO toListVO(MoviePO po) {
        MovieVO vo = toVO(po);
        vo.setCat(po.getCat());
        vo.setSrc(po.getSrc());
        vo.setReleaseYear(po.getReleaseYear());
        vo.setDur(po.getDur());
        vo.setPubDesc(po.getPubDesc());
        return vo;
    }

    private MovieVO toVO(MoviePO po) {
        MovieVO vo = new MovieVO();
        vo.setId(po.getId());
        vo.setNm(po.getNm());
        vo.setImg(po.getImg());
        vo.setStar(po.getStar());
        vo.setShowInfo(po.getShowInfo());
        vo.setWish(po.getWish());
        vo.setGlobalReleased(po.getGlobalReleased() != null && po.getGlobalReleased() == 1);
        vo.setComingTitle(po.getComingTitle());

        // 评分处理：已上映显示数字评分，未上映显示"暂无评分"
        if (po.getGlobalReleased() != null && po.getGlobalReleased() == 1 && po.getSc() != null) {
            vo.setSc(po.getSc());
        } else {
            vo.setSc("暂无评分");
        }

        return vo;
    }

    private MovieVO toDetailVO(MoviePO po) {
        MovieVO vo = toVO(po);
        vo.setEnm(po.getEnm());
        vo.setCat(po.getCat());
        vo.setSrc(po.getSrc());
        vo.setDur(po.getDur());
        vo.setPubDesc(po.getPubDesc());
        vo.setDra(po.getDra());
        vo.setVd(po.getVd());
        vo.setPn(po.getPn());
        vo.setReleaseYear(po.getReleaseYear());

        // 评分对详情页始终返回数字
        if (po.getSc() != null) {
            vo.setSc(po.getSc());
        }

        // 解析剧照JSON数组
        if (po.getPhotos() != null && !po.getPhotos().isEmpty()) {
            try {
                List<String> photoList = objectMapper.readValue(po.getPhotos(), new TypeReference<>() {});
                vo.setPhotos(photoList);
            } catch (JsonProcessingException e) {
                log.warn("解析电影剧照JSON失败, movieId={}", po.getId(), e);
                vo.setPhotos(Collections.emptyList());
            }
        }

        return vo;
    }
}
