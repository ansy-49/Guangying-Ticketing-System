package com.guangying.provider.controller;

import com.guangying.domain.model.vo.QueueEnterVO;
import com.guangying.domain.model.vo.QueueStatusVO;
import com.guangying.domain.model.vo.Result;
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
 * 入场名额 = 可售座位数 × 3（在初始化热门场次时设定）
 * 入场令牌 TTL = 10 分钟（超时自动释放名额）
 * </pre>
 */
@Slf4j
@RestController
@RequestMapping("/api/queue")
public class QueueController {

    private final QueueService queueService;

    public QueueController(QueueService queueService) {
        this.queueService = queueService;
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
     * <p>建议 maxAdmission = 可售座位数 × 2~3</p>
     */
    @PostMapping("/admin/init-hot")
    public Result<Void> initHotSchedule(
            @RequestParam Long scheduleId,
            @RequestParam int maxAdmission) {
        queueService.initHotSchedule(scheduleId, maxAdmission);
        log.info("[Queue] Admin initialized hot schedule: scheduleId={}, maxAdmission={}",
                scheduleId, maxAdmission);
        return Result.ok(null);
    }
}
