package com.guangying.service.auth.handler;

import com.guangying.common.utils.PasswordUtil;
import com.guangying.domain.enums.ResponseCodeEnum;
import com.guangying.domain.exception.BizException;
import com.guangying.service.auth.LoginContext;
import com.guangying.service.auth.LoginHandler;
import org.springframework.stereotype.Component;

/**
 * 责任 4：BCrypt 密码校验。
 */
@Component
public class LoginPasswordVerificationHandler implements LoginHandler {

    @Override
    public int order() {
        return 40;
    }

    @Override
    public void handle(LoginContext context) {
        if (!PasswordUtil.matches(context.getRawPassword(), context.getUser().getPassword())) {
            throw new BizException(ResponseCodeEnum.BAD_REQUEST, "密码错误");
        }
    }
}
