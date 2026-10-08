package com.guangying.service.auth;

/**
 * 登录责任链节点。每个节点只处理一种校验职责。
 */
public interface LoginHandler {

    int order();

    void handle(LoginContext context);
}
