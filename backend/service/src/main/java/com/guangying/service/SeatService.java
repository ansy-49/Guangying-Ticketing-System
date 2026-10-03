package com.guangying.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guangying.common.constants.CacheConstants;
import com.guangying.dao.mapper.*;
import com.guangying.domain.enums.OrderStatusEnum;
import com.guangying.domain.enums.ResponseCodeEnum;
import com.guangying.domain.exception.BizException;
import com.guangying.domain.model.dto.LockSeatsDTO;
import com.guangying.domain.model.po.*;
import com.guangying.domain.model.vo.OrderVO;
import com.guangying.domain.model.vo.SeatLayoutVO;
import com.guangying.service.infrastructure.OutboxService;
import com.guangying.service.infrastructure.DistributedLockService;
import com.guangying.service.infrastructure.QueueService;
import com.guangying.service.infrastructure.SeatLockScriptService;
import com.guangying.service.infrastructure.SeatSoldService;
import com.guangying.service.infrastructure.TransactionCallbacks;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 座位服务 — 目标架构：Lua 原子锁 + 同步建单
 *
 * <h3>架构要点（目标设计）：</h3>
 * <pre>
 * 锁座 Lua（争抢在此终结）
 *   └─ 同一 HTTP 请求内：
 *        DB 事务: INSERT seat_lock + INSERT orders → commit
 *   └─ 直接返回 orderNo → 前端跳支付页
 *
 * 争抢点 = Redis Lua 对每个座位的原子锁定
 * 锁一旦拿到，建单无争抢
 *
 * Lua 失败 → 补偿释放 Redis 锁（兜底：单座 Key 自带 15min TTL 自愈）
 * </pre>
 */
@Slf4j
@Service
public class SeatService {

    private final CinemaHallMapper cinemaHallMapper;
    private final SeatLockMapper seatLockMapper;
    private final OrderSeatMapper orderSeatMapper;
    private final OrderMapper orderMapper;
    private final ScheduleMapper scheduleMapper;
    private final CinemaMapper cinemaMapper;
    private final MovieMapper movieMapper;
    private final SeatLockScriptService lockScriptService;
    private final SeatSoldService soldService;
    private final OutboxService outboxService;
    private final QueueService queueService;
    private final TransactionCallbacks transactionCallbacks;
    private final DistributedLockService distributedLockService;
    private final TransactionTemplate purchaseTransaction;

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final DateTimeFormatter ORDER_NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter VO_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public SeatService(CinemaHallMapper cinemaHallMapper,
                       SeatLockMapper seatLockMapper,
                       OrderSeatMapper orderSeatMapper,
                       OrderMapper orderMapper,
                       ScheduleMapper scheduleMapper,
                       CinemaMapper cinemaMapper,
                       MovieMapper movieMapper,
                       SeatLockScriptService lockScriptService,
                       SeatSoldService soldService,
                       OutboxService outboxService,
                       QueueService queueService,
                       TransactionCallbacks transactionCallbacks,
                       DistributedLockService distributedLockService,
                       PlatformTransactionManager transactionManager) {
        this.cinemaHallMapper = cinemaHallMapper;
        this.seatLockMapper = seatLockMapper;
        this.orderSeatMapper = orderSeatMapper;
        this.orderMapper = orderMapper;
        this.scheduleMapper = scheduleMapper;
        this.cinemaMapper = cinemaMapper;
        this.movieMapper = movieMapper;
        this.lockScriptService = lockScriptService;
        this.soldService = soldService;
        this.outboxService = outboxService;
        this.queueService = queueService;
        this.transactionCallbacks = transactionCallbacks;
        this.distributedLockService = distributedLockService;
        this.purchaseTransaction = new TransactionTemplate(transactionManager);
        this.purchaseTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.purchaseTransaction.setTimeout(10);
    }

    // ============================================================
    //  读链路：选座图
    // ============================================================

