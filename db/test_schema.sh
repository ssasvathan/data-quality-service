#!/bin/bash
docker exec -e PGPASSWORD=localdev data-quality-service-postgres-1 psql -U postgres -d postgres -c "SELECT dataset_id, src_sys_nm FROM dataset LIMIT 1;"
