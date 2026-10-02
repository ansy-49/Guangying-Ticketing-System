package com.guangying.service.auth;

import com.guangying.domain.model.dto.UserLoginDTO;
import com.guangying.domain.model.po.UserPO;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 登录责任链：Spring 自动收集节点，并按 order 顺序执行。 */
@Component
public class LoginValidationChain {
    private final List<LoginHandler> handlers;

    public LoginValidationChain(List<LoginHandler> handlers) {
        this.handlers = handlers.stream()
                .sorted(Comparator.comparingInt(LoginHandler::order))
                .toList();
        rejectDuplicateOrders(this.handlers);
    }

    public UserPO authenticate(UserLoginDTO dto) {
        LoginContext context = LoginContext.from(dto);
        handlers.forEach(handler -> handler.handle(context));
        if (context.getUser() == null) {
            throw new IllegalStateException("登录责任链未加载用户");
        }
        return context.getUser();
    }

    private void rejectDuplicateOrders(List<LoginHandler> orderedHandlers) {
        Set<Integer> orders = new HashSet<>();
        for (LoginHandler handler : orderedHandlers) {
            if (!orders.add(handler.order())) {
                throw new IllegalStateException("重复的登录责任链顺序: " + handler.order());
            }
        }
    }
}