    /**
     * 获取影厅座位布局（含实时锁定/已售状态）
     *
     * <p>渲染缓存 seat:layout:rendered:{scheduleId} (3~5s TTL) 挡住高频刷新</p>
     */
    public SeatLayoutVO getSeatLayout(Long scheduleId, Long userId) {
        // 1. 查渲染缓存
        if (stringRedisTemplate != null) {
            try {
                String cacheKey = CacheConstants.SEAT_LAYOUT_RENDERED_PREFIX + scheduleId;
                String cached = stringRedisTemplate.opsForValue().get(cacheKey);
                if (cached != null) {
                    return objectMapper.readValue(cached, SeatLayoutVO.class);
                }
            } catch (Exception e) {
                log.warn("[Seat] Failed to read render cache: scheduleId={}", scheduleId, e);
            }
        }

        // 2. 组装
        SeatLayoutVO vo = assembleSeatLayout(scheduleId, userId);

        // 3. 回填渲染缓存
        if (stringRedisTemplate != null && vo != null) {
            try {
                String cacheKey = CacheConstants.SEAT_LAYOUT_RENDERED_PREFIX + scheduleId;
                stringRedisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(vo), 5, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.warn("[Seat] Failed to write render cache: scheduleId={}", scheduleId, e);
            }
        }

        return vo;
    }

    private SeatLayoutVO assembleSeatLayout(Long scheduleId, Long userId) {
        SchedulePO schedule = scheduleMapper.selectById(scheduleId);
        if (schedule == null || schedule.getDeleted() == 1) {
            throw new BizException(ResponseCodeEnum.NOT_FOUND.getCode(), "场次不存在");
        }

        CinemaHallPO hall = cinemaHallMapper.selectByCinemaAndHall(schedule.getCinemaId(), schedule.getHallName());
        if (hall == null) {
            hall = buildDefaultHall(schedule.getCinemaId(), schedule.getHallName());
        }

        SeatLayoutVO vo = new SeatLayoutVO();
        vo.setHallName(hall.getHallName());
        vo.setHallType(hall.getHallType());
        vo.setRows(hall.getSeatRows());
        vo.setCols(hall.getSeatCols());

        List<Integer> aisles = parseIntList(hall.getAisleAfterCol());
        vo.setAisles(aisles);

        List<Integer> coupleRows = parseIntList(hall.getCoupleRows());
        vo.setCoupleRows(coupleRows);

        Set<String> disabledSet = parseDisabledSeats(hall.getDisabledSeats());

        // 已售座位：从 Redis 投影读取（降级时查 DB）
        Set<String> soldSet = soldService.getSoldSeats(scheduleId);

        // 构建二维矩阵（每座查 Redis 锁）
        List<List<SeatLayoutVO.SeatInfo>> seats = new ArrayList<>();
        for (int r = 1; r <= hall.getSeatRows(); r++) {
            List<SeatLayoutVO.SeatInfo> row = new ArrayList<>();
            for (int c = 1; c <= hall.getSeatCols(); c++) {
                SeatLayoutVO.SeatInfo info = new SeatLayoutVO.SeatInfo();
                info.setRow(r);
                info.setCol(c);
                info.setLabel(r + "排" + c + "座");
                info.setCouple(coupleRows.contains(r));

                String key = r + "_" + c;

                if (disabledSet.contains(key)) {
                    info.setStatus(-1);
                } else if (soldSet.contains(key)) {
                    info.setStatus(1); // 已售
                } else {
                    String owner = lockScriptService.getSeatOwner(scheduleId, r, c);
                    if (owner != null) {
                        if (owner.equals(String.valueOf(userId))) {
                            info.setStatus(3); // 我锁定的
                        } else {
                            info.setStatus(2); // 他人锁定
                        }
                    } else {
                        info.setStatus(0); // 可选
                    }
                }
                row.add(info);
            }
            seats.add(row);
        }
        vo.setSeats(seats);
        return vo;
    }

    // ============================================================
    //  写链路：锁座 + 同步建单（目标架构核心）
    // ============================================================

