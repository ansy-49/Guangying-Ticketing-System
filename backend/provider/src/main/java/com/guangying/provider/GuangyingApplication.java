package com.guangying.provider;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 光影后端服务启动类
 */
@SpringBootApplication(scanBasePackages = "com.guangying")
@MapperScan("com.guangying.dao.mapper")
@EnableAsync
@EnableScheduling
@EnableTransactionManagement
public class GuangyingApplication {

    public static void main(String[] args) {
        SpringApplication.run(GuangyingApplication.class, args);
    }
}
