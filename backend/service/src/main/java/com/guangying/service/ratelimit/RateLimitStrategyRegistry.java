package com.guangying.service.ratelimit;

import com.guangying.common.enums.RateLimitAlgorithm;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 限流策略注册表。
 *
 * <p>组合了策略模式与注册表模式：Spring 自动发现策略，注册表负责路由和重复校验。</p>
 */
@Component
public class RateLimitStrategyRegistry {

    private final Map<RateLimitAlgorithm, RateLimitStrategy> strategies;

    public RateLimitStrategyRegistry(List<RateLimitStrategy> strategyList) {
        EnumMap<RateLimitAlgorithm, RateLimitStrategy> registered =
                new EnumMap<>(RateLimitAlgorithm.class);
        for (RateLimitStrategy strategy : strategyList) {
            RateLimitStrategy previous = registered.put(strategy.algorithm(), strategy);
            if (previous != null) {
                throw new IllegalStateException(
                        "Duplicate rate-limit strategy: " + strategy.algorithm());
            }
        }
        this.strategies = Map.copyOf(registered);
    }

    public RateLimitStrategy resolve(RateLimitAlgorithm algorithm) {
        RateLimitStrategy strategy = strategies.get(algorithm);
        if (strategy == null) {
            throw new IllegalArgumentException("Unsupported rate-limit algorithm: " + algorithm);
        }
        return strategy;
    }
}
