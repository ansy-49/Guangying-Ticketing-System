package com.guangying.provider.controller;

import com.guangying.domain.enums.ResponseCodeEnum;
import com.guangying.domain.model.po.OutboxEventPO;
import com.guangying.domain.model.vo.Result;
import com.guangying.service.UserService;
import com.guangying.service.infrastructure.OutboxService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Outbox 运维入口：只允许 ADMIN 查看和重放 DEAD 事件。 */
@RestController
@RequestMapping("/api/admin/outbox")
@RequiredArgsConstructor
public class OutboxAdminController {

    private final OutboxService outboxService;
    private final UserService userService;

    @GetMapping("/dead")
    public Result<List<OutboxEventPO>> listDead(
            @RequestParam(defaultValue = "50") int limit,
            HttpServletRequest request) {
        userService.requireAdmin((Long) request.getAttribute("userId"));
        return Result.ok(outboxService.listDead(limit));
    }

    @PostMapping("/retry")
    public Result<Void> retryDead(
            @RequestParam Long eventId,
            HttpServletRequest request) {
        userService.requireAdmin((Long) request.getAttribute("userId"));
        if (!outboxService.retryDead(eventId)) {
            return Result.fail(ResponseCodeEnum.CONFLICT.getCode(),
                    "事件不存在或当前状态不允许重放");
        }
        return Result.ok(null);
    }
}
