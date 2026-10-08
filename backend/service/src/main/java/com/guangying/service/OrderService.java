package com.guangying.service;

import com.guangying.dao.mapper.OrderMapper;
import com.guangying.domain.enums.OrderStatusEnum;
import com.guangying.domain.model.po.OrderPO;
import com.guangying.domain.model.vo.OrderVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 订单查询、用户取消与超时扫描兜底。
 *
 * <p>超时关单的主触发器是 RocketMQ 定时消息；本服务的扫描器只处理漏消息、
 * Broker 长时间不可用或运维恢复后的积压。</p>
 */
@Slf4j
@Service
public class OrderService {

    private final OrderMapper orderMapper;
    private final OrderCloseService orderCloseService;
    private final TransactionTemplate requiresNewTransaction;

    @Value("${guangying.order.timeout-scan-batch-size:100}")
    private int timeoutScanBatchSize;

    @Value("${guangying.order.timeout-scan-max-batches:10}")
    private int timeoutScanMaxBatches;

    private static final DateTimeFormatter VO_TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public OrderService(OrderMapper orderMapper,
                        OrderCloseService orderCloseService,
                        PlatformTransactionManager transactionManager) {
        this.orderMapper = orderMapper;
        this.orderCloseService = orderCloseService;
        this.requiresNewTransaction = new TransactionTemplate(transactionManager);
        this.requiresNewTransaction.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.requiresNewTransaction.setTimeout(8);
    }

    public void cancelOrder(Long userId, String orderNo) {
        orderCloseService.cancelByUser(userId, orderNo);
    }

    /**
     * 数据库兜底扫描：有序、分批、单订单独立事务。
     * 条件状态更新保证多实例即使扫描到同一订单也只有一个实例返还库存。
     */
    @Scheduled(fixedDelayString = "${guangying.order.timeout-scan-interval-ms:60000}")
    public void cancelExpiredOrders() {
        int batchSize = Math.max(1, Math.min(timeoutScanBatchSize, 500));
        int maxBatches = Math.max(1, Math.min(timeoutScanMaxBatches, 100));
        int closed = 0;

        for (int batch = 0; batch < maxBatches; batch++) {
            List<OrderPO> expired = orderMapper.selectExpiredPendingOrders(
                    LocalDateTime.now(), batchSize);
            if (expired.isEmpty()) {
                break;
            }

            for (OrderPO order : expired) {
                try {
                    OrderCloseService.CloseResult result = requiresNewTransaction.execute(
                            status -> orderCloseService.closeExpired(
                                    order.getOrderNo(), OrderCloseService.REASON_DB_SCAN_FALLBACK));
                    if (result == OrderCloseService.CloseResult.CLOSED) {
                        closed++;
                    }
                } catch (Exception e) {
                    log.error("[Order] Fallback scan failed: orderNo={}", order.getOrderNo(), e);
                }
            }
            if (expired.size() < batchSize) {
                break;
            }
        }

        if (closed > 0) {
            log.warn("[Order] Fallback scanner closed {} expired orders; inspect delayed-message health", closed);
        }
    }

    public List<OrderVO> getUserOrders(Long userId, int page, int size) {
        int offset = (page - 1) * size;
        return orderMapper.selectByUserIdWithPage(userId, offset, size).stream()
                .map(this::toVO)
                .toList();
    }

    private OrderVO toVO(OrderPO po) {
        OrderVO vo = new OrderVO();
        vo.setId(po.getId());
        vo.setOrderNo(po.getOrderNo());
        vo.setLockToken(po.getLockToken());
        vo.setMovieName(po.getMovieName());
        vo.setCinemaName(po.getCinemaName());
        vo.setHallName(po.getHallName());
        vo.setShowTime(po.getShowTime());
        vo.setSeatCount(po.getSeatCount());
        vo.setSeatsInfo(po.getSeatsInfo());
        vo.setUnitPrice(po.getUnitPrice());
        vo.setTotalPrice(po.getTotalPrice());
        vo.setStatus(po.getStatus());
        vo.setStatusDesc(OrderStatusEnum.of(po.getStatus()).getDesc());
        vo.setScheduleId(po.getScheduleId());
        if (po.getCreateTime() != null) {
            vo.setCreateTime(po.getCreateTime().format(VO_TIME_FMT));
        }
        if (po.getPayTime() != null) {
            vo.setPayTime(po.getPayTime().format(VO_TIME_FMT));
        }
        if (po.getExpireTime() != null) {
            vo.setExpireTime(po.getExpireTime().format(VO_TIME_FMT));
        }
        return vo;
    }
}
