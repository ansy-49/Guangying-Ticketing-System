# Seat Lock Load Test Report

- Run ID: `muf40rfuv64c`
- Time: 2026-09-24T05:47:17.133Z
- Base URL: `http://localhost:18081`
- Users: 100
- User setup: 2127.94 ms

| Scenario | Requests | Success / Expected | Oversold | RPS | P50 ms | P95 ms | P99 ms | Max ms |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| same-seat contention | 100 | 1/1 | 0 | 357.78 | 247.56 | 269.49 | 272.28 | 273.85 |
| distinct-seat throughput | 100 | 100/100 | 0 | 96.42 | 566.32 | 983.11 | 1024.99 | 1033.88 |

## Response codes

- same-seat contention: 200=1, 462=99
- distinct-seat throughput: 200=100

## Verification

- Same-seat final status: 2
- Distinct-seat locked count observed after cache expiry: 100
- Same-seat invariant: exactly one successful order and zero oversell.
- Distinct-seat target: one successful order per unique available seat.
