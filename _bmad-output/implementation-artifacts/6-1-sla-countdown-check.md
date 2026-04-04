# Story 6.1: SLA Countdown Check

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a **data steward**,
I want the SLA Countdown check to measure time remaining against the expected delivery window,
so that I can monitor whether datasets are arriving within their service level agreements.

## Acceptance Criteria

1. **Given** a dataset with `sla_hours` configured in `dataset_enrichment`
   **When** the SLA Countdown check executes
   **Then** it computes hours remaining until SLA breach (positive = time left, negative = overdue) and writes a `MetricNumeric` with `check_type=SLA_COUNTDOWN`, `metric_name=hours_remaining`
   **And** it determines PASS (within SLA), WARN (approaching — ≤20% of window remaining), or FAIL (breached — negative hours remaining)

2. **Given** a dataset with no SLA configuration in `dataset_enrichment` (no matching row or `sla_hours IS NULL`)
   **When** the SLA Countdown check is invoked
   **Then** it skips gracefully and returns an empty list (check not applicable, no metrics written)

3. **And** the check implements `DqCheck`, is registered in `CheckFactory` via `DqsJob.buildCheckFactory()`, and requires zero changes to serve/API/dashboard

## Tasks / Subtasks

- [x] Task 1: Create `SlaCountdownCheck.java` in `dqs-spark/src/main/java/com/bank/dqs/checks/` (AC: 1, 2)
  - [x] Implement `DqCheck` interface — `getCheckType()` returns `"SLA_COUNTDOWN"`
  - [x] Constructor: `SlaCountdownCheck()` (no-arg, uses `new JdbcSlaProvider()` and `Clock.systemDefaultZone()`)
  - [x] Constructor: `SlaCountdownCheck(SlaProvider slaProvider, Clock clock)` — for testability
  - [x] `execute(DatasetContext context)`: guard null context → return empty list (not applicable)
  - [x] Query `sla_hours` from `v_dataset_enrichment_active` via `SlaProvider.getSlaHours(context)`
  - [x] If `sla_hours` returns empty → return empty list (skip gracefully, AC2)
  - [x] Compute `hoursRemaining = slaHours - hoursSincePartitionDate()` where `hoursSincePartitionDate = Duration.between(context.getPartitionDate().atStartOfDay(ZoneId.systemDefault()).toInstant(), clock.instant()).toHours()`
  - [x] Write `MetricNumeric(CHECK_TYPE, METRIC_HOURS_REMAINING, hoursRemaining)` — always written when SLA configured
  - [x] Determine status: `hoursRemaining < 0` → FAIL; `hoursRemaining <= slaHours * 0.20` → WARN; else → PASS
  - [x] Write `MetricDetail(CHECK_TYPE, DETAIL_TYPE_STATUS, json)` with payload: `{status, reason, hours_remaining, sla_hours, warn_threshold}`
  - [x] Wrap entire `execute()` body in try/catch — on exception return list with single error detail metric
  - [x] Define inner `SlaProvider` functional interface: `Optional<Double> getSlaHours(DatasetContext ctx) throws Exception`
  - [x] Define inner `JdbcSlaProvider` implementing `SlaProvider` — queries `v_dataset_enrichment_active` via JDBC
  - [x] Define inner `ConnectionProvider` functional interface (same pattern as `FreshnessCheck`)

- [x] Task 2: Register `SlaCountdownCheck` in `DqsJob.buildCheckFactory()` (AC: 3)
  - [x] Add `f.register(new SlaCountdownCheck())` in `buildCheckFactory()` — BEFORE `DqsScoreCheck` registration
  - [x] Import `com.bank.dqs.checks.SlaCountdownCheck` in `DqsJob.java`

