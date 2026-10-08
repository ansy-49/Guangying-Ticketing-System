package com.guangying.service.aspect;

import com.guangying.common.annotation.RateLimit;
import com.guangying.common.enums.RateLimitDimension;
import com.guangying.domain.exception.BizException;
import com.guangying.domain.enums.ResponseCodeEnum;
import com.guangying.service.ratelimit.RateLimitContext;
import com.guangying.service.ratelimit.RateLimitStrategyRegistry;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
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

    private final RateLimitStrategyRegistry strategyRegistry;
    private final ExpressionParser expressionParser = new SpelExpressionParser();
    private final DefaultParameterNameDiscoverer parameterNameDiscoverer =
            new DefaultParameterNameDiscoverer();

    /** 仅当应用只允许可信反向代理访问时才读取代理转发头。 */
    @Value("${guangying.rate-limit.trust-proxy-headers:false}")
    private boolean trustProxyHeaders;

    @Around("@annotation(com.guangying.common.annotation.RateLimit) || "
            + "@annotation(com.guangying.common.annotation.RateLimits)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        RateLimit[] rules = signature.getMethod().getAnnotationsByType(RateLimit.class);

        for (RateLimit rule : rules) {
            String key = rule.key();
            if (key.isEmpty()) {
                key = signature.getDeclaringType().getSimpleName() + ":" + signature.getName();
            }

            String identifier = resolveIdentifier(pjp, signature, rule);
            RateLimitContext context = new RateLimitContext(
                    key,
                    identifier,
                    rule.maxRequests(),
                    rule.windowSeconds(),
                    rule.capacity(),
                    rule.refillRate()
            );
            boolean allowed = strategyRegistry.resolve(rule.algorithm()).isAllowed(context);
            if (!allowed) {
                log.warn("[RateLimit] Rejected resource={}, dimension={}, identifier={}",
                        key, rule.dimension(), identifier);
                throw new BizException(ResponseCodeEnum.RATE_LIMITED);
            }
        }

        return pjp.proceed();
    }

    private String resolveIdentifier(ProceedingJoinPoint pjp,
                                     MethodSignature signature,
                                     RateLimit rule) {
        if (rule.dimension() == RateLimitDimension.GLOBAL) {
            return "global";
        }
        if (rule.dimension() == RateLimitDimension.CALLER) {
            return getClientIdentifier();
        }
        if (rule.dimensionKey().isBlank()) {
            throw new IllegalStateException("RESOURCE rate limit requires dimensionKey");
        }

        MethodBasedEvaluationContext context = new MethodBasedEvaluationContext(
                pjp.getTarget(), signature.getMethod(), pjp.getArgs(), parameterNameDiscoverer);
        Object value = expressionParser.parseExpression(rule.dimensionKey()).getValue(context);
        if (value == null) {
            throw new IllegalStateException("Rate-limit dimensionKey resolved to null: "
                    + rule.dimensionKey());
        }
        return "resource:" + value;
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
                String ip = null;
                if (trustProxyHeaders) {
                    ip = firstForwardedAddress(request.getHeader("X-Real-IP"));
                    if (ip == null) {
                        ip = firstForwardedAddress(request.getHeader("X-Forwarded-For"));
                    }
                }
                if (ip == null) ip = request.getRemoteAddr();
                return "ip:" + ip;
            }
        } catch (Exception e) {
            log.warn("Failed to get client identifier", e);
        }
        return "unknown";
    }

    private String firstForwardedAddress(String value) {
        if (value == null || value.isBlank()) return null;
        String first = value.split(",", 2)[0].trim();
        return first.isEmpty() || "unknown".equalsIgnoreCase(first) ? null : first;
    }
}
