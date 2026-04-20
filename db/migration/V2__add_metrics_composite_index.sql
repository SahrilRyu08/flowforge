-- Metrics / dashboard query optimization (see README for EXPLAIN reasoning).
-- Non-CONCURRENT indexes so this runs inside Flyway's default transaction.

CREATE INDEX IF NOT EXISTS idx_run_metrics
    ON workflow_runs (tenant_id, status, created_at DESC)
    INCLUDE (duration_ms);

CREATE INDEX IF NOT EXISTS idx_run_active
    ON workflow_runs (tenant_id)
    WHERE status IN ('PENDING', 'RUNNING');

CREATE INDEX IF NOT EXISTS idx_workflow_webhook_token
    ON workflows (webhook_token)
    WHERE webhook_token IS NOT NULL;

COMMENT ON INDEX idx_run_metrics IS
    'Composite index for dashboard metrics queries: tenant + status + time range.';
