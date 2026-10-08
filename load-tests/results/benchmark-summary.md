# Seat-lock concurrency benchmark

Date: 2026-09-24 (Asia/Shanghai)

## Environment

- Backend: Spring Boot 3.2.3, Java 21, 192-384 MB heap
- Database: MySQL 8.0, 200 max connections, 128 MB InnoDB buffer pool
- Cache and distributed lock: Redis 7
- Messaging: RocketMQ 5.1.4
- Load generator: Node.js, 100 authenticated users released concurrently
- Isolation: dedicated containers, loopback-only ports, tmpfs data

## Result

| Scenario | Before fix | After fix | After-fix throughput | P50 | P95 | P99 | Oversold |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 100 users compete for one seat | 1/1 | 1/1 | 357.78 req/s | 247.56 ms | 269.49 ms | 272.28 ms | 0 |
| 100 users choose 100 distinct seats | 6/100 | 100/100 | 96.42 req/s | 566.32 ms | 983.11 ms | 1024.99 ms | 0 |

## Finding and fix

The original stock update required the version read immediately before it to remain unchanged. Concurrent orders for different seats updated the same schedule row, so 94 valid requests were incorrectly reported as out of stock.

The update now performs the stock check and decrement atomically in one conditional SQL statement. MySQL serializes changes to the schedule row, while `available_seats >= seatCount`, the Redis Lua seat lock, and the database seat uniqueness constraint continue to prevent overselling.

## Database reconciliation after the fixed run

| Schedule | Orders | Locked rows | Distinct seats | Remaining / Total | Version |
| ---: | ---: | ---: | ---: | ---: | ---: |
| 35 (same-seat race) | 1 | 1 | 1 | 199 / 200 | 1 |
| 36 (distinct seats) | 100 | 100 | 100 | 50 / 150 | 100 |

No duplicate `(schedule_id, row_num, col_num)` rows were found.

## Scope

This is a local single-instance concurrency benchmark intended to validate correctness and expose bottlenecks. It is not a production capacity claim; a production benchmark should use fixed server hardware, an external load generator, warm-up phases, sustained load, and host-level CPU, memory, GC, database, and Redis metrics.
