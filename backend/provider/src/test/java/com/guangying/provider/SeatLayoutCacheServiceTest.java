package com.guangying.provider;

import com.guangying.service.cache.SeatLayoutCacheService;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SeatLayoutCacheServiceTest {

    @Test
    void invalidatesRenderedLayoutBySchedule() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        SeatLayoutCacheService service = new SeatLayoutCacheService();
        ReflectionTestUtils.setField(service, "stringRedisTemplate", redis);

        service.invalidate(42L);

        verify(redis).delete("seat:layout:rendered:42");
    }
}