    /**
     * 锁座并同步建单 — 一个请求完成，直接返回 orderNo
     *
     * <p>第一层 @RateLimit AOP（令牌桶）
     * <p>第二层 Redis Lua 原子锁座
     * <p>第三层 DB 事务建单
     *
     * @return 订单 VO（含 orderNo、expireTime、totalPrice 等）
     */
    public OrderVO lockSeatsAndCreateOrder(Long userId, LockSeatsDTO dto) {
        Long scheduleId = dto.getScheduleId();
        List<LockSeatsDTO.SeatPos> seats = dto.getSeats();
        if (seats == null || seats.isEmpty()) {
            throw new BizException(ResponseCodeEnum.BAD_REQUEST.getCode(), "至少选择一个座位");
        }
        Set<String> requestedSeats = seats.stream()
                .map(seat -> seat.getRow() + "_" + seat.getCol())
                .collect(Collectors.toSet());
        if (requestedSeats.size() != seats.size()) {
            throw new BizException(ResponseCodeEnum.BAD_REQUEST.getCode(), "座位列表不能包含重复位置");
        }

        String fingerprint = requestFingerprint(scheduleId, requestedSeats);
        OrderVO replay = findByIdempotencyKey(userId, dto.getIdempotencyKey(), fingerprint);
        if (replay != null) {
            return replay;
        }

        // 幂等键锁覆盖整个数据库事务（包括 commit），避免同一请求在多实例上并发执行。
        // Redis/Redisson 不可用时，数据库唯一索引仍提供最终的 at-most-once 兜底。
        OrderVO result;
        try {
            result = distributedLockService.executeWithBoundedLock(
                    "order:idempotency:" + userId + ":" + dto.getIdempotencyKey(),
                    3,
                    15,
                    () -> purchaseTransaction.execute(status ->
                            lockSeatsAndCreateOrderInTransaction(userId, dto, requestedSeats, fingerprint)));
        } catch (DataIntegrityViolationException e) {
            // Redisson 降级或租约极端到期时，由数据库唯一索引裁决；事务回滚后重放胜者结果。
            replay = findByIdempotencyKey(userId, dto.getIdempotencyKey(), fingerprint);
            if (replay != null) {
                return replay;
            }
            throw new BizException(ResponseCodeEnum.SEAT_LOCKED);
        }
        if (result != null) {
            return result;
        }

        replay = findByIdempotencyKey(userId, dto.getIdempotencyKey(), fingerprint);
        if (replay != null) {
            return replay;
        }
        throw new BizException(ResponseCodeEnum.CONFLICT.getCode(), "相同购票请求正在处理中，请稍后重试");
    }

    private OrderVO lockSeatsAndCreateOrderInTransaction(Long userId,
                                                          LockSeatsDTO dto,
                                                          Set<String> requestedSeats,
                                                          String fingerprint) {
        Long scheduleId = dto.getScheduleId();
        List<LockSeatsDTO.SeatPos> seats = dto.getSeats();

        OrderVO replay = findByIdempotencyKey(userId, dto.getIdempotencyKey(), fingerprint);
        if (replay != null) {
            return replay;
        }

        OrderVO existingOrder = findExistingPendingOrder(userId, scheduleId, requestedSeats);
        if (existingOrder != null) {
            log.info("[Seat] Idempotent retry returned existing order: orderNo={}, userId={}",
                    existingOrder.getOrderNo(), userId);
            return existingOrder;
        }

        // ★ 热门场次准入校验：必须持有入场令牌
        if (!queueService.validateToken(scheduleId, userId)) {
            throw new BizException(ResponseCodeEnum.QUEUE_FULL.getCode(), "请先排队入场");
        }

        // === 第二阶段：Redis Lua 原子锁座 ===
        SeatLockScriptService.LockResult lockResult =
                lockScriptService.lockSeats(scheduleId, seats, userId);
        if (lockResult == SeatLockScriptService.LockResult.CONFLICT) {
            throw new BizException(ResponseCodeEnum.SEAT_LOCKED);
        }
        if (lockResult == SeatLockScriptService.LockResult.UNAVAILABLE) {
            throw new BizException(ResponseCodeEnum.INTERNAL_ERROR.getCode(),
                    "锁座服务暂时不可用，请稍后重试");
        }
        if (lockResult == SeatLockScriptService.LockResult.ALREADY_OWNED) {
            existingOrder = findExistingPendingOrder(userId, scheduleId, requestedSeats);
            if (existingOrder != null) {
                return existingOrder;
            }
            throw new BizException(ResponseCodeEnum.CONFLICT.getCode(),
                    "该座位正在由当前账号的另一笔请求处理，请稍后查看订单");
        }

        // DB 提交成功后才释放 Waiting Room 名额；包括 commit-time 异常在内的回滚
        // 都会在事务真正结束后补偿 Redis 预占锁。
        transactionCallbacks.register(
                () -> queueService.leave(scheduleId, userId),
                () -> {
                    lockScriptService.releaseSeats(scheduleId, seats, userId);
                    log.warn("[Seat] Transaction rolled back, Redis locks compensated: scheduleId={}, userId={}",
                            scheduleId, userId);
                });

        return createOrderInTransaction(userId, dto, fingerprint);
    }

