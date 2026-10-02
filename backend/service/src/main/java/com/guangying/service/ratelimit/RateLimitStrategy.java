package com.guangying.service.ratelimit;

import com.guangying.common.enums.RateLimitAlgorithm;

public interface RateLimitStrategy {

    RateLimitAlgorithm algorithm();

    boolean isAllowed(RateLimitContext context);
}