- [x] Task 3: Write `SlaCountdownCheckTest.java` in `dqs-spark/src/test/java/com/bank/dqs/checks/` (AC: 1, 2)
  - [x] Test: `executeReturnsHoursRemainingAndPassWhenWithinSla` — SLA 24h, 12h elapsed → PASS, `hours_remaining=12.0`
  - [x] Test: `executeReturnsWarnWhenApproachingSlaThreshold` — SLA 10h, 8.5h elapsed → WARN (1.5h left, ≤20% of 10h)
  - [x] Test: `executeReturnsFailWhenSlaBreached` — SLA 12h, 13h elapsed → FAIL, negative `hours_remaining`
  - [x] Test: `executeReturnsEmptyListWhenNoSlaConfigured` — SlaProvider returns empty → empty list
  - [x] Test: `executeReturnsEmptyListWhenContextIsNull` — null context → empty list
  - [x] Test: `executeHandlesExceptionFromSlaProviderGracefully` — SlaProvider throws → returns error detail metric, does NOT propagate
  - [x] Test: `getCheckTypeReturnsSlaCountdown` — `assertEquals("SLA_COUNTDOWN", check.getCheckType())`
  - [x] No SparkSession needed — SLA check does NOT use `context.getDf()` (unlike volume/freshness)

## Dev Notes

### SLA Countdown Logic — No DataFrame Interaction

Unlike Freshness or Volume checks, SLA Countdown does NOT process `context.getDf()`. It computes purely from:
- `context.getPartitionDate()` — the date being processed
- `sla_hours` from `dataset_enrichment` — the configured delivery window
- `Clock` — for testable "now" injection

The check measures: **has the dataset arrived within its expected delivery window since the start of the partition date?**

```
hours_elapsed = Duration.between(partitionDate.atStartOfDay(zone), now).toHours()
hours_remaining = sla_hours - hours_elapsed
```

### Check Type and Metric Names

```java
public static final String CHECK_TYPE           = "SLA_COUNTDOWN";
static final String METRIC_HOURS_REMAINING     = "hours_remaining";
static final String DETAIL_TYPE_STATUS         = "sla_countdown_status";

// Status constants
private static final String STATUS_PASS        = "PASS";
private static final String STATUS_WARN        = "WARN";
private static final String STATUS_FAIL        = "FAIL";
private static final String STATUS_NOT_RUN     = "NOT_RUN";

// Reason constants
private static final String REASON_WITHIN_SLA  = "within_sla";
private static final String REASON_APPROACHING = "approaching_sla";
private static final String REASON_BREACHED    = "sla_breached";
private static final String REASON_EXECUTION_ERROR = "execution_error";
```

### WARN Threshold

WARN = ≤ 20% of `sla_hours` remaining (approaching deadline). Example: SLA=10h, warn when `hours_remaining ≤ 2.0`.

```java
double warnThreshold = slaHours * 0.20;
if (hoursRemaining < 0.0) {
    status = STATUS_FAIL; reason = REASON_BREACHED;
} else if (hoursRemaining <= warnThreshold) {
    status = STATUS_WARN; reason = REASON_APPROACHING;
} else {
    status = STATUS_PASS; reason = REASON_WITHIN_SLA;
}
```

### Detail Payload Structure

```java
Map<String, Object> payload = new LinkedHashMap<>();
payload.put("status", status);
payload.put("reason", reason);
payload.put("hours_remaining", hoursRemaining);
payload.put("sla_hours", slaHours);
payload.put("warn_threshold", warnThreshold);
// Serialise with ObjectMapper — same toJson() pattern as FreshnessCheck/VolumeCheck
```

### JdbcSlaProvider — Query Pattern

Query `v_dataset_enrichment_active` using **LIKE reversal** (same pattern as `EnrichmentResolver`):

```java
private static final String SLA_QUERY =
    "SELECT sla_hours FROM v_dataset_enrichment_active "
    + "WHERE ? LIKE dataset_pattern AND sla_hours IS NOT NULL "
    + "ORDER BY id ASC LIMIT 1";
```

