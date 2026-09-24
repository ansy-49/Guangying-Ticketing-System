import { mkdir, writeFile } from 'node:fs/promises'
import { performance } from 'node:perf_hooks'
import { resolve } from 'node:path'

const cli = Object.fromEntries(
  process.argv.slice(2).map((arg) => {
    const [key, value = 'true'] = arg.replace(/^--/, '').split('=')
    return [key, value]
  }),
)

const config = {
  baseUrl: cli['base-url'] || 'http://localhost:18080',
  users: Number(cli.users || 100),
  setupConcurrency: Number(cli['setup-concurrency'] || 8),
  sameSeatScheduleId: Number(cli['same-seat-schedule'] || 35),
  distinctSeatScheduleId: Number(cli['distinct-seat-schedule'] || 36),
  timeoutMs: Number(cli.timeout || 15000),
}

const runId = `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`
const password = 'LoadTest123'

function percentile(values, p) {
  if (!values.length) return 0
  const sorted = [...values].sort((a, b) => a - b)
  return sorted[Math.min(sorted.length - 1, Math.ceil((p / 100) * sorted.length) - 1)]
}

function rounded(value) {
  return Math.round(value * 100) / 100
}

async function request(path, options = {}) {
  const started = performance.now()
  try {
    const response = await fetch(`${config.baseUrl}${path}`, {
      ...options,
      headers: {
        'Content-Type': 'application/json',
        ...(options.headers || {}),
      },
      signal: AbortSignal.timeout(config.timeoutMs),
    })
    const text = await response.text()
    let body
    try {
      body = JSON.parse(text)
    } catch {
      body = { code: response.status, message: text.slice(0, 200) }
    }
    return {
      ok: true,
      status: response.status,
      body,
      durationMs: performance.now() - started,
    }
  } catch (error) {
    return {
      ok: false,
      status: 0,
      body: { code: 0, message: error.message },
      durationMs: performance.now() - started,
    }
  }
}

async function mapWithConcurrency(items, concurrency, worker) {
  const results = new Array(items.length)
  let cursor = 0
  async function consume() {
    while (true) {
      const index = cursor++
      if (index >= items.length) return
      results[index] = await worker(items[index], index)
    }
  }
  await Promise.all(Array.from({ length: Math.min(concurrency, items.length) }, consume))
  return results
}

async function createUser(index) {
  const account = `lt_${runId}_${String(index).padStart(3, '0')}`
  const register = await request('/api/auth/register', {
    method: 'POST',
    body: JSON.stringify({
      account,
      password,
      userNick: `LoadTest ${index}`,
      inviteCode: 'lpf',
    }),
  })
  if (register.body?.code !== 200) {
    throw new Error(`register ${account}: ${register.body?.code} ${register.body?.message}`)
  }

  const login = await request('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ account, password }),
  })
  const token = login.body?.data?.token
  if (login.body?.code !== 200 || !token) {
    throw new Error(`login ${account}: ${login.body?.code} ${login.body?.message}`)
  }
  return { account, token }
}

async function getLayout(scheduleId) {
  const response = await request(`/api/seat/layout?scheduleId=${scheduleId}`)
  if (response.body?.code !== 200) {
    throw new Error(`layout ${scheduleId}: ${response.body?.code} ${response.body?.message}`)
  }
  return response.body.data
}

function availableSeats(layout) {
  return layout.seats.flat().filter((seat) => seat.status === 0)
}

async function lockSeat(user, scheduleId, seat) {
  const response = await request('/api/seat/lock', {
    method: 'POST',
    headers: { Authorization: `Bearer ${user.token}` },
    body: JSON.stringify({
      scheduleId,
      seats: [{ row: seat.row, col: seat.col }],
    }),
  })
  return {
    account: user.account,
    seat: `${seat.row}_${seat.col}`,
    status: response.status,
    code: response.body?.code ?? 0,
    message: response.body?.message || '',
    orderNo: response.body?.data?.orderNo || null,
    durationMs: response.durationMs,
  }
}

function summarize(name, responses, wallTimeMs, expectedSuccesses) {
  const durations = responses.map((item) => item.durationMs)
  const successes = responses.filter((item) => item.code === 200 && item.orderNo)
  const codeCounts = responses.reduce((counts, item) => {
    counts[item.code] = (counts[item.code] || 0) + 1
    return counts
  }, {})
  const uniqueOrders = new Set(successes.map((item) => item.orderNo)).size
  return {
    name,
    requests: responses.length,
    expectedSuccesses,
    successes: successes.length,
    uniqueOrders,
    oversold: Math.max(0, successes.length - expectedSuccesses),
    successRate: rounded((successes.length / responses.length) * 100),
    wallTimeMs: rounded(wallTimeMs),
    throughputRps: rounded(responses.length / (wallTimeMs / 1000)),
    latencyMs: {
      min: rounded(Math.min(...durations)),
      avg: rounded(durations.reduce((sum, value) => sum + value, 0) / durations.length),
      p50: rounded(percentile(durations, 50)),
      p95: rounded(percentile(durations, 95)),
      p99: rounded(percentile(durations, 99)),
      max: rounded(Math.max(...durations)),
    },
    responseCodes: codeCounts,
    responses,
  }
}

