-- =========================================================================
-- APPROVAL WORKFLOW DATABASE SCHEMA (PostgreSQL)
-- =========================================================================

-- 1. Ensure DATASOURCE_HISTORY table has status column & index
-- Status Codes:
--   '03' = PENDING_APPROVAL (Default upon upload)
--   '01' = APPROVED (Active / accessible to queries)
--   '02' = REJECTED
--   '04' = CANCELLED
ALTER TABLE IF EXISTS datasource_history 
    ADD COLUMN IF NOT EXISTS first_register_user VARCHAR(100),
    ADD COLUMN IF NOT EXISTS first_register_date VARCHAR(8),
    ADD COLUMN IF NOT EXISTS first_register_time VARCHAR(9),
    ADD COLUMN IF NOT EXISTS last_change_user    VARCHAR(100),
    ADD COLUMN IF NOT EXISTS last_change_date    VARCHAR(8),
    ADD COLUMN IF NOT EXISTS last_change_time    VARCHAR(9);

CREATE INDEX IF NOT EXISTS idx_datasource_history_status ON datasource_history(status);
CREATE INDEX IF NOT EXISTS idx_datasource_history_source ON datasource_history(source_id, archived_at DESC);
CREATE INDEX IF NOT EXISTS idx_datasource_history_upload ON datasource_history(upload_id);

-- 2. Audit Trail Table for Approval Workflow
CREATE TABLE IF NOT EXISTS upload_approval_audit (
    audit_id            BIGSERIAL PRIMARY KEY,
    upload_id           VARCHAR(255) NOT NULL,
    source_id           VARCHAR(100) NOT NULL,
    action              VARCHAR(50)  NOT NULL, -- 'SUBMITTED', 'APPROVED', 'REJECTED', 'CANCELLED'
    action_by           VARCHAR(100) NOT NULL,
    action_date         VARCHAR(8)   NOT NULL,
    action_time         VARCHAR(9)   NOT NULL,
    previous_status     VARCHAR(10),
    new_status          VARCHAR(10)  NOT NULL,
    reason              TEXT,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_approval_audit_upload_id ON upload_approval_audit(upload_id);
CREATE INDEX IF NOT EXISTS idx_approval_audit_source_id ON upload_approval_audit(source_id);
CREATE INDEX IF NOT EXISTS idx_approval_audit_created_at ON upload_approval_audit(created_at DESC);
