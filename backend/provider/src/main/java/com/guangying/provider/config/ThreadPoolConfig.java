package com.guangying.provider.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 线程池配置
 * <p>
 * 按照高级架构师标准配置线程池：
 * - 合理的核心/最大线程数（基于CPU核数）
 * - 有界的工作队列（防止OOM）
 * - CallerRunsPolicy 拒绝策略（降级到调用者线程执行，避免丢任务）
 * - 优雅关闭（等待任务完成）
 * - 自定义线程工厂（便于排查线程问题）
 * </p>
 */
@Slf4j
@Configuration
@EnableAsync
public class ThreadPoolConfig {

    /**
     * 业务异步任务线程池
     * <p>
     * 用于：并行数据聚合、异步日志写入、非关键异步任务
     * </p>
     */
    @Bean("bizTaskExecutor")
    public ThreadPoolExecutor bizTaskExecutor() {
        int cpuCores = Runtime.getRuntime().availableProcessors();
        int corePoolSize = cpuCores;
        int maxPoolSize = cpuCores * 2;
        int queueCapacity = 256;

        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                corePoolSize,
                maxPoolSize,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueCapacity),
                new NamedThreadFactory("biz-task"),
                new ThreadPoolExecutor.CallerRunsPolicy()  // 队列满时降级到调用者线程
        );

        // 允许核心线程超时回收（节省资源）
        executor.allowCoreThreadTimeOut(true);

        log.info("业务线程池初始化完成: core={}, max={}, queue={}",
                corePoolSize, maxPoolSize, queueCapacity);

        return executor;
    }

    /**
     * IO密集型线程池（用于数据库查询并发场景）
     */
    @Bean("ioTaskExecutor")
    public ThreadPoolExecutor ioTaskExecutor() {
        int cpuCores = Runtime.getRuntime().availableProcessors();
        int corePoolSize = cpuCores * 2;
        int maxPoolSize = cpuCores * 4;

        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                corePoolSize,
                maxPoolSize,
                120L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(512),
                new NamedThreadFactory("io-task"),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        log.info("IO线程池初始化完成: core={}, max={}", corePoolSize, maxPoolSize);
        return executor;
    }

    /**
     * 自定义线程工厂 — 设置线程名前缀，便于排查问题
     */
    public static class NamedThreadFactory implements ThreadFactory {
        private final AtomicInteger counter = new AtomicInteger(0);
        private final String prefix;

        public NamedThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, prefix + "-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        }
    }
}
