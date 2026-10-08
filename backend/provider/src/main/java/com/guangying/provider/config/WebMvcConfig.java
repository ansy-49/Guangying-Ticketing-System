package com.guangying.provider.config;

import com.guangying.provider.interceptor.JwtAuthInterceptor;
import com.guangying.provider.interceptor.RequestContextInterceptor;
import com.guangying.provider.interceptor.RequestLogInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.annotation.Resource;

/**
 * Web MVC 配置
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Resource
    private RequestContextInterceptor requestContextInterceptor;

    @Resource
    private RequestLogInterceptor requestLogInterceptor;

    @Resource
    private JwtAuthInterceptor jwtAuthInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 请求上下文 → 日志 → 鉴权，order 明确保证拦截链顺序
        registry.addInterceptor(requestContextInterceptor)
                .order(0)
                .addPathPatterns("/**")
                .excludePathPatterns("/h2-console/**", "/static/**");

        // 请求日志拦截器 — 全局
        registry.addInterceptor(requestLogInterceptor)
                .order(10)
                .addPathPatterns("/**")
                .excludePathPatterns("/h2-console/**", "/static/**");

        // JWT 认证拦截器 — 仅保护需要登录的接口
        registry.addInterceptor(jwtAuthInterceptor)
                .order(20)
                .addPathPatterns(
                        "/api/auth/me",   // 当前用户
                        "/api/order/**",    // 订单操作
                        "/api/seat/**",     // 座位操作
                        "/api/payment/**",  // 支付操作
                        "/api/queue/**",    // 排队操作
                        "/api/admin/**",    // 管理操作（Controller内继续校验ADMIN角色）
                        "/ajax/wish/**"     // 想看操作
                )
                .excludePathPatterns(
                        "/ajax/wish/check/**",  // 检查想看状态无需登录
                        "/api/seat/layout"      // 查看座位布局无需登录
                );
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/static/**")
                .addResourceLocations("classpath:/static/");
    }
}
