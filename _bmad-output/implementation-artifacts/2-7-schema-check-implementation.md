# Story 2.7: Schema Check Implementation

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a **data steward**,
I want the Schema check (#5) to detect schema drift by comparing today's discovered schema against yesterday's stored schema hash,
so that I can identify unexpected structural changes in source data.

## Acceptance Criteria

1. **Given** a dataset DataFrame and a previously stored schema hash in `dq_metric_detail` **When** the Schema check executes **Then** it computes the current schema hash and compares against the stored hash.
2. **And** if the schema changed, it writes a `MetricDetail` with check_type=`schema` containing the diff (added/removed/changed fields).
3. **And** it determines pass (no change) or warn/fail (schema drift detected).
4. **Given** a dataset with no previous schema hash (first run) **When** the Schema check executes **Then** it stores the current schema hash as baseline and passes.
5. **Given** a schema change involving only added fields (no removals) **When** the Schema check executes **Then** it reports the change with detail showing the new fields.

## Tasks / Subtasks

- [x] Task 1: Implement `SchemaCheck` strategy class in `dqs-spark` (AC: #1, #2, #3, #4, #5)
  - [x] Create `dqs-spark/src/main/java/com/bank/dqs/checks/SchemaCheck.java`.
  - [x] Implement `DqCheck` with canonical check type `SCHEMA` (uppercase; must match `check_config.check_type`).
  - [x] Guard `execute(DatasetContext context)` for null `context` and null `context.getDf()` and return deterministic `NOT_RUN` `MetricDetail` payloads (no uncaught exceptions).
  - [x] Compute canonical schema representation from `context.getDf().schema()` and derive a deterministic schema hash.
  - [x] Persist current baseline hash in a detail metric every run so future runs can compare against latest active baseline.

- [x] Task 2: Implement baseline retrieval + hash comparison contract (AC: #1, #4)
  - [x] Add `SchemaCheck.BaselineProvider` abstraction (same testability approach as `FreshnessCheck`/`VolumeCheck`).
  - [x] Add default/no-op baseline provider for first-run behavior.
  - [x] Add JDBC baseline provider that reads latest prior schema hash/detail for the dataset from active records only.
  - [x] Compare current hash vs baseline hash and branch to unchanged/changed behavior deterministically.

- [x] Task 3: Implement schema diff extraction and classification policy (AC: #2, #3, #5)
  - [x] Build canonical field map from schema (field path -> normalized type/nullability descriptor) to enable diffing.
  - [x] Compute `added_fields`, `removed_fields`, and `changed_fields` from current vs baseline schema.
  - [x] Classification rules:
  - [x] `PASS` when no drift (hash equal) or first run (`baseline_unavailable`).
  - [x] `WARN` when drift is add-only (`added_fields` non-empty, removed/changed empty).
  - [x] `FAIL` when any removed or changed field exists.
  - [x] Write drift detail payload with added/removed/changed field lists when drift exists.

- [x] Task 4: Emit metrics/detail payloads with explicit contract (AC: #1, #2, #3, #4, #5)
  - [x] Emit `MetricNumeric` counts for `added_fields_count`, `removed_fields_count`, and `changed_fields_count`.
  - [x] Emit `MetricDetail` `schema_status` JSON with `{status, reason, schema_hash, baseline_hash, added_count, removed_count, changed_count}`.
  - [x] Emit `MetricDetail` `schema_hash` JSON baseline payload with hash algorithm and canonical schema encoding.
  - [x] Emit `MetricDetail` `schema_diff` JSON only when drift is detected.
  - [x] Ensure payloads include field names/structure only; never include raw source data values.

- [x] Task 5: Register in factory and extend tests (AC: #1, #2, #3, #4, #5)
  - [x] Update `dqs-spark/src/test/java/com/bank/dqs/checks/CheckFactoryTest.java` with real `SchemaCheck` registration and enablement tests.
  - [x] Create `dqs-spark/src/test/java/com/bank/dqs/checks/SchemaCheckTest.java` with SparkSession lifecycle pattern matching existing check tests.
  - [x] Cover at minimum:
  - [x] `executePassesAndStoresBaselineWhenNoPreviousHash`
  - [x] `executePassesWhenSchemaHashMatchesBaseline`
  - [x] `executeWarnsAndListsAddedFieldsWhenDriftIsAddOnly`
  - [x] `executeFailsWhenFieldsRemovedOrChanged`
  - [x] `executeReturnsNotRunForMissingContextOrDataFrame`
  - [x] `executeSanitizesExecutionErrorPayload`
  - [x] `getCheckTypeReturnsSchema`

- [x] Task 6: Keep scope boundaries aligned with epic sequencing
  - [x] Do **not** implement `OpsCheck` or `DqsScoreCheck` in this story.
  - [x] Do **not** wire end-to-end check execution into `DqsJob` (Story 2.10 handles orchestration + batch write).
  - [x] Do **not** change Postgres schema DDL in this story.
  - [x] Do **not** add serve/API/dashboard logic for schema check specifics.

### Review Findings

- [x] [Review][Patch] Handle hash-only baseline mismatches deterministically by classifying drift as `FAIL` instead of returning `NOT_RUN` when prior baseline payloads are missing `schema_json` [`dqs-spark/src/main/java/com/bank/dqs/checks/SchemaCheck.java:150`]
- [x] [Review][Patch] Add regression coverage for hash-only baseline mismatch handling to prevent reintroduction [`dqs-spark/src/test/java/com/bank/dqs/checks/SchemaCheckTest.java:238`]

## Dev Notes

### Story Context and Cross-Story Dependencies

- Story 2.1 established `DqCheck`, `MetricNumeric`, `MetricDetail`, `DatasetContext`, and factory-based check discovery.
- Stories 2.2-2.4 established discovery/read/enrichment paths feeding `DatasetContext` into check execution.
- Story 2.5 established baseline-provider abstraction and per-dataset isolation pattern for check execution.
- Story 2.6 established statistical/check status payload pattern, factory registration tests, and error-payload sanitization expectations.
- Story 2.7 should mirror these patterns to keep check behavior uniform and low-risk for Story 2.8/2.9.

### Existing Code to Reuse (Do Not Reinvent)

- `dqs-spark/src/main/java/com/bank/dqs/checks/DqCheck.java`
- `dqs-spark/src/main/java/com/bank/dqs/checks/CheckFactory.java`
- `dqs-spark/src/main/java/com/bank/dqs/checks/FreshnessCheck.java`
- `dqs-spark/src/main/java/com/bank/dqs/checks/VolumeCheck.java`
- `dqs-spark/src/main/java/com/bank/dqs/model/DatasetContext.java`
- `dqs-spark/src/main/java/com/bank/dqs/model/MetricNumeric.java`
- `dqs-spark/src/main/java/com/bank/dqs/model/MetricDetail.java`
- `dqs-spark/src/main/java/com/bank/dqs/model/DqsConstants.java`

Reuse established implementation patterns:
- Constructor argument guards via `IllegalArgumentException`.
- Try-with-resources for JDBC providers.
- Prepared statements with `?` placeholders only.
- `LinkedHashMap` for deterministic JSON payload field order in detail metrics.
- Per-dataset exception isolation by returning diagnostic detail metrics instead of throwing.

### Schema Metric and Detail Contract

- `check_type`: `SCHEMA`
- Numeric metrics:
  - `added_fields_count`
  - `removed_fields_count`
  - `changed_fields_count`
- Detail metrics:
  - `detail_type = schema_status`
  - `detail_type = schema_hash`
  - `detail_type = schema_diff` (only when drift exists)

Suggested `schema_status` payload:

```json
{
  "status": "PASS|WARN|FAIL|NOT_RUN",
  "reason": "baseline_unavailable|no_schema_drift|schema_additive_change|schema_breaking_change|missing_context|missing_dataframe|execution_error",
  "schema_hash": "sha256:...",
  "baseline_hash": "sha256:...",
  "added_count": 0,
  "removed_count": 0,
  "changed_count": 0
}
```

Suggested `schema_diff` payload:

```json
{
  "added_fields": ["path.to.new_field"],
  "removed_fields": ["path.to.deleted_field"],
  "changed_fields": [
    {
      "field": "path.to.field",
      "before": "type=string,nullable=true",
      "after": "type=long,nullable=false"
    }
  ]
}
```

Suggested `schema_hash` payload:

```json
{
  "algorithm": "SHA-256",
  "hash": "sha256:...",
  "schema_json": "{...canonical spark schema json...}"
}
```

### Baseline Retrieval SQL Contract

Use active records only and read the latest prior baseline for the same dataset:

```sql
SELECT md.detail_value
FROM dq_metric_detail md
JOIN dq_run r ON r.id = md.dq_run_id
WHERE r.dataset_name = ?
  AND md.check_type = ?
  AND md.detail_type = ?
  AND r.partition_date < ?
  AND r.expiry_date = ?
  AND md.expiry_date = ?
ORDER BY r.partition_date DESC
LIMIT 1
```

Bindings:
- `dataset_name` = `context.getDatasetName()`
- `check_type` = `SCHEMA`
- `detail_type` = `schema_hash`
- `partition_date` = `context.getPartitionDate()`
- `expiry_date` values = `DqsConstants.EXPIRY_SENTINEL`

### Canonicalization and Drift Rules

- Hash input should be deterministic across runs for the same schema:
  - Canonical base: Spark schema JSON (`StructType.json()`).
  - Hash algorithm: `SHA-256` (hex string).
  - Prefix hash with algorithm marker (`sha256:`) in payload.
- Drift evaluation:
  - If no baseline: `PASS` + `baseline_unavailable`; store baseline hash payload.
  - If baseline hash equals current hash: `PASS` + `no_schema_drift`.
  - If different:
    - `WARN` for add-only drift (added fields only).
    - `FAIL` if any removed field or type/nullability change.
- Field path conventions must be deterministic and explicit (nested struct + array/map paths).

### Security and Data Sensitivity Guardrails

- Only persist schema structure metadata (field names, types, nullability); never persist row values.
- Error detail payloads must be sanitized (store `error_type`, not raw exception messages).
- Keep all outputs within generic metric tables (`dq_metric_numeric`, `dq_metric_detail`) to preserve check-agnostic serve/dashboard layers.

### Project Structure Notes

Implementation stays inside `dqs-spark`:

```text
dqs-spark/src/main/java/com/bank/dqs/checks/
  SchemaCheck.java                <-- NEW

dqs-spark/src/test/java/com/bank/dqs/checks/
  SchemaCheckTest.java            <-- NEW
  CheckFactoryTest.java           <-- MODIFY
```

No files outside `dqs-spark/` are required for this story.

### Previous Story Intelligence (2.6) to Apply

- Preserve baseline metadata in low-history scenarios instead of replacing with empty baseline objects.
- Sanitize execution-error payloads to avoid leaking sensitive context.
- Add regression tests for every review fix to prevent reintroduction.
- Keep check/factory behavior deterministic and isolated to story scope (no opportunistic cross-story implementation).

### Git Intelligence Summary (Last 5 Commits)

- Recent commits implemented `FreshnessCheck` and `VolumeCheck` plus matching test suites and `CheckFactory` coverage.
- Pattern is stable: add check class + targeted test class + factory tests + story/sprint artifacts.
- Story 2.4/2.3 commits reinforce DqsJob scope boundaries and incremental story sequencing; keep Story 2.7 focused on check implementation only.

### Latest Technical Notes (Web-validated)

- Apache Spark 4.1.1 is a maintenance release with security/correctness fixes; Spark 4.0 introduced `VariantType`, so schema comparison should rely on Spark’s type APIs instead of custom ad hoc parsing.
- Spark `DataType` Java API provides `json()`, `fromJson()`, `fromDDL()`, `equalsStructurally()`, and `equalsStructurallyByName()` helpers that are useful for robust schema normalization/comparison.
- Project runtime remains Spark `3.5.0` in `dqs-spark/pom.xml`; this story should not change dependency versions.

### Testing Commands

```bash
cd /home/sas/workspace/data-quality-service/dqs-spark
mvn test -Dtest="SchemaCheckTest,CheckFactoryTest"
```

Full regression:

```bash
cd /home/sas/workspace/data-quality-service/dqs-spark
mvn test
```

### References

- Story definition and ACs: `_bmad-output/planning-artifacts/epics/epic-2-dataset-discovery-tier-1-quality-checks.md` (Story 2.7)
- Functional requirements: `_bmad-output/planning-artifacts/prd.md` (FR10, FR32, FR33, FR34)
- Architecture patterns and boundaries: `_bmad-output/planning-artifacts/architecture.md` (Current Versions, Structure Patterns, Process Patterns, FR mapping)
- Guardrails and anti-patterns: `_bmad-output/project-context.md` (Spark rules, temporal pattern, data-sensitivity boundary)
- Existing Spark contracts:
  - `dqs-spark/src/main/java/com/bank/dqs/checks/DqCheck.java`
  - `dqs-spark/src/main/java/com/bank/dqs/checks/CheckFactory.java`
  - `dqs-spark/src/main/java/com/bank/dqs/checks/FreshnessCheck.java`
  - `dqs-spark/src/main/java/com/bank/dqs/checks/VolumeCheck.java`
- Schema/table contracts:
  - `dqs-serve/src/serve/schema/ddl.sql` (`dq_metric_numeric`, `dq_metric_detail`, `check_config`)
  - `dqs-serve/src/serve/schema/fixtures.sql` (`SCHEMA` check_type rows and JSONB detail examples)
- Legacy conceptual reference (do not copy implementation directly):
  - `spark-job/src/main/java/com/dqs/checks/CheckExecutor.java`
- External docs:
  - Spark DataType JavaDoc: https://spark.apache.org/docs/4.0.0/api/java/org/apache/spark/sql/types/DataType.html
  - Spark 4.1.1 release notes: https://spark.apache.org/releases/spark-release-4.1.1.html
  - Spark VariantType JavaDoc: https://spark.apache.org/docs/4.0.0/api/java/org/apache/spark/sql/types/VariantType.html

## Story Completion Status

- Story implementation completed and validated against acceptance criteria.
- Code review findings have been fixed and regression tested.
- Story status set to `done` and sprint status synchronized to `2-7-schema-check-implementation: done`.

## Dev Agent Record

### Agent Model Used

GPT-5 Codex

### Debug Log References

- `cd dqs-spark && mvn test -Dtest="SchemaCheckTest,CheckFactoryTest"` (initial red phase failed: `SchemaCheck` missing).
- `cd dqs-spark && mvn test -Dtest="SchemaCheckTest,CheckFactoryTest"` (green phase passed after implementation).
- `cd dqs-spark && mvn test` (full regression passed, 127 tests).
- `cd dqs-spark && mvn test -Dtest="SchemaCheckTest,CheckFactoryTest"` (code review regression pass after patching hash-only baseline handling, 25 tests).
- `cd dqs-spark && mvn test` (code review full regression pass, 128 tests).

### Completion Notes List

- Implemented `SchemaCheck` with deterministic schema hashing (`SHA-256`), baseline comparison, and drift classification (`PASS/WARN/FAIL`).
- Added schema diff extraction (`added_fields`, `removed_fields`, `changed_fields`) using canonical field-path descriptors for nested Spark types.
- Added baseline provider contract with no-op and JDBC implementations using active-record filtering and latest-prior baseline retrieval.
- Emitted full metric contract: numeric counts plus `schema_status`, `schema_hash`, and conditional `schema_diff` detail payloads.
- Preserved error-safety and sensitivity boundaries: execution errors return sanitized `error_type` without leaking exception message content.
- Validated with targeted and full regression tests; all tests passing.
- Addressed review edge case where baseline hash existed without `schema_json` by emitting deterministic fail drift output instead of `NOT_RUN`.
- Added targeted regression test locking the hash-only baseline mismatch behavior.

### File List

- `dqs-spark/src/main/java/com/bank/dqs/checks/SchemaCheck.java` (new)
- `dqs-spark/src/test/java/com/bank/dqs/checks/SchemaCheckTest.java` (new)
- `dqs-spark/src/test/java/com/bank/dqs/checks/CheckFactoryTest.java` (updated)
- `_bmad-output/implementation-artifacts/code-review-2-7-schema-check-implementation.md` (new)
- `_bmad-output/implementation-artifacts/2-7-schema-check-implementation.md` (updated)
- `_bmad-output/implementation-artifacts/sprint-status.yaml` (updated: `2-7-schema-check-implementation` to `done`)

## Change Log

- 2026-04-03: Story created and status set to `ready-for-dev`.
- 2026-04-03: Implemented `SchemaCheck`, completed test coverage, and advanced story status to `review`.
- 2026-04-03: Completed code review, fixed findings, re-ran regressions, and set story status to `done`.
