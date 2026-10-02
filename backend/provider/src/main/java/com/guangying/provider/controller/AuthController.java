package com.guangying.provider.controller;

import com.guangying.domain.model.dto.UserLoginDTO;
import com.guangying.domain.model.dto.UserRegisterDTO;
import com.guangying.domain.model.vo.Result;
import com.guangying.domain.model.vo.UserVO;
import com.guangying.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 用户认证接口控制器
 * <p>
 * - POST /api/auth/login    → 登录
 * - POST /api/auth/register → 注册
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Resource
    private UserService userService;

    /**
     * 用户注册
     */
    @PostMapping("/register")
    public Result<UserVO> register(@Validated @RequestBody UserRegisterDTO dto) {
        UserVO user = userService.register(dto);
        return Result.success(user);
    }

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public Result<UserVO> login(@Validated @RequestBody UserLoginDTO dto) {
        UserVO user = userService.login(dto);
        return Result.success(user);
    }

    /**
     * 获取当前用户信息（需要在Header传 Authorization: Bearer xxx）
     */
    @GetMapping("/me")
    public Result<UserVO> me(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        UserVO vo = userService.getUserInfo(userId);
        return vo == null ? Result.fail(404, "用户不存在") : Result.success(vo);
    }
}