    private OrderVO createOrderInTransaction(Long userId, LockSeatsDTO dto, String fingerprint) {
        Long scheduleId = dto.getScheduleId();
        List<LockSeatsDTO.SeatPos> seats = dto.getSeats();
        int seatCount = seats.size();
        LocalDateTime now = LocalDateTime.now();

        // 1. 验证场次
        SchedulePO schedule = scheduleMapper.selectById(scheduleId);
        if (schedule == null || schedule.getStatus() != 1 || schedule.getDeleted() == 1) {
            throw new BizException(ResponseCodeEnum.NOT_FOUND.getCode(), "场次不存在或已停售");
        }

        // 2. 原子扣减库存：条件 UPDATE 在数据库行锁内完成校验，避免并发下把版本冲突误判为缺货
        int affected = scheduleMapper.deductStock(scheduleId, seatCount);
        if (affected == 0) {
            throw new BizException(ResponseCodeEnum.STOCK_NOT_ENOUGH);
        }

        // 3. 生成 lockToken（不透明 UUID）和 orderNo
        String lockToken = UUID.randomUUID().toString().replace("-", "");
        String orderNo = generateOrderNo(userId);
        LocalDateTime lockUntil = now.plusMinutes(CacheConstants.SEAT_LOCK_MINUTES);

        // 4. INSERT seat_lock（唯一索引 uk(schedule_id, row_num, col_num) 兜底防重）
        for (LockSeatsDTO.SeatPos seat : seats) {
            SeatLockPO lock = new SeatLockPO();
            lock.setScheduleId(scheduleId);
            lock.setRowNum(seat.getRow());
            lock.setColNum(seat.getCol());
            lock.setUserId(userId);
            lock.setLockToken(lockToken);
            lock.setOrderNo(orderNo);
            lock.setLockUntil(lockUntil);
            lock.setStatus(1); // 锁定中
            lock.setCreateTime(now);
            lock.setUpdateTime(now);
            try {
                seatLockMapper.insert(lock);
            } catch (Exception e) {
                throw new BizException(ResponseCodeEnum.SEAT_LOCKED);
            }
        }

        // 5. 获取电影/影院名称（订单快照）
        String movieName = null;
        String cinemaName = null;
        try {
            MoviePO movie = movieMapper.selectById(schedule.getMovieId());
            if (movie != null) movieName = movie.getNm();
            CinemaPO cinema = cinemaMapper.selectById(schedule.getCinemaId());
            if (cinema != null) cinemaName = cinema.getNm();
        } catch (Exception e) {
            log.warn("[Seat] Failed to enrich order snapshot: scheduleId={}", scheduleId, e);
        }

        // 6. 构建座位描述
        String seatsInfo = seats.stream()
                .map(s -> s.getRow() + "排" + s.getCol() + "座")
                .collect(Collectors.joining(","));

        // 7. INSERT orders
        OrderPO order = new OrderPO();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setIdempotencyKey(dto.getIdempotencyKey());
        order.setRequestFingerprint(fingerprint);
        order.setScheduleId(scheduleId);
        order.setLockToken(lockToken);
        order.setMovieName(movieName);
        order.setCinemaName(cinemaName);
        order.setHallName(schedule.getHallName());
        order.setShowTime(schedule.getShowDate() + " " + schedule.getShowTime());
        order.setSeatCount(seatCount);
        order.setSeatsInfo(seatsInfo);
        order.setUnitPrice(schedule.getPrice());
        order.setTotalPrice(schedule.getPrice().multiply(BigDecimal.valueOf(seatCount)));
        order.setStatus(OrderStatusEnum.PENDING.getCode());
        order.setExpireTime(now.plusMinutes(CacheConstants.ORDER_PAY_TIMEOUT_MINUTES));
        orderMapper.insert(order);

        // 8. 写 outbox（与业务同事务，保证不丢）
        Map<String, Object> eventPayload = new LinkedHashMap<>();
        eventPayload.put("type", "ORDER_CREATED");
        eventPayload.put("orderNo", orderNo);
        eventPayload.put("userId", userId);
        eventPayload.put("scheduleId", scheduleId);
        eventPayload.put("seatCount", seatCount);
        eventPayload.put("timestamp", System.currentTimeMillis());
        outboxService.writeEvent("ORDER_CREATED", eventPayload);

        log.info("[Seat] Lock+Order created: orderNo={}, userId={}, scheduleId={}, seats={}, total={}",
                orderNo, userId, scheduleId, seatCount, order.getTotalPrice());

        return toVO(order);
    }