- Candidate string = `context.getDatasetName()` — same as CheckFactory's `getEnabledChecks` approach
- Returns `Optional.of(slaHours)` if found, `Optional.empty()` if no row or sla_hours is null
- Use try-with-resources for JDBC connection + PreparedStatement + ResultSet (per project rule)
- `ConnectionProvider` functional interface (same as `FreshnessCheck.ConnectionProvider`):

```java
@FunctionalInterface
public interface ConnectionProvider {
    Connection getConnection() throws SQLException;
}

public static final class JdbcSlaProvider implements SlaProvider {
    private final ConnectionProvider connectionProvider;
    // constructor validates non-null
    // getBaseline queries v_dataset_enrichment_active
}
```

### No-Arg Constructor (Default for Production)

Unlike FreshnessCheck/VolumeCheck which use a `NoOpBaselineProvider` default, `SlaCountdownCheck()` must use a **real JDBC connection** in production. However, since `DqsJob` creates `SlaCountdownCheck` via `new SlaCountdownCheck()` and the JDBC URL is available via system config, the pattern is:

```java
public SlaCountdownCheck() {
    // No-arg constructor used by DqsJob.buildCheckFactory()
    // JdbcSlaProvider requires a ConnectionProvider — but DqsJob manages JDBC per-dataset
    // Use NoOpSlaProvider as default (returns empty = skip), letting DqsJob wire JDBC separately
    // OR: accept that no-arg = no-op, and wire via the 2-arg constructor in DqsJob
    this(new NoOpSlaProvider(), Clock.systemDefaultZone());
}
```

**IMPORTANT:** Looking at how `DqsJob.buildCheckFactory()` creates `FreshnessCheck()` and `VolumeCheck()` — these use `NoOpBaselineProvider` by default and the `JdbcBaselineProvider` is never wired in DqsJob currently (history lookups are optional in Tier 1). For `SlaCountdownCheck`, no-arg = no-op (returns empty = check skipped) is acceptable for the first Tier 2 story. The `JdbcSlaProvider` enables full testing via the 2-arg constructor. Register `f.register(new SlaCountdownCheck())` in `buildCheckFactory()`.

If production wiring with JDBC is needed, the test suite validates the JDBC path through `JdbcSlaProvider` directly — the no-arg `DqsJob` usage uses `NoOpSlaProvider` (no-op = check skipped). Document this limitation in a `// TODO: wire JdbcSlaProvider` comment in `buildCheckFactory()`.

### File Structure — Exact Locations

```
dqs-spark/
  src/main/java/com/bank/dqs/
    checks/
      SlaCountdownCheck.java    ← NEW file
    DqsJob.java                 ← MODIFY: register SlaCountdownCheck in buildCheckFactory()
  src/test/java/com/bank/dqs/
    checks/
      SlaCountdownCheckTest.java  ← NEW file
```

**Do NOT touch:** `CheckFactory.java` (no changes needed — registration happens in `DqsJob`), `DqCheck.java`, any model files, writer, scanner, or serve-layer files.

### Java Patterns — From Project Context + Existing Checks

- **try-with-resources** for all JDBC: `try (Connection conn = ...; PreparedStatement ps = ...; ResultSet rs = ...)` — never manual `.close()`
- **PreparedStatement** with `?` — NEVER string concatenation
- **`throws SQLException`** on JDBC method signatures
- **Constructor validation** with `IllegalArgumentException` for null args
- **Jackson `ObjectMapper`** (static final) for JSON payload serialisation — same as FreshnessCheck/VolumeCheck
- **Static imports** NOT needed for SLA check (no Spark SQL functions used)
- **Full generic types**: `List<DqMetric>`, `Optional<Double>` — never raw types
- **Naming**: PascalCase class, camelCase methods, UPPER_SNAKE constants
- **Package**: `com.bank.dqs.checks`

### Test Pattern — No SparkSession Required

`SlaCountdownCheck` does not call `context.getDf()`, so **no SparkSession is needed** in the test class. Use plain JUnit 5 — no `@BeforeAll`/`@AfterAll` SparkSession lifecycle:

