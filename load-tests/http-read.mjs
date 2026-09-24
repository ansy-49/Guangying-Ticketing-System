import { performance } from 'node:perf_hooks';
import { writeFile, mkdir } from 'node:fs/promises';

function arg(name, fallback) {
  const index = process.argv.indexOf(`--${name}`);
  if (index >= 0) return process.argv[index + 1];
  const inline = process.argv.find((value) => value.startsWith(`--${name}=`));
  return inline ? inline.slice(name.length + 3) : fallback;
}

const baseUrl = arg('base-url', 'http://localhost:18082');
const path = arg('path', '/ajax/movieOnInfoList');
const requests = Number(arg('requests', '3000'));
const concurrency = Number(arg('concurrency', '100'));
const warmup = Number(arg('warmup', '100'));
const label = arg('label', 'read-benchmark');

if (![requests, concurrency, warmup].every(Number.isFinite) || requests < 1 || concurrency < 1 || warmup < 0) {
  throw new Error('requests/concurrency must be positive numbers and warmup must be non-negative');
}

const url = new URL(path, baseUrl).toString();

async function requestOnce() {
  const started = performance.now();
  const response = await fetch(url);
  await response.arrayBuffer();
  return { status: response.status, latencyMs: performance.now() - started };
}

async function runPool(total, workers) {
  const results = new Array(total);
  let cursor = 0;
  await Promise.all(Array.from({ length: Math.min(workers, total) }, async () => {
    while (true) {
      const index = cursor++;
      if (index >= total) return;
      try {
        results[index] = await requestOnce();
      } catch (error) {
        results[index] = { status: 0, latencyMs: 0, error: String(error) };
      }
    }
  }));
  return results;
}

function percentile(sorted, p) {
  if (sorted.length === 0) return 0;
  const index = Math.min(sorted.length - 1, Math.ceil((p / 100) * sorted.length) - 1);
  return sorted[index];
}

console.log(`[warmup] ${warmup} requests to ${url}`);
await runPool(warmup, Math.min(concurrency, 20));

console.log(`[run] ${requests} requests at concurrency ${concurrency}`);
const started = performance.now();
const results = await runPool(requests, concurrency);
const wallTimeMs = performance.now() - started;
const successful = results.filter((item) => item.status >= 200 && item.status < 300);
const latencies = successful.map((item) => item.latencyMs).sort((a, b) => a - b);
const codes = results.reduce((acc, item) => {
  acc[item.status] = (acc[item.status] ?? 0) + 1;
  return acc;
}, {});

const report = {
  label,
  time: new Date().toISOString(),
  url,
  requests,
  concurrency,
  warmup,
  successes: successful.length,
  successRate: Number((successful.length * 100 / requests).toFixed(2)),
  wallTimeMs: Number(wallTimeMs.toFixed(2)),
  throughputRps: Number((requests * 1000 / wallTimeMs).toFixed(2)),
  latencyMs: {
    avg: Number((latencies.reduce((sum, value) => sum + value, 0) / Math.max(latencies.length, 1)).toFixed(2)),
    p50: Number(percentile(latencies, 50).toFixed(2)),
    p95: Number(percentile(latencies, 95).toFixed(2)),
    p99: Number(percentile(latencies, 99).toFixed(2)),
    max: Number((latencies.at(-1) ?? 0).toFixed(2)),
  },
  responseCodes: codes,
};

await mkdir('load-tests/results', { recursive: true });
const safeLabel = label.replace(/[^a-z0-9_-]+/gi, '-').toLowerCase();
const file = `load-tests/results/read-${safeLabel}-${Date.now()}.json`;
await writeFile(file, `${JSON.stringify(report, null, 2)}\n`);
console.log(JSON.stringify({ ...report, report: file }, null, 2));

if (successful.length !== requests) process.exitCode = 1;
