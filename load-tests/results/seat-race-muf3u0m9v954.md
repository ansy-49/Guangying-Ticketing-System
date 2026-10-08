# Seat Lock Load Test Report

- Run ID: `muf3u0m9v954`
- Time: 2026-09-24T05:42:02.207Z
- Base URL: `http://localhost:18081`
- Users: 100
- User setup: 2246.29 ms

| Scenario | Requests | Success / Expected | Oversold | RPS | P50 ms | P95 ms | P99 ms | Max ms |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| same-seat contention | 100 | 1/1 | 0 | 378.01 | 231.19 | 260.6 | 262.62 | 263.62 |
| distinct-seat throughput | 100 | 6/100 | 0 | 163.79 | 335.09 | 571 | 599.21 | 606.92 |

## Response codes

- same-seat contention: 200=1, 462=99
- distinct-seat throughput: 200=6, 460=94

## Verification

- Same-seat final status: 2
- Distinct-seat locked count observed after cache expiry: 6
- Same-seat invariant: exactly one successful order and zero oversell.
- Distinct-seat target: one successful order per unique available seat.
