package com.guangying.provider.controller;

import com.guangying.service.SearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import java.util.Map;

/**
 * 搜索接口控制器
 * <p>
 * - GET /ajax/search → 综合搜索（电影+影院）
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/ajax")
public class SearchController {

    @Resource
    private SearchService searchService;

    /**
     * 综合搜索
     * 返回格式: { movies: { list: [...] }, cinemas: { list: [...] } }
     */
    @GetMapping("/search")
    public Map<String, Object> search(
            @RequestParam String kw,
            @RequestParam Long cityId,
            @RequestParam(required = false) Integer stype
    ) {
        // 异步记录搜索行为
        searchService.recordSearchAsync(kw, cityId);

        return searchService.search(kw, cityId);
    }
}
