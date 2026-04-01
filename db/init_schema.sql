CREATE TABLE IF NOT EXISTS dataset (
    dataset_id SERIAL PRIMARY KEY,
    src_sys_nm VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(src_sys_nm)
);
