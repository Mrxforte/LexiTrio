# LexiTrio Load Testing & Acceptance Criteria

## 1. Locust Load Testing Guide

Locust load test scenarios are defined in `backend/load_testing/locustfile.py`.

### Running Locust Scenarios

```bash
# Install Locust
pip install locust

# Start Locust web interface targeting local backend
locust -f backend/load_testing/locustfile.py --host=http://localhost:8000
```

---

## 2. Load Testing Scenarios & Concurrency Stages

| Stage | Virtual Users (VUs) | Spawn Rate | Target User Behavior | Target p95 Latency |
|---|---|---|---|---|
| Stage 1 | 10 VUs | 2 / sec | Exact word lookups, catalog fetch | < 50 ms |
| Stage 2 | 50 VUs | 5 / sec | Mixed search & translation filters | < 100 ms |
| Stage 3 | 100 VUs | 10 / sec | High-concurrency rapid search | < 150 ms |
| Stage 4 | 250 VUs | 25 / sec | Stress test with Redis cache enabled | < 200 ms |
| Stage 5 | 500 VUs | 50 / sec | Peak load verification | < 300 ms |

---

## 3. Performance Acceptance Thresholds
- **Exact & Prefix Dictionary Search**: p95 latency **< 200 ms** under 250 VUs load.
- **Error Rate**: **0.0%** HTTP 5xx errors under target load.
- **Cache Hit Rate**: **> 85%** for repeated popular queries.
- **Database Connection Pool Utilization**: Peak utilization **< 80%** of max allowed connections.
