package com.guangying.service.auth;

import com.guangying.domain.model.dto.UserLoginDTO;
import com.guangying.domain.model.po.UserPO;

/** 一次登录校验在责任链中的共享上下文。 */
public class LoginContext {
    private String account;
    private final String rawPassword;
    private UserPO user;

    private LoginContext(String account, String rawPassword) {
        this.account = account;
        this.rawPassword = rawPassword;
    }

    public static LoginContext from(UserLoginDTO dto) {
        return new LoginContext(dto == null ? null : dto.getAccount(),
                dto == null ? null : dto.getPassword());
    }

    public String getAccount() { return account; }
    public void setAccount(String account) { this.account = account; }
    public String getRawPassword() { return rawPassword; }
    public UserPO getUser() { return user; }
    public void setUser(UserPO user) { this.user = user; }
}
