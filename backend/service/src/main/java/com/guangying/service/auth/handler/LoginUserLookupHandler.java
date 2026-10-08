package com.guangying.service.auth.handler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.guangying.dao.mapper.UserMapper;
import com.guangying.domain.enums.ResponseCodeEnum;
import com.guangying.domain.exception.BizException;
import com.guangying.domain.model.po.UserPO;
import com.guangying.service.auth.LoginContext;
import com.guangying.service.auth.LoginHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 责任 3：查询有效用户并写入登录上下文。
 */
@Component
@RequiredArgsConstructor
public class LoginUserLookupHandler implements LoginHandler {

    private final UserMapper userMapper;

    @Override
    public int order() {
        return 30;
    }

    @Override
    public void handle(LoginContext context) {
        UserPO user = userMapper.selectOne(new LambdaQueryWrapper<UserPO>()
                .eq(UserPO::getAccount, context.getAccount())
                .eq(UserPO::getDeleted, 0));
        if (user == null) {
            throw new BizException(ResponseCodeEnum.NOT_FOUND, "账号不存在");
        }
        context.setUser(user);
    }
}
