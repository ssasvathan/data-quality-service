#!/bin/bash
docker exec -e PGPASSWORD=localdev data-quality-service-postgres-1 psql -U postgres -d postgres -c "SELECT dataset_id, src_sys_nm FROM dataset LIMIT 1;"
docker exec -e PGPASSWORD=localdev data-quality-service-postgres-1 psql -U postgres -d postgres -c "SELECT run_id, dataset_id, status FROM dq_run LIMIT 1;"
