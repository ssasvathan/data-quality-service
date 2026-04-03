---
stepsCompleted: ['step-01-preflight-and-context', 'step-02-generation-mode', 'step-03-test-strategy', 'step-04-generate-tests', 'step-04c-aggregate', 'step-05-validate-and-complete']
lastStep: 'step-05-validate-and-complete'
lastSaved: '2026-04-03'
workflowType: 'testarch-atdd'
inputDocuments:
  - _bmad-output/implementation-artifacts/1-1-initialize-project-structure-development-environment.md
  - _bmad-output/planning-artifacts/epics/epic-1-project-foundation-data-model.md
  - _bmad-output/project-context.md
  - _bmad-output/planning-artifacts/architecture.md
  - _bmad/tea/config.yaml
---

# ATDD Checklist - Epic 1, Story 1.1: Initialize Project Structure & Development Environment

**Date:** 2026-04-03
**Author:** Sas (BMad TEA Agent)
**Primary Test Level:** Structural / Integration
**TDD Phase:** RED (all tests skipped, will fail when unskipped until story implemented)

---

## Story Summary

Initializes all 4 component skeletons (`dqs-spark`, `dqs-serve`, `dqs-orchestrator`, `dqs-dashboard`) from architecture starter templates with a working `docker-compose.yml` for local development.

**As a** developer
**I want** all 4 component skeletons initialized with a working docker-compose for local development
**So that** I can start building any component immediately with a consistent, deployable stack

---

## Acceptance Criteria

1. Given a fresh clone, when I run `docker-compose up`, Then Postgres starts on port 5432, FastAPI (dqs-serve) starts on port 8000, and Vite dev server (dqs-dashboard) starts on port 5173
2. And dqs-spark has a Maven project structure with `pom.xml` and Spark as `provided` dependency
3. And dqs-orchestrator has a Python project with `pyproject.toml` and its own `config/` directory
4. And each component has its own config/, dependencies, and build — no shared resources between components

---

## Failing Tests Created (RED Phase)

### Structural Verification Tests (57 tests)

**File:** `tests/acceptance/test_story_1_1_project_structure.py`

#### TestDqsSparkStructure (13 tests) — AC2

- **[P0]** `test_dqs_spark_root_directory_exists` — RED: dqs-spark/ not created
- **[P0]** `test_pom_xml_exists` — RED: pom.xml not created
- **[P0]** `test_pom_xml_group_id_is_com_bank_dqs` — RED: must be com.bank.dqs (not com.dqs)
- **[P0]** `test_pom_xml_artifact_id_is_dqs_spark` — RED: artifactId must be dqs-spark
- **[P0]** `test_spark_dependency_is_provided_scope` — RED: Spark scope must be provided
- **[P0]** `test_pom_xml_spark_version_is_3_5_0` — RED: Spark version must be 3.5.0
- **[P0]** `test_pom_xml_surefire_uses_java_21_jvm` — RED: Java 21 JVM path required
- **[P0]** `test_pom_xml_surefire_has_add_opens_flags` — RED: 12 --add-opens flags required
- **[P0]** `test_dqs_spark_main_java_directory_structure` — RED: Maven src layout not created
- **[P0]** `test_dqs_spark_test_java_directory_exists` — RED: test directory not created
- **[P0]** `test_dqs_spark_placeholder_main_class_exists` — RED: DqsJob.java not created
- **[P1]** `test_dqs_spark_config_directory_exists` — RED: config/ not created
- **[P1]** `test_dqs_spark_application_properties_exists` — RED: application.properties not created

#### TestDqsOrchestratorStructure (11 tests) — AC3

- **[P0]** `test_dqs_orchestrator_root_directory_exists` — RED: dqs-orchestrator/ not created
- **[P0]** `test_dqs_orchestrator_pyproject_toml_exists` — RED: pyproject.toml not created
- **[P0]** `test_dqs_orchestrator_has_psycopg2_dependency` — RED: dependency not declared
- **[P0]** `test_dqs_orchestrator_has_pyyaml_dependency` — RED: dependency not declared
- **[P0]** `test_dqs_orchestrator_config_directory_exists` — RED: config/ not created
- **[P0]** `test_dqs_orchestrator_config_orchestrator_yaml_exists` — RED: file not created
- **[P0]** `test_dqs_orchestrator_config_parent_paths_yaml_exists` — RED: file not created
- **[P0]** `test_dqs_orchestrator_src_package_exists` — RED: src/orchestrator/ not created
- **[P0]** `test_dqs_orchestrator_src_init_exists` — RED: __init__.py not created
- **[P1]** `test_dqs_orchestrator_placeholder_modules_exist` — RED: modules not created
- **[P1]** `test_dqs_orchestrator_tests_directory_exists` — RED: tests/ not created

