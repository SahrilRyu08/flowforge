# FlowForge

Real-time, multi-tenant workflow orchestration MVP (Spring Boot 3, PostgreSQL, Redis, React).

## Quick start (Docker)

```bash
docker compose up --build
```

- **API:** http://localhost:8080 — Swagger UI: http://localhost:8080/swagger-ui.html  
- **Web:** http://localhost:3000  
- **PostgreSQL:** localhost:5432 (`flowforge` / `flowforge`)  
- **Redis:** localhost:6379  

Set a strong `JWT_SECRET` in production (env var).

### Local development (without Docker for the API)

1. Start PostgreSQL and Redis matching `src/main/resources/application.yml`.
2. `mvn spring-boot:run`
3. `cd frontend && npm install && npm run dev` (proxies `/api` and `/ws` to :8080)

## Architecture (short)

- **Engine:** DAG parse/validate (`DagParser`, `TopologicalSorter`), parallel execution per topological level (`DagExecutor`, Java 21 virtual threads via `workflowExecutor` pool).
- **API:** JWT auth (`sub` = user UUID for global uniqueness), tenant isolation (`TenantIsolationFilter` + repository queries).
- **Real-time:** STOMP over SockJS at `/ws?token=…`, topics `/topic/tenant/{tenantId}/runs`.
- **Rate limiting:** Redis token bucket per IP (`RateLimitFilter`; disable in tests via `flowforge.rate-limit.enabled=false`).
- **Scheduling:** Spring `@Scheduled` evaluates cron expressions on `ACTIVE` workflows.
- **Logs:** Step output in JSONB + optional line logs on `step_runs` / `step_run_logs` — for massive scale, offload to object storage (see `docs/INFRASTRUCTURE.md`).
- **AI:** `POST /api/v1/runs/{id}/failure-insights` calls an OpenAI-compatible chat API when `FLOWFORGE_AI_OPENAI_API_KEY` is set; truncates context and parses JSON safely.

## Trade-offs & next steps

| Area | MVP choice | With more time |
|------|------------|----------------|
| Execution | In-process `@Async` | Queue + worker fleet; isolate scripting |
| WebSocket | Simple broker | Redis pub/sub or managed MQ for multi-instance |
| Cron | Minute-level tick + Spring cron | Quartz / external scheduler with persistence |
| GraphQL | Not included | Federation or Spring GraphQL for reads |
| E2E | Spring `MockMvc` + async polling | Testcontainers + Playwright |

## Data migrations

Flyway scripts live in `src/main/resources/db/migration/`. `V1` uses `gen_random_uuid()` (PostgreSQL 13+) to avoid superuser extension installs. `V2` adds dashboard-friendly indexes.

## Testing

```bash
mvn verify
```

Includes parser/engine unit tests, API integration tests, and `WorkflowE2ETest` (create → activate → trigger → assert run completes).

## Pull request template (example)

**Title:** `feat: FlowForge MVP — engine, API, dashboard, AI insights`

**Summary:** Implements DAG execution, CRUD + versioning, webhooks, cron scheduling, JWT/RBAC, WebSocket run events, Redis rate limiting, dashboard metrics, optional OpenAI failure analysis, Docker/Compose, CI, and documentation.

**Checklist:** tests green, `docker compose up` smoke OK, secrets not committed.

## License

Proprietary / interview project — confirm with the author.