```java
class SlaCountdownCheckTest {
    private static final LocalDate PARTITION_DATE = LocalDate.of(2026, 4, 3);

    private DatasetContext context() {
        return new DatasetContext(
            "lob=retail/src_sys_nm=alpha/dataset=sales_daily",
            "ALPHA",
            PARTITION_DATE,
            "/prod/data",
            null,   // df=null is safe — SLA check never calls getDf()
            DatasetContext.FORMAT_PARQUET
        );
    }

    private SlaCountdownCheck checkWithSla(Optional<Double> slaHours, Instant fixedNow) {
        SlaCountdownCheck.SlaProvider provider = ctx -> slaHours;
        Clock clock = Clock.fixed(fixedNow, ZoneId.systemDefault());
        return new SlaCountdownCheck(provider, clock);
    }
    // ...
}
```

**Clock injection pattern** — fix "now" to a known instant:
```java
// Partition date = 2026-04-03, SLA = 24h, want 12h elapsed:
Instant fixedNow = LocalDate.of(2026, 4, 3)
    .atStartOfDay(ZoneId.systemDefault())
    .plusHours(12)
    .toInstant();
```

### Anti-Patterns — NEVER Do These

- **NEVER call `context.getDf()`** — SLA check is time-based only; the DataFrame is irrelevant
- **NEVER hardcode `9999-12-31 23:59:59`** — use `DqsConstants.EXPIRY_SENTINEL` in any DB queries inside this class
- **NEVER query raw `dataset_enrichment` table** — always `v_dataset_enrichment_active` view
- **NEVER let exceptions propagate** from `execute()` — catch all, return error detail
- **NEVER add check-type-specific logic to serve/API/dashboard** — only Spark knows `SLA_COUNTDOWN`
- **NEVER create a second `CheckFactory` class** — just register via `DqsJob.buildCheckFactory()`

### Data Sensitivity Note

The SLA check outputs only numeric hours metrics and status/reason strings. No dataset values or row content is ever touched. The `sla_hours` threshold is configuration metadata, not PII/PCI — safe to include in metric payload.

### Existing Fixtures — SLA Hours Already in Test Data

`dqs-serve/src/serve/schema/fixtures.sql` already inserts `dataset_enrichment` rows with `sla_hours`:
- `src_sys_nm=omni/%` → `sla_hours = 24`
- `lob=commercial/%` → `sla_hours = 12`
- `lob=retail/%` → `sla_hours = 8`

These are available for integration tests if `JdbcSlaProvider` is tested against a real DB. The unit tests in `SlaCountdownCheckTest.java` use mock `SlaProvider` — no DB required.

### DqsJob Integration — Where to Register

In `DqsJob.java`, method `buildCheckFactory()` (lines ~306-316):

```java
private static CheckFactory buildCheckFactory(List<DqMetric> accumulator) {
    CheckFactory f = new CheckFactory();
    f.register(new FreshnessCheck());
    f.register(new VolumeCheck());
    f.register(new SchemaCheck());
    f.register(new OpsCheck());
    f.register(new SlaCountdownCheck()); // ADD: Tier 2 — Epic 6, Story 6.1
    // TODO: wire JdbcSlaProvider via ConnectionProvider once JDBC connection threading is resolved
    // DqsScoreCheck is registered LAST — always runs after all other checks
    f.register(new DqsScoreCheck(ctx -> accumulator));
    return f;
}
```

Add import at top: `import com.bank.dqs.checks.SlaCountdownCheck;`

### References