#### TestDqsServeStructure (12 tests) — AC1/AC4

- **[P0]** `test_dqs_serve_root_directory_exists` — RED: dqs-serve/ not created
- **[P0]** `test_dqs_serve_pyproject_toml_exists` — RED: pyproject.toml not created
- **[P0]** `test_dqs_serve_has_fastapi_dependency` — RED: dependency not declared
- **[P0]** `test_dqs_serve_has_sqlalchemy_dependency` — RED: dependency not declared
- **[P1]** `test_dqs_serve_config_directory_exists` — RED: config/ not created
- **[P1]** `test_dqs_serve_config_serve_yaml_exists` — RED: serve.yaml not created
- **[P0]** `test_dqs_serve_src_package_structure_exists` — RED: src/serve/ structure not created
- **[P0]** `test_dqs_serve_main_py_exists_with_fastapi_app` — RED: main.py not created
- **[P0]** `test_dqs_serve_db_placeholder_files_exist` — RED: db/ files not created
- **[P1]** `test_dqs_serve_schema_ddl_sql_exists` — RED: schema/ddl.sql not created
- **[P0]** `test_dqs_serve_dockerfile_exists` — RED: Dockerfile not created
- **[P1]** `test_dqs_serve_tests_directory_with_conftest_exists` — RED: tests/ not created

#### TestDqsDashboardStructure (8 tests) — AC1/AC4

- **[P0]** `test_dqs_dashboard_root_directory_exists` — RED: dqs-dashboard/ not created
- **[P0]** `test_dqs_dashboard_package_json_exists` — RED: package.json not created
- **[P0]** `test_dqs_dashboard_vite_config_exists_with_proxy` — RED: vite.config.ts not created
- **[P0]** `test_dqs_dashboard_tsconfig_json_exists` — RED: tsconfig.json not created
- **[P0]** `test_dqs_dashboard_src_directory_structure_exists` — RED: src/ structure not created
- **[P0]** `test_dqs_dashboard_api_placeholder_files_exist` — RED: api/ files not created
- **[P1]** `test_dqs_dashboard_page_components_exist` — RED: page components not created
- **[P0]** `test_dqs_dashboard_app_tsx_exists` — RED: App.tsx not created

#### TestDockerComposeConfiguration (8 tests) — AC1

- **[P0]** `test_docker_compose_postgres_port_is_5432` — RED: currently 5433:5432, must change
- **[P0]** `test_docker_compose_serve_service_exists` — RED: serve service not added
- **[P0]** `test_docker_compose_serve_port_is_8000` — RED: serve service not added
- **[P0]** `test_docker_compose_serve_depends_on_postgres` — RED: depends_on not configured
- **[P0]** `test_docker_compose_dashboard_service_exists` — RED: dashboard service not added
- **[P0]** `test_docker_compose_dashboard_port_is_5173` — RED: dashboard service not added
- **[P1]** `test_docker_compose_spark_is_not_in_services` — Anti-pattern guard (should pass)
- **[P1]** `test_docker_compose_orchestrator_is_not_in_services` — Anti-pattern guard (should pass)

#### TestSelfContainmentNoSharedResources (5 tests) — AC4

- **[P1]** `test_no_shared_config_directory_at_root` — Anti-pattern guard (should pass)
- **[P1]** `test_no_shared_requirements_txt_at_root` — Anti-pattern guard (should pass)
- **[P1]** `test_each_new_component_has_own_config_directory` — RED: components not created
- **[P1]** `test_prototype_directories_still_exist_and_untouched` — Brownfield guard (should pass)
- **[P1]** `test_dqs_dashboard_has_own_package_json_not_shared` — RED: dqs-dashboard not created

### Docker Compose Integration Tests (10 tests)

**File:** `tests/acceptance/test_story_1_1_docker_compose_startup.py`

