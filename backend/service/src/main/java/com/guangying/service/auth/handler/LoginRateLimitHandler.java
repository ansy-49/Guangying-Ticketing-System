package com.guangying.service.auth.handler;

import com.guangying.domain.enums.ResponseCodeEnum;
import com.guangying.domain.exception.BizException;
import com.guangying.service.auth.LoginContext;
import com.guangying.service.auth.LoginHandler;
import com.guangying.service.infrastructure.RateLimiterService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 责任 2：限制单账号的登录尝试频率，降低暴力破解风险。
 */
@Component
@RequiredArgsConstructor
public class LoginRateLimitHandler implements LoginHandler {

    private static final int MAX_ATTEMPTS = 10;
    private static final int WINDOW_SECONDS = 60;

    private final RateLimiterService rateLimiterService;

    @Override
    public int order() {
        return 20;
    }

    @Override
    public void handle(LoginContext context) {
        boolean allowed = rateLimiterService.isAllowed(
                "auth:login", "account:" + context.getAccount(), MAX_ATTEMPTS, WINDOW_SECONDS);
        if (!allowed) {
            throw new BizException(ResponseCodeEnum.RATE_LIMITED, "登录尝试过于频繁，请稍后再试");
        }
    }
}
