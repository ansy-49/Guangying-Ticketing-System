package com.guangying.service.aspect;

import com.guangying.common.annotation.RateLimit;
import com.guangying.common.enums.RateLimitAlgorithm;
import com.guangying.domain.exception.BizException;
import com.guangying.domain.enums.ResponseCodeEnum;
import com.guangying.service.infrastructure.RateLimiterService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 限流切面 — 基于 AOP + Redis 实现声明式限流（面试亮点）
 *
 * <h3>支持双算法动态切换：</h3>
 * <pre>
 * 1. SLIDING_WINDOW（默认）— 通用 API 限流，精确窗口计数
 * 2. TOKEN_BUCKET         — 抢座/秒杀场景，允许突发 + 平滑限速
 *
 * 算法选择通过 @RateLimit(algorithm = ...) 声明，零代码切换。
 * </pre>
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    private final RateLimiterService rateLimiterService;

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint pjp, RateLimit rateLimit) throws Throwable {
        String key = rateLimit.key();
        if (key.isEmpty()) {
            MethodSignature sig = (MethodSignature) pjp.getSignature();
            key = sig.getDeclaringType().getSimpleName() + ":" + sig.getName();
        }

        String identifier = getClientIdentifier();

        // 根据注解配置的算法分发
        boolean allowed;
        if (rateLimit.algorithm() == RateLimitAlgorithm.TOKEN_BUCKET) {
            allowed = rateLimiterService.isAllowedTokenBucket(
                    key, identifier,
                    rateLimit.capacity(),
                    rateLimit.refillRate()
            );
        } else {
            allowed = rateLimiterService.isAllowed(
                    key, identifier,
                    rateLimit.maxRequests(),
                    rateLimit.windowSeconds()
            );
        }

        if (!allowed) {
            throw new BizException(ResponseCodeEnum.RATE_LIMITED);
        }

        return pjp.proceed();
    }

    private String getClientIdentifier() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                // 优先从请求属性获取用户ID（JWT拦截器设置的）
                Object userId = request.getAttribute("userId");
                if (userId != null) {
                    return "user:" + userId;
                }
                // 降级到 IP
                String ip = request.getHeader("X-Real-IP");
                if (ip == null) ip = request.getHeader("X-Forwarded-For");
                if (ip == null) ip = request.getRemoteAddr();
                return "ip:" + ip;
            }
        } catch (Exception e) {
            log.warn("Failed to get client identifier", e);
        }
        return "unknown";
    }
}
