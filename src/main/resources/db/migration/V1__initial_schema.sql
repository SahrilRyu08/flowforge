-- FlowForge Initial Schema
-- Uses gen_random_uuid() (PostgreSQL 13+) so no uuid-ossp extension is required.

-- ── Tenants ───────────────────────────────────────────────────────────────
CREATE TABLE tenants (
                         id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
                         slug                VARCHAR(100) NOT NULL UNIQUE,
                         name                VARCHAR(200) NOT NULL,
                         status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
                         max_workflows       INT          NOT NULL DEFAULT 100,
                         max_runs_per_day    INT          NOT NULL DEFAULT 1000,
                         created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
                         updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
                         CONSTRAINT chk_tenant_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DELETED'))
);

-- ── Users ─────────────────────────────────────────────────────────────────
CREATE TABLE users (
                       id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
                       tenant_id       UUID        NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
                       email           VARCHAR(255) NOT NULL,
                       password_hash   VARCHAR(255) NOT NULL,
                       full_name       VARCHAR(100) NOT NULL,
                       role            VARCHAR(20)  NOT NULL DEFAULT 'VIEWER',
                       status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
                       created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
                       updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
                       CONSTRAINT uq_user_tenant_email UNIQUE (tenant_id, email),
                       CONSTRAINT chk_user_role   CHECK (role   IN ('ADMIN', 'EDITOR', 'VIEWER')),
                       CONSTRAINT chk_user_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'LOCKED'))
);

-- ── Workflows ─────────────────────────────────────────────────────────────
CREATE TABLE workflows (
                           id                      UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
                           tenant_id               UUID        NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
                           created_by              UUID        NOT NULL REFERENCES users(id),
                           name                    VARCHAR(200) NOT NULL,
                           description             VARCHAR(500),
                           status                  VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
                           current_version         INT          NOT NULL DEFAULT 1,
                           cron_expression         VARCHAR(100),
                           webhook_token           VARCHAR(100) UNIQUE,
                           global_timeout_seconds  INT          NOT NULL DEFAULT 3600,
                           created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
                           updated_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
                           CONSTRAINT chk_workflow_status CHECK (status IN ('DRAFT','ACTIVE','INACTIVE','ARCHIVED'))
);

-- ── Workflow Versions ─────────────────────────────────────────────────────
CREATE TABLE workflow_versions (
                                   id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
                                   workflow_id     UUID        NOT NULL REFERENCES workflows(id) ON DELETE CASCADE,
                                   version         INT         NOT NULL,
                                   dag_definition  JSONB       NOT NULL,
                                   change_note     VARCHAR(500),
                                   created_by      UUID        REFERENCES users(id),
                                   created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                                   CONSTRAINT uq_workflow_version UNIQUE (workflow_id, version)
);

-- ── Workflow Runs ─────────────────────────────────────────────────────────
CREATE TABLE workflow_runs (
                               id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
                               tenant_id           UUID        NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
                               workflow_id         UUID        NOT NULL REFERENCES workflows(id),
                               workflow_version_id UUID        NOT NULL REFERENCES workflow_versions(id),
                               status              VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
                               trigger_type        VARCHAR(20)  NOT NULL,
                               triggered_by        VARCHAR(255),
                               started_at          TIMESTAMPTZ,
                               finished_at         TIMESTAMPTZ,
                               duration_ms         BIGINT,
                               error_message       VARCHAR(2000),
                               created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
                               updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
                               CONSTRAINT chk_run_status        CHECK (status       IN ('PENDING','RUNNING','SUCCESS','FAILED','TIMED_OUT','CANCELLED')),
                               CONSTRAINT chk_run_trigger_type  CHECK (trigger_type IN ('MANUAL','SCHEDULED','WEBHOOK','API'))
);

-- ── Step Runs ─────────────────────────────────────────────────────────────
CREATE TABLE step_runs (
                           id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
                           workflow_run_id UUID        NOT NULL REFERENCES workflow_runs(id) ON DELETE CASCADE,
                           step_id         VARCHAR(100) NOT NULL,
                           step_name       VARCHAR(200) NOT NULL,
                           status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
                           attempt_number  INT          NOT NULL DEFAULT 1,
                           started_at      TIMESTAMPTZ,
                           finished_at     TIMESTAMPTZ,
                           duration_ms     BIGINT,
                           output          JSONB,
                           error_message   VARCHAR(2000),
                           created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
                           updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
                           CONSTRAINT chk_step_status CHECK (status IN ('PENDING','RUNNING','SUCCESS','FAILED','SKIPPED','RETRYING'))
);

-- ── Step Run Logs ─────────────────────────────────────────────────────────
CREATE TABLE step_run_logs (
                               step_run_id UUID        NOT NULL REFERENCES step_runs(id) ON DELETE CASCADE,
                               log_line    VARCHAR(4000),
                               line_order  INT         NOT NULL
);

-- ════════════════════════════════════════════════════════════════════════════
-- INDEXES
-- ════════════════════════════════════════════════════════════════════════════

CREATE INDEX idx_workflow_tenant      ON workflows (tenant_id);
CREATE INDEX idx_workflow_status      ON workflows (status);
CREATE INDEX idx_workflow_name_lower  ON workflows (tenant_id, lower(name));

CREATE INDEX idx_version_workflow     ON workflow_versions (workflow_id, version DESC);

CREATE INDEX idx_run_tenant_created   ON workflow_runs (tenant_id, created_at DESC);
CREATE INDEX idx_run_workflow_id      ON workflow_runs (workflow_id);
CREATE INDEX idx_run_status           ON workflow_runs (status);
CREATE INDEX idx_run_tenant_status    ON workflow_runs (tenant_id, status);

CREATE INDEX idx_steprun_run          ON step_runs (workflow_run_id);
CREATE INDEX idx_steprun_status       ON step_runs (status);

INSERT INTO tenants (slug, name, status)
VALUES ('system', 'System Tenant', 'ACTIVE');
