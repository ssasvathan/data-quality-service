-- Active-record views for dqs-serve
-- Temporal pattern: active records have expiry_date = '9999-12-31 23:59:59' (EXPIRY_SENTINEL)
-- All serve-layer queries MUST use these views — never query raw tables with manual expiry filters
-- Reference: project-context.md § Framework-Specific Rules (FastAPI) and § Anti-Patterns

CREATE OR REPLACE VIEW v_dq_orchestration_run_active AS
    SELECT * FROM dq_orchestration_run WHERE expiry_date = '9999-12-31 23:59:59';

CREATE OR REPLACE VIEW v_dq_run_active AS
    SELECT * FROM dq_run WHERE expiry_date = '9999-12-31 23:59:59';

CREATE OR REPLACE VIEW v_dq_metric_numeric_active AS
    SELECT * FROM dq_metric_numeric WHERE expiry_date = '9999-12-31 23:59:59';

CREATE OR REPLACE VIEW v_dq_metric_detail_active AS
    SELECT * FROM dq_metric_detail WHERE expiry_date = '9999-12-31 23:59:59';

CREATE OR REPLACE VIEW v_check_config_active AS
    SELECT * FROM check_config WHERE expiry_date = '9999-12-31 23:59:59';

CREATE OR REPLACE VIEW v_dataset_enrichment_active AS
    SELECT * FROM dataset_enrichment WHERE expiry_date = '9999-12-31 23:59:59';
