# Story 2.9: DQS Score Computation

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a **data steward**,
I want the DQS Score (#25) computed as a weighted composite across all executed checks with configurable weights,
so that I have a single health indicator per dataset that reflects overall quality.

## Acceptance Criteria

1. **Given** a dataset with completed Freshness, Volume, Schema, and Ops checks **When** the DQS Score check executes **Then** it reads per-check scores and applies configurable weights (from `check_config` or defaults).
2. **And** it computes a composite score (0-100) and writes a `MetricNumeric` with check_type=`DQS_SCORE`.
3. **And** the score breakdown (weight * check_score per check) is written to `MetricDetail` for transparency.
4. **Given** a dataset with custom weights in `dataset_enrichment` **When** the DQS Score check executes **Then** it uses the custom weights instead of defaults.
5. **Given** a dataset where one check could not run **When** the DQS Score check executes **Then** it computes the score from available checks, redistributing the missing check's weight proportionally.

## Tasks / Subtasks

- [x] Task 1: Implement `DqsScoreCheck` strategy class in `dqs-spark` (AC: #1, #2, #3, #4, #5)
  - [x] Create `dqs-spark/src/main/java/com/bank/dqs/checks/DqsScoreCheck.java`.
  - [x] Implement `DqCheck` with canonical check type `DQS_SCORE` (uppercase; must match `check_config.check_type`).
  - [x] Guard `execute(DatasetContext context)` for null `context`, returning deterministic `NOT_RUN` detail payload.
  - [x] Keep per-dataset failure isolation: catch internal exceptions, return diagnostic detail payload, never throw from `execute()`.
  - [x] Accept a `ScoreInputProvider` abstraction (injected via constructor) to read per-check scores from the in-memory results of the current run.

- [x] Task 2: Implement per-check score extraction (AC: #1, #2)
  - [x] Map each Tier 1 check to a numeric score (0-100) derived from its status metric:
    - [x] `PASS` → 100.0
    - [x] `WARN` → 50.0
    - [x] `FAIL` → 0.0
    - [x] `NOT_RUN` or missing → treat as unavailable (excluded from weighted average with weight redistribution)
  - [x] Read check status from the list of `DqMetric` results already computed for this dataset (passed in via `ScoreInputProvider`).
  - [x] Determine per-check score from the status `MetricDetail` payload for each check type (FRESHNESS, VOLUME, SCHEMA, OPS).
  - [x] Use `check_type` constants from each check class: `FreshnessCheck.CHECK_TYPE`, `VolumeCheck.CHECK_TYPE`, `SchemaCheck.CHECK_TYPE`, `OpsCheck.CHECK_TYPE`.

- [x] Task 3: Implement configurable weight resolution (AC: #1, #4)
  - [x] Define default weights for each Tier 1 check:
    - [x] FRESHNESS: 0.30
    - [x] VOLUME: 0.30
    - [x] SCHEMA: 0.20
    - [x] OPS: 0.20
  - [x] Add `WeightProvider` abstraction (injected via constructor) for weight resolution.
  - [x] Implement `DefaultWeightProvider` returning the default weights above.
  - [x] Implement `JdbcWeightProvider` that reads custom weights from `check_config` rows for `check_type='DQS_SCORE'` using a JSON or structured `configuration` column — **OR** fall back to reading weights from `dataset_enrichment` using the context's `lookup_code`. See Dev Notes for the exact resolution contract.
  - [x] If no custom weights found, use defaults.

- [x] Task 4: Implement composite score computation with weight redistribution (AC: #2, #5)
  - [x] Compute composite score only from checks that are available (score is not `NOT_RUN`/missing).
  - [x] Redistribute unavailable check weights proportionally among available checks so weights always sum to 1.0.
  - [x] Formula: `composite_score = sum(normalized_weight_i * score_i)` where `normalized_weight_i = original_weight_i / sum(available_original_weights)`.
  - [x] Clamp final score to `[0.0, 100.0]`.
  - [x] If ALL checks are unavailable, emit `NOT_RUN` with reason `all_checks_unavailable`.

- [x] Task 5: Emit output metrics with explicit contract (AC: #2, #3)
  - [x] Emit `MetricNumeric(DQS_SCORE, "composite_score", <value>)` with check_type=`DQS_SCORE`.
  - [x] Emit `MetricDetail(DQS_SCORE, "dqs_score_breakdown", <json>)` with score breakdown per check:
    - [x] `status`: overall status (PASS ≥80, WARN ≥50, FAIL <50)
    - [x] `composite_score`: final score
    - [x] `checks`: array of `{check_type, score, weight, normalized_weight, contribution}` per participating check
    - [x] `unavailable_checks`: array of check types that were `NOT_RUN` or missing
    - [x] `reason`: rationale string (e.g., `composite_computed`, `partial_checks`, `all_checks_unavailable`)
  - [x] Sanitize error payloads (`error_type` class name only, no raw exception messages).
  - [x] Use `LinkedHashMap` for deterministic JSON field ordering (same pattern as all other checks).

- [x] Task 6: Register in `CheckFactory` and add dedicated unit test suite (AC: #1, #2, #3, #4, #5)
  - [x] Update `dqs-spark/src/test/java/com/bank/dqs/checks/CheckFactoryTest.java`:
    - [x] Add `registerDqsScoreCheckImplementationAddsRealCheck` test.
    - [x] Add `getEnabledChecksReturnsRegisteredDqsScoreCheckImplementation` test.
  - [x] Create `dqs-spark/src/test/java/com/bank/dqs/checks/DqsScoreCheckTest.java`.
  - [x] Cover minimum cases:
    - [x] `executeComputesCompositeScoreFromAllFourChecks` — all 4 checks PASS → score = 100
    - [x] `executeComputesCompositeScoreWhenSomeChecksFail` — mix of PASS/WARN/FAIL statuses
    - [x] `executeRedistributesWeightForUnavailableCheck` — one check NOT_RUN, score from remaining 3
    - [x] `executeReturnsNotRunWhenAllChecksUnavailable` — all checks NOT_RUN
    - [x] `executeUsesDefaultWeightsWhenNoCustomWeightsConfigured`
    - [x] `executeUsesCustomWeightsWhenProvided` — WeightProvider injects non-default weights
    - [x] `executeReturnsNotRunForNullContext`
    - [x] `executeSanitizesExecutionErrorPayload`
    - [x] `getCheckTypeReturnsDqsScore`
    - [x] `executeComputesScoreBreakdownDetailWithPerCheckContributions`
    - [x] `executeClampsFinalScoreToZeroToHundredRange`

- [x] Task 7: Maintain epic sequencing boundaries
  - [x] Do **not** implement `BatchWriter` or `DqsJob` wiring in this story (Story 2.10 owns this).
  - [x] Do **not** modify Postgres schema DDL in this story.
  - [x] Do **not** add serve/API/dashboard logic for `DQS_SCORE` check type.
  - [x] Do **not** change any existing check class (FreshnessCheck, VolumeCheck, SchemaCheck, OpsCheck).

## Dev Notes

### Story Context and Cross-Story Dependencies

- Stories 2.1 through 2.8 established `DqCheck`, `MetricNumeric`, `MetricDetail`, `DatasetContext`, `CheckFactory`, and all 4 Tier 1 check implementations (Freshness, Volume, Schema, Ops).
- **DQS Score is the only component that knows check types** — the serve layer, API, and dashboard have zero check-type awareness. All score logic lives exclusively in `DqsScoreCheck.java`.
- Story 2.10 will wire `DqsScoreCheck` into `DqsJob` along with `BatchWriter`. This story only creates the standalone class and its tests — do NOT wire into `DqsJob`.
- `DqsScoreCheck` is a `DqCheck` implementation; it receives a `DatasetContext` like all other checks. It does NOT read from HDFS or Spark DataFrame — it reads the in-memory metric results already produced by the other checks in the same run.

### How DqsScoreCheck Receives Per-Check Results

`DqsScoreCheck` cannot read from the database during the same run (the other checks' results aren't written yet — Story 2.10 does the write). It must receive the current run's metrics in-memory.

**Implementation contract for `ScoreInputProvider`:**

```java
@FunctionalInterface
public interface ScoreInputProvider {
    /**
     * Returns the DqMetric results already computed for this dataset in the current run.
     * These are the outputs from Freshness, Volume, Schema, and Ops checks.
     * May return null or empty list when called from tests without prior checks.
     */
    List<DqMetric> getCheckResults(DatasetContext context);
}
```

The `DqsScoreCheck` constructor accepts a `ScoreInputProvider`. In tests, inject a lambda. In Story 2.10's `DqsJob`, the accumulated metrics list from prior checks will be passed via the provider.

A default `NoOpScoreInputProvider` returns an empty list (treats all checks as unavailable → NOT_RUN).

### Per-Check Score Mapping

Extract the per-check status from the `MetricDetail` with the appropriate `detail_type`:

| Check | Status detail_type to read | CHECK_TYPE constant |
|---|---|---|
| Freshness | `freshness_status` | `FreshnessCheck.CHECK_TYPE` = `"FRESHNESS"` |
| Volume | `volume_status` | `VolumeCheck.CHECK_TYPE` = `"VOLUME"` |
| Schema | `schema_status` | `SchemaCheck.CHECK_TYPE` = `"SCHEMA"` |
| Ops | `ops_status` | `OpsCheck.CHECK_TYPE` = `"OPS"` |

Parse the `detail_value` JSON to extract the `"status"` field. Map:
- `"PASS"` → 100.0
- `"WARN"` → 50.0
- `"FAIL"` → 0.0
- `"NOT_RUN"` or absent/unparseable → treat as unavailable

Use Jackson `ObjectMapper` (already used in other checks) for JSON parsing:

```java
private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

private double extractScore(List<DqMetric> allMetrics, String checkType, String detailType) {
    return allMetrics.stream()
        .filter(m -> m instanceof MetricDetail)
        .map(m -> (MetricDetail) m)
        .filter(m -> checkType.equals(m.getCheckType()) && detailType.equals(m.getDetailType()))
        .findFirst()
        .map(m -> parseStatusScore(m.getDetailValue()))
        .orElse(Double.NaN); // NaN = unavailable
}
```

Use `Double.NaN` as the sentinel for "unavailable" internally (do not emit NaN in output — clamp/redistribute before emitting).

### Weight Resolution Contract

For MVP, use a simple `WeightProvider` interface:

```java
@FunctionalInterface
public interface WeightProvider {
    /**
     * Returns check weights keyed by check_type (e.g., "FRESHNESS" -> 0.30).
     * Must return weights summing to ~1.0 for all 4 Tier 1 checks.
     * May return a subset — missing checks use 0.0 weight.
     */
    Map<String, Double> getWeights(DatasetContext context);
}
```

Default weights (implement as `DefaultWeightProvider`):
```
FRESHNESS → 0.30
VOLUME    → 0.30
SCHEMA    → 0.20
OPS       → 0.20
```

For AC #4 (custom weights from `dataset_enrichment`), the `JdbcWeightProvider` implementation is scaffolded but may be a no-op for MVP tests — the test for custom weights should inject a `WeightProvider` lambda directly. The actual JDBC weight resolution from `dataset_enrichment` can be stubbed for this story; Story 2.10 will integrate with the real DB.

### Composite Score Formula

```
available_checks = [c for c in ALL_CHECKS if score(c) is not NaN]
unavailable_checks = [c for c in ALL_CHECKS if score(c) is NaN]

if available_checks is empty:
    → emit NOT_RUN with reason "all_checks_unavailable"
    return

total_available_weight = sum(weight(c) for c in available_checks)
for each c in available_checks:
    normalized_weight(c) = weight(c) / total_available_weight
    contribution(c) = normalized_weight(c) * score(c)

composite_score = sum(contribution(c) for c in available_checks)
composite_score = clamp(composite_score, 0.0, 100.0)
```

### Overall Status Thresholds

```
composite_score >= 80.0 → PASS
composite_score >= 50.0 → WARN
composite_score  < 50.0 → FAIL
```

### Output MetricDetail JSON Contract (`dqs_score_breakdown`)

```json
{
  "status": "PASS",
  "composite_score": 87.5,
  "reason": "composite_computed",
  "checks": [
    {
      "check_type": "FRESHNESS",
      "score": 100.0,
      "weight": 0.30,
      "normalized_weight": 0.30,
      "contribution": 30.0
    },
    {
      "check_type": "VOLUME",
      "score": 50.0,
      "weight": 0.30,
      "normalized_weight": 0.30,
      "contribution": 15.0
    },
    {
      "check_type": "SCHEMA",
      "score": 100.0,
      "weight": 0.20,
      "normalized_weight": 0.20,
      "contribution": 20.0
    },
    {
      "check_type": "OPS",
      "score": 100.0,
      "weight": 0.20,
      "normalized_weight": 0.20,
      "contribution": 20.0
    }
  ],
  "unavailable_checks": []
}
```

When one check is unavailable (`NOT_RUN`), its weight is redistributed:
- `unavailable_checks: ["SCHEMA"]`
- remaining weights renormalized from `{FRESHNESS:0.30, VOLUME:0.30, OPS:0.20}` → total `0.80` → normalized `{FRESHNESS:0.375, VOLUME:0.375, OPS:0.25}`
- `reason: "partial_checks"`

When ALL checks unavailable:
```json
{"status": "NOT_RUN", "reason": "all_checks_unavailable", "composite_score": 0.0, "checks": [], "unavailable_checks": ["FRESHNESS","VOLUME","SCHEMA","OPS"]}
```

### Existing Code to Reuse (Do Not Reinvent)

| File | What to Reuse |
|---|---|
| `dqs-spark/src/main/java/com/bank/dqs/checks/DqCheck.java` | Interface contract — implement it |
| `dqs-spark/src/main/java/com/bank/dqs/checks/CheckFactory.java` | Register `DqsScoreCheck` via `factory.register(new DqsScoreCheck())` |
| `dqs-spark/src/main/java/com/bank/dqs/checks/FreshnessCheck.java` | `CHECK_TYPE = "FRESHNESS"`, detail_type `"freshness_status"` |
| `dqs-spark/src/main/java/com/bank/dqs/checks/VolumeCheck.java` | `CHECK_TYPE = "VOLUME"`, detail_type `"volume_status"` |
| `dqs-spark/src/main/java/com/bank/dqs/checks/SchemaCheck.java` | `CHECK_TYPE = "SCHEMA"`, detail_type `"schema_status"` |
| `dqs-spark/src/main/java/com/bank/dqs/checks/OpsCheck.java` | `CHECK_TYPE = "OPS"`, detail_type `"ops_status"` |
| `dqs-spark/src/main/java/com/bank/dqs/model/MetricNumeric.java` | Emit composite_score metric |
| `dqs-spark/src/main/java/com/bank/dqs/model/MetricDetail.java` | Emit score breakdown detail |
| `dqs-spark/src/main/java/com/bank/dqs/model/DqsConstants.java` | `EXPIRY_SENTINEL` if any JDBC reads needed |

**Reuse these patterns from existing checks:**
- Constructor argument guards with `IllegalArgumentException`.
- `LinkedHashMap` JSON payload assembly for deterministic field order.
- `static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()` for JSON.
- Sanitize error payloads (`error_type = exception.getClass().getSimpleName()`).
- Deterministic `NOT_RUN` payloads for missing context.
- Per-dataset isolation: wrap all logic in `try/catch(Exception e)`, return diagnostic detail, never rethrow.

### Architecture Compliance Requirements

- All DQS score logic lives in `dqs-spark` (`DqsScoreCheck.java`). No code in `dqs-serve`, `dqs-orchestrator`, or `dqs-dashboard`.
- `DqsScoreCheck` is the **only component** that knows about Tier 1 check types — this is the explicit architectural decision. Do not add check-type awareness anywhere else.
- Output only to generic metric contracts (`dq_metric_numeric`, `dq_metric_detail`) through `MetricNumeric`/`MetricDetail`.
- Preserve check-type agnostic downstream behavior (serve/API/dashboard coupling to `DQS_SCORE` internals is forbidden).
- Data sensitivity boundary: aggregate scores only; no source values persisted.
- `DqsScoreCheck` does NOT trigger Spark DataFrame operations — it purely processes in-memory `DqMetric` objects. The `DatasetContext.getDf()` is not needed and should be ignored.

### File Structure Requirements

```text
dqs-spark/src/main/java/com/bank/dqs/checks/
  DqsScoreCheck.java                <-- NEW

dqs-spark/src/test/java/com/bank/dqs/checks/
  DqsScoreCheckTest.java            <-- NEW
  CheckFactoryTest.java             <-- MODIFY (add 2 tests)
```

No files outside `dqs-spark/` are required for this story. Do NOT create new top-level directories.

### Testing Requirements

`DqsScoreCheckTest` does NOT need a `SparkSession` — `DqsScoreCheck` only processes in-memory `DqMetric` objects, not Spark DataFrames. Use plain JUnit 5 with no SparkSession lifecycle.

Targeted test run:
```bash
cd /home/sas/workspace/data-quality-service/dqs-spark
mvn test -Dtest="DqsScoreCheckTest,CheckFactoryTest"
```

Full regression:
```bash
cd /home/sas/workspace/data-quality-service/dqs-spark
mvn test
```

Test naming convention: `<action><expectation>` (camelCase), matching existing checks:
- `executeComputesCompositeScoreFromAllFourChecks`
- `executeRedistributesWeightForUnavailableCheck`
- `getCheckTypeReturnsDqsScore`

Use `@BeforeAll`/`@AfterAll` only if H2 DB needed (not required for this story — no JDBC baseline reads).
Use `@BeforeEach` for test data setup.

Build the MetricDetail test inputs manually using `new MetricDetail(checkType, detailType, json)` to simulate prior check outputs.

### Previous Story Intelligence (from 2.8)

- Keep per-dataset failure isolation strict: catch `Exception`, not subclasses. Return diagnostic payload, never rethrow.
- Add explicit regression tests for every edge case in the acceptance criteria — don't skip cases.
- Keep payload sanitization strict (`error_type` = `exception.getClass().getSimpleName()` only; no raw exception message content).
- Maintain strict story scope: no opportunistic wiring into `DqsJob` (Story 2.10 owns that).
- When implementing the `ScoreInputProvider`, use the `@FunctionalInterface` pattern already established by `BaselineProvider` in FreshnessCheck, VolumeCheck, SchemaCheck, and OpsCheck.
- Constructor null-guard pattern: `if (provider == null) { throw new IllegalArgumentException("..."); }`.

### Git Intelligence Summary

- Last 5 commits confirm stable Epic 2 cadence: one check class + check-specific tests + CheckFactoryTest additions + story/sprint artifact sync.
- Pattern to follow: create `DqsScoreCheck.java` + `DqsScoreCheckTest.java` + modify `CheckFactoryTest.java` (add 2 tests for `DqsScoreCheck` registration and enablement).
- All prior commits use Java conventions consistently (PascalCase class, camelCase method/variable, `UPPER_SNAKE` constant, `static final` for shared instances).

### References

- Story source and ACs: `_bmad-output/planning-artifacts/epics/epic-2-dataset-discovery-tier-1-quality-checks.md` (Story 2.9)
- Architecture constraints: `_bmad-output/planning-artifacts/architecture.md` (Core Architectural Decisions, Structure Patterns, Data Boundaries — "DQS Score is a weighted composite computed in Spark — the only component that knows check types")
- Project guardrails: `_bmad-output/project-context.md` (Spark rules, data sensitivity boundary, anti-patterns, per-dataset failure isolation)
- Prior story file: `_bmad-output/implementation-artifacts/2-8-ops-check-implementation.md`
- Existing checks: `dqs-spark/src/main/java/com/bank/dqs/checks/` (all check classes for reuse and pattern reference)

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

None — implementation succeeded on first attempt with no debugging required.

### Completion Notes List

- Created `DqsScoreCheck.java` implementing `DqCheck` with `CHECK_TYPE = "DQS_SCORE"`.
- Implemented `ScoreInputProvider` and `WeightProvider` as inner `@FunctionalInterface`s matching the established pattern from prior checks (BaselineProvider in FreshnessCheck/VolumeCheck/SchemaCheck/OpsCheck).
- Three constructors: no-arg (NoOpScoreInputProvider + DefaultWeightProvider), single-arg (ScoreInputProvider + DefaultWeightProvider), two-arg (full injection).
- Score mapping: PASS→100.0, WARN→50.0, FAIL→0.0, NOT_RUN/missing→Double.NaN (sentinel for unavailability).
- Weight redistribution: when a check is unavailable, its weight is proportionally redistributed among available checks using `normalizedWeight = weight / totalAvailableWeight`.
- All-unavailable guard: returns NOT_RUN with reason `all_checks_unavailable` before any computation.
- Emits `MetricNumeric(DQS_SCORE, "composite_score", value)` + `MetricDetail(DQS_SCORE, "dqs_score_breakdown", json)`.
- Breakdown JSON includes: status, composite_score, reason, checks array (check_type/score/weight/normalized_weight/contribution), unavailable_checks array.
- Status thresholds: ≥80→PASS, ≥50→WARN, <50→FAIL.
- Per-dataset failure isolation: catch Exception, return NOT_RUN with sanitized error_type, never rethrow.
- Null context guard: returns NOT_RUN with reason `missing_context`.
- Removed `@Disabled` from all 11 DqsScoreCheckTest tests and 2 CheckFactoryTest tests (green phase).
- Full test run: 152 tests, 0 failures, 0 errors, 0 skipped — no regressions.

### File List

- `dqs-spark/src/main/java/com/bank/dqs/checks/DqsScoreCheck.java` (NEW)
- `dqs-spark/src/test/java/com/bank/dqs/checks/DqsScoreCheckTest.java` (MODIFIED — removed @Disabled)
- `dqs-spark/src/test/java/com/bank/dqs/checks/CheckFactoryTest.java` (MODIFIED — removed @Disabled from 2 tests)

## Tasks / Review Findings

### Review Findings

- [x] [Review][Patch] DefaultWeightProvider returns mutable LinkedHashMap — callers could corrupt shared defaults [DqsScoreCheck.java:355-368] — fixed: wrapped with Collections.unmodifiableMap
- [x] [Review][Patch] CheckFactoryTest comment still says "TDD RED PHASE — @Disabled" — stale documentation [CheckFactoryTest.java:317] — fixed: updated comment
- [x] [Review][Patch] All-unavailable path redundantly builds allCheckTypes when unavailableCheckTypes already holds all four [DqsScoreCheck.java:132-138] — fixed: pass unavailableCheckTypes directly
- [x] [Review][Patch] Missing tests for null-argument constructor guards (single-arg null, two-arg null ScoreInputProvider, two-arg null WeightProvider) [DqsScoreCheckTest.java] — fixed: added 3 new tests
- [x] [Review][Patch] executeClampsFinalScoreToZeroToHundredRange comment says "prevent values > 100" but score is naturally 100, not clamped from above — added clarifying comment and new all-FAIL lower-bound test [DqsScoreCheckTest.java] — fixed: improved comment + added executeClampsFinalScoreAtLowerBound test

## Change Log

- 2026-04-03: Implemented DqsScoreCheck with ScoreInputProvider, WeightProvider, DefaultWeightProvider, JdbcWeightProvider (MVP stub), NoOpScoreInputProvider. Enabled all 13 ATDD tests. Full regression: 152 tests pass.
- 2026-04-03: Code review complete. Fixed 5 findings (1 Medium, 4 Low). Full regression: 156 tests pass, 0 failures.