#### TestDockerComposeYamlValidity (7 tests) — AC1

- **[P0]** `test_compose_file_is_valid_yaml` — RED: services missing
- **[P0]** `test_compose_defines_exactly_three_services` — RED: serve/dashboard missing
- **[P0]** `test_postgres_uses_postgres_15_image` — RED: validates image version
- **[P0]** `test_serve_service_builds_from_dqs_serve` — RED: serve not added
- **[P0]** `test_serve_service_has_database_url_env` — RED: serve not added
- **[P0]** `test_dashboard_service_builds_from_dqs_dashboard` — RED: dashboard not added
- **[P0]** `test_compose_validate_command_exits_successfully` — RED: docker compose config fails

#### TestDockerComposeServiceStartup (3 tests) — AC1 (integration, requires docker)

- **[P0]** `test_postgres_is_reachable_on_port_5432` — RED: requires running services
- **[P0]** `test_fastapi_health_endpoint_returns_200` — RED: requires running dqs-serve
- **[P0]** `test_vite_dev_server_responds_on_port_5173` — RED: requires running dqs-dashboard

---

## Test Summary

| Category | Test Count | Priority Breakdown |
|---|---|---|
| dqs-spark structural | 13 | P0: 11, P1: 2 |
| dqs-orchestrator structural | 11 | P0: 9, P1: 2 |
| dqs-serve structural | 12 | P0: 9, P1: 3 |
| dqs-dashboard structural | 8 | P0: 7, P1: 1 |
| docker-compose config | 8 | P0: 6, P1: 2 |
| self-containment | 5 | P1: 5 |
| compose YAML validity | 7 | P0: 7 |
| compose service startup | 3 | P0: 3 (integration) |
| **Total** | **67** | **P0: 52, P1: 15** |

**TDD Phase:** RED — All 67 tests skipped (pytest.mark.skip)
**Expected to fail when unskipped:** 63 tests (4 anti-pattern guards should pass immediately)

---

## Data Factories / Fixtures

No data factories needed for structural verification tests.
Shared fixtures in `tests/acceptance/conftest.py`:
- `project_root` — absolute path to project root
- `component_paths` — dictionary of expected component directories

---

## Anti-Pattern Guards

4 tests check for the ABSENCE of anti-patterns (they will pass even before story implementation):
- `test_no_shared_config_directory_at_root` — no shared config/ at root
- `test_no_shared_requirements_txt_at_root` — no shared requirements.txt
- `test_docker_compose_spark_is_not_in_services` — spark not in compose
- `test_docker_compose_orchestrator_is_not_in_services` — orchestrator not in compose

---

## Implementation Checklist

### To make TestDqsSparkStructure pass:

- [ ] Create `dqs-spark/pom.xml` with groupId `com.bank.dqs`, Spark 3.5.0 `provided`
- [ ] Set Surefire JVM to `/usr/lib/jvm/java-21-openjdk-amd64/bin/java`
- [ ] Copy 12 `--add-opens` flags from `spark-job/pom.xml`
- [ ] Create `dqs-spark/src/main/java/com/bank/dqs/` with subdirs: checks/, scanner/, model/, writer/
- [ ] Create `dqs-spark/src/test/java/com/bank/dqs/`
- [ ] Create `dqs-spark/src/main/java/com/bank/dqs/DqsJob.java` with `package com.bank.dqs`
- [ ] Create `dqs-spark/config/application.properties`

### To make TestDqsOrchestratorStructure pass:

- [ ] Run `uv init dqs-orchestrator && cd dqs-orchestrator && uv add psycopg2-binary pyyaml`
- [ ] Create `dqs-orchestrator/config/orchestrator.yaml`
- [ ] Create `dqs-orchestrator/config/parent_paths.yaml`
- [ ] Create `dqs-orchestrator/src/orchestrator/__init__.py`
- [ ] Create placeholder modules: cli.py, runner.py, db.py, email.py, models.py
- [ ] Create `dqs-orchestrator/tests/` directory

### To make TestDqsServeStructure pass:

