package com.guangying.provider.config;

import com.guangying.provider.interceptor.JwtAuthInterceptor;
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
    private RequestLogInterceptor requestLogInterceptor;

    @Resource
    private JwtAuthInterceptor jwtAuthInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 请求日志拦截器 — 全局
        registry.addInterceptor(requestLogInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/h2-console/**", "/static/**");

        // JWT 认证拦截器 — 仅保护需要登录的接口
        registry.addInterceptor(jwtAuthInterceptor)
                .addPathPatterns(
                        "/api/order/**",    // 订单操作
                        "/api/seat/**",     // 座位操作
                        "/api/payment/**",  // 支付操作
                        "/api/queue/**",    // 排队操作
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
