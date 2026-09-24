# Load tests

The seat-race test creates isolated users, then runs two concurrent scenarios:

1. all users compete for the same seat, where exactly one order may succeed;
2. all users select different available seats, measuring normal concurrent order throughput.

Start the isolated, ephemeral Docker backend on port `18080`, then run:

```powershell
docker compose -f load-tests/docker-compose.load.yml up -d --build
node load-tests/seat-race.mjs --base-url=http://localhost:18080 --users=100
docker compose -f load-tests/docker-compose.load.yml stop
```

Results are written to `load-tests/results/` as JSON and Markdown. User creation and login are setup operations and are not included in scenario latency or throughput.

The read-path A/B runner can be used against a backend started with either
`--guangying.cache.enabled=false` or `--guangying.cache.enabled=true`:

```powershell
node load-tests/http-read.mjs --base-url=http://localhost:18082 --requests=3000 --concurrency=100 --warmup=100 --label=cache-on
```

The load-test stack does not reuse the project's persistent volumes. MySQL, Redis, and RocketMQ data are stored in container `tmpfs` mounts and disappear when the containers are removed.