async function runScenario(name, users, scheduleId, seats, expectedSuccesses) {
  const started = performance.now()
  const responses = await Promise.all(
    users.map((user, index) => lockSeat(user, scheduleId, seats[index])),
  )
  return summarize(name, responses, performance.now() - started, expectedSuccesses)
}

function markdownReport(result) {
  const rows = result.scenarios.map((scenario) =>
    `| ${scenario.name} | ${scenario.requests} | ${scenario.successes}/${scenario.expectedSuccesses} | ${scenario.oversold} | ${scenario.throughputRps} | ${scenario.latencyMs.p50} | ${scenario.latencyMs.p95} | ${scenario.latencyMs.p99} | ${scenario.latencyMs.max} |`,
  )
  const codeSections = result.scenarios.map((scenario) =>
    `- ${scenario.name}: ${Object.entries(scenario.responseCodes).map(([code, count]) => `${code}=${count}`).join(', ')}`,
  )
  return `# Seat Lock Load Test Report

- Run ID: \`${result.runId}\`
- Time: ${result.timestamp}
- Base URL: \`${result.config.baseUrl}\`
- Users: ${result.config.users}
- User setup: ${result.setupDurationMs} ms

| Scenario | Requests | Success / Expected | Oversold | RPS | P50 ms | P95 ms | P99 ms | Max ms |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
${rows.join('\n')}

## Response codes

${codeSections.join('\n')}

## Verification

- Same-seat final status: ${result.verification.sameSeatStatus}
- Distinct-seat locked count observed after cache expiry: ${result.verification.distinctLockedCount}
- Same-seat invariant: exactly one successful order and zero oversell.
- Distinct-seat target: one successful order per unique available seat.
`
}

async function main() {
  const health = await request('/ajax/movieOnInfoList')
  if (!health.ok || health.status !== 200) {
    throw new Error(`backend unavailable at ${config.baseUrl}`)
  }

  console.log(`[setup] creating ${config.users} users with concurrency ${config.setupConcurrency}`)
  const setupStarted = performance.now()
  const users = await mapWithConcurrency(
    Array.from({ length: config.users }, (_, index) => index),
    config.setupConcurrency,
    createUser,
  )
  const setupDurationMs = rounded(performance.now() - setupStarted)

  const sameLayout = await getLayout(config.sameSeatScheduleId)
  const sameSeat = availableSeats(sameLayout)[0]
  if (!sameSeat) throw new Error('no available seat for same-seat scenario')

  console.log(`[run] ${config.users} users racing for seat ${sameSeat.row}_${sameSeat.col}`)
  const sameSeatScenario = await runScenario(
    'same-seat contention',
    users,
    config.sameSeatScheduleId,
    users.map(() => sameSeat),
    1,
  )

  const distinctLayout = await getLayout(config.distinctSeatScheduleId)
  const distinctAvailable = availableSeats(distinctLayout)
  const distinctUsers = users.slice(0, Math.min(users.length, distinctAvailable.length))
  const distinctSeats = distinctAvailable.slice(0, distinctUsers.length)

  console.log(`[run] ${distinctUsers.length} users locking distinct seats`)
  const distinctSeatScenario = await runScenario(
    'distinct-seat throughput',
    distinctUsers,
    config.distinctSeatScheduleId,
    distinctSeats,
    distinctUsers.length,
  )

  await new Promise((resolvePromise) => setTimeout(resolvePromise, 6000))
  const sameFinalLayout = await getLayout(config.sameSeatScheduleId)
  const distinctFinalLayout = await getLayout(config.distinctSeatScheduleId)
  const sameFinalSeat = sameFinalLayout.seats
    .flat()
    .find((seat) => seat.row === sameSeat.row && seat.col === sameSeat.col)
  const distinctLockedCount = distinctFinalLayout.seats
    .flat()
    .filter((seat) => seat.status === 2 || seat.status === 3).length

  const result = {
    runId,
    timestamp: new Date().toISOString(),
    config,
    setupDurationMs,
    scenarios: [sameSeatScenario, distinctSeatScenario],
    verification: {
      sameSeatStatus: sameFinalSeat?.status,
      distinctLockedCount,
    },
  }

  const resultsDir = resolve('load-tests', 'results')
  await mkdir(resultsDir, { recursive: true })
  const basename = `seat-race-${runId}`
  await writeFile(resolve(resultsDir, `${basename}.json`), JSON.stringify(result, null, 2))
  await writeFile(resolve(resultsDir, `${basename}.md`), markdownReport(result))

  console.log(JSON.stringify({
    runId,
    setupDurationMs,
    scenarios: result.scenarios.map(({ responses, ...summary }) => summary),
    verification: result.verification,
    report: `load-tests/results/${basename}.md`,
  }, null, 2))
}

main().catch((error) => {
  console.error(error)
  process.exitCode = 1
})
