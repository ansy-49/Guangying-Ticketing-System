package com.guangying.service.auth.handler;

import com.guangying.domain.enums.ResponseCodeEnum;
import com.guangying.domain.exception.BizException;
import com.guangying.service.auth.LoginContext;
import com.guangying.service.auth.LoginHandler;
import org.springframework.stereotype.Component;

/** 责任 1：服务层参数兜底与账号规范化。 */
@Component
public class LoginRequestValidationHandler implements LoginHandler {
    @Override
    public int order() { return 10; }

    @Override
    public void handle(LoginContext context) {
        if (context.getAccount() == null || context.getAccount().isBlank()
                || context.getRawPassword() == null || context.getRawPassword().isBlank()) {
            throw new BizException(ResponseCodeEnum.BAD_REQUEST, "账号和密码不能为空");
        }
        context.setAccount(context.getAccount().trim());
    }
}
