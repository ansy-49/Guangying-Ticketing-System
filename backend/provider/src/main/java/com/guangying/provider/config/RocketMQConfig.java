package com.guangying.provider.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * RocketMQ 自动配置入口。
 *
 * <p>RocketMQ 由 rocketmq-spring-boot-starter 自动配置：</p>
 * <ul>
 *   <li>普通 Topic 可在开发环境自动创建；定时消息 Topic 必须显式创建为 DELAY 类型</li>
 *   <li>Producer 自动注册（配置 name-server + group 即可）</li>
 *   <li>Consumer 通过 @RocketMQMessageListener 注解自动注册</li>
 * </ul>
 *
 * <h3>本项目中的职责：</h3>
 * <pre>
 * 1. Outbox 投递订单创建、支付和取消事件
 * 2. 消费失败由 Broker 重试，消费端通过 eventId 落库去重
 * 3. 下游异步维护 Redis 查询投影并清理缓存
 * 4. ORDER_TIMEOUT_CHECK 通过专用 DELAY Topic 定时投递，数据库扫描仅作漏消息兜底
 * </pre>
 *
 * <p>仅在配置了 rocketmq.name-server 时激活</p>
 */
@Configuration
@ConditionalOnProperty(name = "rocketmq.name-server")
public class RocketMQConfig {

    // RocketMQ Spring Boot Starter 自动配置了：
    // 1. RocketMQTemplate（生产者模板，类似 RabbitTemplate）
    // 2. DefaultMQProducer（底层生产者实例）
    // 3. @RocketMQMessageListener 标注的消费者自动注册
    //
    // 无需手动声明 Queue/Exchange/Binding（不同于 RabbitMQ）
    // RocketMQ 的 Topic 在 Broker 端自动创建或通过控制台预创建
}
