package com.guangying.provider.controller;

import com.guangying.common.annotation.RateLimit;
import com.guangying.common.enums.RateLimitAlgorithm;
import com.guangying.common.enums.RateLimitDimension;
import com.guangying.domain.model.vo.QueueEnterVO;
import com.guangying.domain.model.vo.QueueStatusVO;
import com.guangying.domain.model.vo.Result;
import com.guangying.service.UserService;
import com.guangying.service.infrastructure.QueueService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 排队控制器 — 热门场次 Waiting Room 准入控制
 *
 * <pre>
 * 非热门场次：直接放行，零开销
 * 热门场次：POST /api/queue/enter → 获取入场资格；GET /api/queue/status → 轮询排队位置
 *
 * 入场名额默认 2000，可在初始化热门场次时按容量压测结果覆盖
 * 入场令牌 TTL = 10 分钟（超时自动释放名额）
 * </pre>
 */
@Slf4j
@RestController
@RequestMapping("/api/queue")
public class QueueController {

    private final QueueService queueService;
    private final UserService userService;

    public QueueController(QueueService queueService, UserService userService) {
        this.queueService = queueService;
        this.userService = userService;
    }

    /**
     * 尝试入场 — 直接放行 / 排队等待
     *
     * <p>热门场次入场限制：
     * <ul>
     *   <li>持有有效令牌 → 直接返回 admitted=true</li>
     *   <li>已在等候队列 → 返回当前排队位置</li>
     *   <li>新用户 → 有空位则入场，否则加入等候队列</li>
     * </ul>
     */
    @PostMapping("/enter")
    @RateLimit(key = "queue:enter", algorithm = RateLimitAlgorithm.TOKEN_BUCKET,
            capacity = 12, refillRate = 3)
    @RateLimit(key = "queue:enter", algorithm = RateLimitAlgorithm.TOKEN_BUCKET,
            dimension = RateLimitDimension.RESOURCE, dimensionKey = "#scheduleId",
            capacity = 20000, refillRate = 5000)
    @RateLimit(key = "queue:enter", algorithm = RateLimitAlgorithm.TOKEN_BUCKET,
            dimension = RateLimitDimension.GLOBAL, capacity = 60000, refillRate = 15000)
    public Result<QueueEnterVO> enterQueue(
            @RequestParam Long scheduleId,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.fail(401, "请先登录");
        }

        QueueService.QueueEnterResult result = queueService.enter(scheduleId, userId);

        QueueEnterVO vo = new QueueEnterVO(
                result.isAdmitted(),
                result.isAdmitted() ? "ok" : "",
                result.getPosition(),
                result.getEstimatedWaitSeconds()
        );

        if (result.isAdmitted()) {
            return Result.ok(vo);
        } else {
            // 排队中，仍返回 200（非错误）
            return Result.ok(vo);
        }
    }

    /**
     * 查询排队状态（前端轮询）
     */
    @GetMapping("/status")
    @RateLimit(key = "queue:status", maxRequests = 30, windowSeconds = 60)
    @RateLimit(key = "queue:status", dimension = RateLimitDimension.RESOURCE,
            dimensionKey = "#scheduleId", maxRequests = 180000, windowSeconds = 60)
    @RateLimit(key = "queue:status", dimension = RateLimitDimension.GLOBAL,
            maxRequests = 600000, windowSeconds = 60)
    public Result<QueueStatusVO> queueStatus(
            @RequestParam Long scheduleId,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.fail(401, "请先登录");
        }

        QueueService.QueueStatusResult result = queueService.status(scheduleId, userId);
        QueueStatusVO vo = new QueueStatusVO(
                result.isAdmitted(),
                result.getPosition(),
                result.getEstimatedWaitSeconds()
        );
        return Result.ok(vo);
    }

    /**
     * 管理：标记场次为热门并设置准入上限
     *
     * <p>不传 maxAdmission 时使用集群基线 2000。该值表示同时持有准入租约的人数，
     * 不是数据库 QPS；正式环境应依据锁座吞吐、连接池和 P95 延迟压测校准。</p>
     */
    @PostMapping("/admin/init-hot")
    public Result<Void> initHotSchedule(
            @RequestParam Long scheduleId,
            @RequestParam(required = false) Integer maxAdmission,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        userService.requireAdmin(userId);
        queueService.initHotSchedule(scheduleId, maxAdmission);
        log.info("[Queue] Admin initialized hot schedule: scheduleId={}, requestedMaxAdmission={}",
                scheduleId, maxAdmission);
        return Result.ok(null);
    }
}
