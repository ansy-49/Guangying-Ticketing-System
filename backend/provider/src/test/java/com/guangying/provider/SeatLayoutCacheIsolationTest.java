package com.guangying.provider;

import com.guangying.dao.mapper.CinemaHallMapper;
import com.guangying.dao.mapper.CinemaMapper;
import com.guangying.dao.mapper.MovieMapper;
import com.guangying.dao.mapper.OrderMapper;
import com.guangying.dao.mapper.OrderSeatMapper;
import com.guangying.dao.mapper.ScheduleMapper;
import com.guangying.dao.mapper.SeatLockMapper;
import com.guangying.domain.model.vo.SeatLayoutVO;
import com.guangying.service.SeatService;
import com.guangying.service.cache.SeatLayoutCacheService;
import com.guangying.service.infrastructure.DistributedLockService;
import com.guangying.service.infrastructure.OutboxService;
import com.guangying.service.infrastructure.QueueService;
import com.guangying.service.infrastructure.SeatLockScriptService;
import com.guangying.service.infrastructure.SeatSoldService;
import com.guangying.service.infrastructure.TransactionCallbacks;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SeatLayoutCacheIsolationTest {

    @Test
    void sharedRenderedCacheDoesNotLeakMyLockedStatusToAnotherUser() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        // 模拟升级前可能短暂残留的个性化缓存；新代码必须先恢复为公共状态。
        when(values.get("seat:layout:rendered:9"))
                .thenReturn("{\"rows\":1,\"cols\":1,\"seats\":[[{\"row\":1,\"col\":1,\"status\":3}]]}");

        SeatLockScriptService lockScript = mock(SeatLockScriptService.class);
        when(lockScript.getSeatOwner(9L, 1, 1)).thenReturn("1");
        SeatService service = new SeatService(
                mock(CinemaHallMapper.class), mock(SeatLockMapper.class),
                mock(OrderSeatMapper.class), mock(OrderMapper.class),
                mock(ScheduleMapper.class), mock(CinemaMapper.class),
                mock(MovieMapper.class), lockScript, mock(SeatSoldService.class),
                mock(OutboxService.class), mock(QueueService.class),
                mock(TransactionCallbacks.class), mock(DistributedLockService.class),
                mock(SeatLayoutCacheService.class),
                mock(PlatformTransactionManager.class));
        ReflectionTestUtils.setField(service, "stringRedisTemplate", redis);

        SeatLayoutVO ownerView = service.getSeatLayout(9L, 1L);
        SeatLayoutVO otherView = service.getSeatLayout(9L, 2L);

        assertEquals(3, ownerView.getSeats().get(0).get(0).getStatus());
        assertEquals(2, otherView.getSeats().get(0).get(0).getStatus());
    }
}