- [ ] Run `uv init dqs-serve && cd dqs-serve && uv add fastapi sqlalchemy psycopg2-binary uvicorn fastmcp pydantic`
- [ ] Create `dqs-serve/config/serve.yaml`
- [ ] Create `src/serve/` with subdirs: db/, routes/, services/, mcp/, schema/
- [ ] Create `src/serve/main.py` with FastAPI app and `/health` endpoint
- [ ] Create `src/serve/db/__init__.py`, `engine.py`, `models.py`, `views.py`
- [ ] Create `src/serve/schema/ddl.sql` (placeholder)
- [ ] Create `dqs-serve/Dockerfile` with uvicorn entrypoint on port 8000
- [ ] Create `dqs-serve/tests/conftest.py`

### To make TestDqsDashboardStructure pass:

- [ ] Run `npm create vite@latest dqs-dashboard -- --template react-ts`
- [ ] Run `npm install @mui/material @emotion/react @emotion/styled recharts react-router @tanstack/react-query`
- [ ] Create `src/api/client.ts`, `queries.ts`, `types.ts`
- [ ] Create `src/pages/SummaryPage.tsx`, `LobDetailPage.tsx`, `DatasetDetailPage.tsx`
- [ ] Configure `vite.config.ts` with proxy: `{ '/api': 'http://localhost:8000' }`

### To make TestDockerComposeConfiguration and TestDockerComposeYamlValidity pass:

- [ ] Change Postgres port from `5433:5432` to `5432:5432`
- [ ] Add `serve` service: `build: ./dqs-serve`, `ports: ["8000:8000"]`, `depends_on: [postgres]`, `DATABASE_URL` env
- [ ] Add `dashboard` service: `build: ./dqs-dashboard`, `ports: ["5173:5173"]`

---

## Running Tests

```bash
# Run all structural tests (fast, no docker required)
python3 -m pytest tests/acceptance/ -k "not integration" -v

# Run specific component tests
python3 -m pytest tests/acceptance/test_story_1_1_project_structure.py -v

# Run docker-compose YAML validation only
python3 -m pytest tests/acceptance/test_story_1_1_docker_compose_startup.py::TestDockerComposeYamlValidity -v

# Run integration tests (requires docker-compose up)
python3 -m pytest tests/acceptance/ -m integration -v

# Run all 67 tests
python3 -m pytest tests/acceptance/ -v
```

---

## Red-Green-Refactor Workflow

### RED Phase (Complete) ✅

**TEA Agent Responsibilities:**

- ✅ 67 failing tests written with pytest.mark.skip() (TDD red phase)
- ✅ Tests assert expected directory/file structure matching architecture spec
- ✅ pom.xml content assertions (groupId, Spark scope, Surefire Java 21)
- ✅ docker-compose.yml service/port assertions
- ✅ Self-containment anti-pattern guards
- ✅ Brownfield coexistence guards

### GREEN Phase (DEV Team — Next Steps)

1. Pick one test class (start with `TestDqsSparkStructure`)
2. Remove `@pytest.mark.skip(...)` from the class decorator
3. Run tests: `python3 -m pytest tests/acceptance/test_story_1_1_project_structure.py::TestDqsSparkStructure -v`
4. Implement the required files/directories
5. Verify tests PASS
6. Repeat for each component class
7. Run all tests: `python3 -m pytest tests/acceptance/ -v`

---

## Test Execution Evidence

### Initial Collection Run (RED Phase Verification)

**Command:** `python3 -m pytest tests/acceptance/ -q`

**Results:**
```
67 skipped in 0.08s
```

**Summary:**
- Total tests: 67
- Passing: 0 (expected — all skipped at class level)
- Skipped: 67 (TDD red phase — correct)
- Status: ✅ RED phase verified

---

## Notes

- **Brownfield coexistence:** `spark-job/`, `serve-api/`, `db/` must remain untouched throughout implementation
- **Port change critical:** docker-compose Postgres must change from `5433:5432` to `5432:5432` per AC1
- **groupId critical:** dqs-spark must use `com.bank.dqs`, NOT `com.dqs` from old prototype
- **Java 21 JVM path:** Must be exact `/usr/lib/jvm/java-21-openjdk-amd64/bin/java` in Surefire
- **4 anti-pattern guards** will pass immediately (before any implementation) — this is expected and intentional
- **Integration tests** (`TestDockerComposeServiceStartup`) require running services; use `-m integration` flag
- **No E2E browser tests** for this story — it is a pure backend/infrastructure setup story

---

**Generated by BMad TEA Agent (bmad-testarch-atdd)** — 2026-04-03
