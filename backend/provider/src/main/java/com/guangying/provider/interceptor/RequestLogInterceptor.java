package com.guangying.provider.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 请求日志拦截器
 * <p>
 * 记录每个请求的方法、URL、耗时，便于性能分析和问题排查。
 * </p>
 */
@Slf4j
@Component
public class RequestLogInterceptor implements HandlerInterceptor {

    private static final String START_TIME = "REQUEST_START_TIME";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(START_TIME, System.currentTimeMillis());
        log.debug("[请求开始] requestId={} {} {}",
                request.getAttribute(RequestContextInterceptor.REQUEST_ID_ATTRIBUTE),
                request.getMethod(), request.getRequestURI());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        Long startTime = (Long) request.getAttribute(START_TIME);
        if (startTime != null) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.info("[请求完成] requestId={} {} {} → {} ({}ms)",
                    request.getAttribute(RequestContextInterceptor.REQUEST_ID_ATTRIBUTE),
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    elapsed);

            // 慢请求告警
            if (elapsed > 1000) {
                log.warn("[慢请求] requestId={} {} {} 耗时 {}ms",
                        request.getAttribute(RequestContextInterceptor.REQUEST_ID_ATTRIBUTE),
                        request.getMethod(), request.getRequestURI(), elapsed);
            }
        }
    }
}
