package com.guangying.provider;

import com.guangying.domain.model.dto.UserLoginDTO;
import com.guangying.domain.model.po.UserPO;
import com.guangying.provider.interceptor.RequestContextInterceptor;
import com.guangying.service.auth.LoginContext;
import com.guangying.service.auth.LoginHandler;
import com.guangying.service.auth.LoginValidationChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LoginChainAndInterceptorTest {

    @Test
    void loginHandlersRunByOrder() {
        List<Integer> invoked = new ArrayList<>();
        LoginHandler last = handler(30, invoked, context -> {
            UserPO user = new UserPO();
            user.setId(1L);
            context.setUser(user);
        });
        LoginValidationChain chain = new LoginValidationChain(List.of(
                last,
                handler(10, invoked, context -> context.setAccount(context.getAccount().trim())),
                handler(20, invoked, context -> { })
        ));

        UserLoginDTO dto = new UserLoginDTO();
        dto.setAccount(" user ");
        dto.setPassword("secret");

        assertEquals(1L, chain.authenticate(dto).getId());
        assertEquals(List.of(10, 20, 30), invoked);
    }

    @Test
    void loginChainRejectsDuplicateOrders() {
        assertThrows(IllegalStateException.class, () -> new LoginValidationChain(List.of(
                handler(10, new ArrayList<>(), context -> { }),
                handler(10, new ArrayList<>(), context -> { })
        )));
    }

    @Test
    void loginChainStopsAfterHandlerFailure() {
        List<Integer> invoked = new ArrayList<>();
        LoginValidationChain chain = new LoginValidationChain(List.of(
                handler(10, invoked, context -> { throw new IllegalArgumentException("stop"); }),
                handler(20, invoked, context -> { })
        ));

        assertThrows(IllegalArgumentException.class, () -> chain.authenticate(new UserLoginDTO()));
        assertEquals(List.of(10), invoked);
    }

    @Test
    void requestContextCreatesAndCleansRequestId() throws Exception {
        RequestContextInterceptor interceptor = new RequestContextInterceptor();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(response.containsHeader(RequestContextInterceptor.REQUEST_ID_HEADER));
        interceptor.preHandle(request, response, new Object());

        Object requestId = request.getAttribute(RequestContextInterceptor.REQUEST_ID_ATTRIBUTE);
        assertNotNull(requestId);
        assertEquals(requestId, response.getHeader(RequestContextInterceptor.REQUEST_ID_HEADER));
        assertEquals(requestId, MDC.get(RequestContextInterceptor.REQUEST_ID_ATTRIBUTE));

        interceptor.afterCompletion(request, response, new Object(), null);
        assertNull(MDC.get(RequestContextInterceptor.REQUEST_ID_ATTRIBUTE));
    }

    private LoginHandler handler(int order, List<Integer> invoked, HandlerAction action) {
        return new LoginHandler() {
            @Override
            public int order() {
                return order;
            }

            @Override
            public void handle(LoginContext context) {
                invoked.add(order);
                action.accept(context);
            }
        };
    }

    @FunctionalInterface
    private interface HandlerAction {
        void accept(LoginContext context);
    }
}