- Epic 6 Story 6.1 AC: `_bmad-output/planning-artifacts/epics/epic-6-tier-2-quality-checks-phase-2.md`
- DqCheck interface: `dqs-spark/src/main/java/com/bank/dqs/checks/DqCheck.java`
- CheckFactory: `dqs-spark/src/main/java/com/bank/dqs/checks/CheckFactory.java`
- FreshnessCheck (pattern to copy): `dqs-spark/src/main/java/com/bank/dqs/checks/FreshnessCheck.java`
- VolumeCheck (pattern to copy): `dqs-spark/src/main/java/com/bank/dqs/checks/VolumeCheck.java`
- DqsJob (register here): `dqs-spark/src/main/java/com/bank/dqs/DqsJob.java`
- DatasetContext: `dqs-spark/src/main/java/com/bank/dqs/model/DatasetContext.java`
- MetricNumeric: `dqs-spark/src/main/java/com/bank/dqs/model/MetricNumeric.java`
- MetricDetail: `dqs-spark/src/main/java/com/bank/dqs/model/MetricDetail.java`
- DqsConstants (EXPIRY_SENTINEL): `dqs-spark/src/main/java/com/bank/dqs/model/DqsConstants.java`
- EnrichmentResolver (LIKE reversal SQL pattern): `dqs-spark/src/main/java/com/bank/dqs/scanner/EnrichmentResolver.java`
- Schema DDL (dataset_enrichment.sla_hours): `dqs-serve/src/serve/schema/ddl.sql`
- Active-record views: `dqs-serve/src/serve/schema/views.sql`
- Test fixture SLA data: `dqs-serve/src/serve/schema/fixtures.sql` (lines ~243-260)
- VolumeCheckTest (test pattern to copy): `dqs-spark/src/test/java/com/bank/dqs/checks/VolumeCheckTest.java`
- Project Context — Java rules + anti-patterns: `_bmad-output/project-context.md`
- Architecture — Check extensibility: `_bmad-output/planning-artifacts/architecture.md#Core Architectural Decisions`

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

_No debug issues encountered. Implementation was straightforward following FreshnessCheck patterns._

### Completion Notes List

- Implemented `SlaCountdownCheck.java` following the FreshnessCheck pattern: `DqCheck` interface, two constructors (no-arg + testable 2-arg), try/catch isolation, Jackson JSON serialisation.
- The check is purely time-based (no DataFrame interaction). Computes `hoursRemaining = slaHours - Duration.between(partitionDate.atStartOfDay(), now).toHours()`.
- Status thresholds: FAIL if `hoursRemaining < 0`; WARN if `hoursRemaining <= slaHours * 0.20`; else PASS.
- No-arg constructor uses `NoOpSlaProvider` (returns empty → check skipped) per story guidance. `JdbcSlaProvider` available for production wiring via 2-arg constructor.
- `DqsJob.buildCheckFactory()` registers `new SlaCountdownCheck()` before `DqsScoreCheck` with a TODO comment for future JDBC wiring.
- All 7 ATDD tests pass (GREEN phase). Full suite: 184 tests, 0 failures, 0 regressions.

### File List

- `dqs-spark/src/main/java/com/bank/dqs/checks/SlaCountdownCheck.java` (NEW)
- `dqs-spark/src/main/java/com/bank/dqs/DqsJob.java` (MODIFIED — added import + registration)
- `dqs-spark/src/test/java/com/bank/dqs/checks/SlaCountdownCheckTest.java` (pre-existing ATDD file, no changes needed)

### Review Findings

_Code review conducted 2026-04-04 — 3 layers (Blind Hunter, Edge Case Hunter, Acceptance Auditor)_

- [x] [Review][Defer] `ZoneId.systemDefault()` timezone portability — partition-date midnight uses JVM default zone; if Spark cluster JVM zone differs from Eastern, results shift. Pre-existing design decision per spec — not actionable now. [SlaCountdownCheck.java:115] — deferred, pre-existing

## Change Log

- 2026-04-04: Story 6-1 implemented — created SlaCountdownCheck.java (Tier 2 SLA countdown check), registered in DqsJob.buildCheckFactory(). All 7 ATDD tests pass. Full regression suite: 184 tests, 0 failures.
- 2026-04-04: Code review complete — clean review. 0 patch, 0 decision-needed, 1 deferred (timezone portability), 3 dismissed. Status advanced to done.