    // ============================================================
    //  私有方法
    // ============================================================

    private OrderVO findByIdempotencyKey(Long userId, String idempotencyKey, String fingerprint) {
        OrderPO existing = orderMapper.selectByUserAndIdempotencyKey(userId, idempotencyKey);
        if (existing == null) {
            return null;
        }
        if (!Objects.equals(existing.getRequestFingerprint(), fingerprint)) {
            throw new BizException(ResponseCodeEnum.CONFLICT.getCode(),
                    "幂等键已用于另一组场次或座位，请生成新的幂等键");
        }
        log.info("[Seat] Idempotent request replayed: orderNo={}, userId={}, key={}",
                existing.getOrderNo(), userId, idempotencyKey);
        return toVO(existing);
    }

    private String requestFingerprint(Long scheduleId, Set<String> requestedSeats) {
        String canonical = scheduleId + ":" + requestedSeats.stream().sorted().collect(Collectors.joining(","));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private OrderVO findExistingPendingOrder(Long userId,
                                             Long scheduleId,
                                             Set<String> requestedSeats) {
        List<OrderPO> pendingOrders = orderMapper.selectPendingByUserAndSchedule(userId, scheduleId);
        for (OrderPO order : pendingOrders) {
            List<SeatLockPO> locks = seatLockMapper.selectLocksByOrderNo(order.getOrderNo());
            Set<String> orderSeats = locks.stream()
                    .filter(lock -> Objects.equals(lock.getStatus(), 1))
                    .map(lock -> lock.getRowNum() + "_" + lock.getColNum())
                    .collect(Collectors.toSet());
            if (orderSeats.equals(requestedSeats)) {
                return toVO(order);
            }
        }
        return null;
    }

    private CinemaHallPO buildDefaultHall(Long cinemaId, String hallName) {
        CinemaHallPO hall = new CinemaHallPO();
        hall.setCinemaId(cinemaId);
        hall.setHallName(hallName);
        hall.setSeatRows(8);
        hall.setSeatCols(12);
        hall.setAisleAfterCol("3,9");
        hall.setCoupleRows("");
        hall.setDisabledSeats("[]");
        hall.setHallType("普通厅");
        return hall;
    }

    private String generateOrderNo(Long userId) {
        String time = LocalDateTime.now().format(ORDER_NO_FMT);
        String userSuffix = String.format("%04d", userId % 10000);
        String random = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        return "MO" + time + userSuffix + random;
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

    private List<Integer> parseIntList(String str) {
        if (str == null || str.isEmpty()) return Collections.emptyList();
        return Arrays.stream(str.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Integer::parseInt)
                .toList();
    }

    private Set<String> parseDisabledSeats(String json) {
        if (json == null || json.isEmpty() || "[]".equals(json)) return Collections.emptySet();
        try {
            List<List<Integer>> disabled = objectMapper.readValue(json, new TypeReference<>() {});
            return disabled.stream()
                    .map(pair -> pair.get(0) + "_" + pair.get(1))
                    .collect(Collectors.toSet());
        } catch (JsonProcessingException e) {
            log.warn("解析不可用座位JSON失败: {}", json, e);
            return Collections.emptySet();
        }
    }
}
