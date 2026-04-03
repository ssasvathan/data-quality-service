# Code Review Report: Story 2.7

Date: 2026-04-03  
Story: `2-7-schema-check-implementation`  
Review scope: uncommitted story changes in `dqs-spark` plus story/sprint artifacts

## Layered Review Summary

- Blind Hunter: 2 findings
- Edge Case Hunter: 2 findings
- Acceptance Auditor: 1 finding
- Deduplicated actionable findings: 2
- Decision-needed: 0
- Deferred: 0
- Dismissed as noise: 3

## Findings (All Resolved)

1. `MEDIUM` Hash mismatch with a legacy/incomplete baseline payload (hash present but missing `schema_json`) caused an exception path and emitted `NOT_RUN`, which suppresses deterministic drift classification.
   - Fix: Added deterministic fallback classification for this edge case, emitting `FAIL` (`schema_breaking_change`) with a synthetic `changed_fields` entry keyed as `$schema` using baseline/current hash values.
   - Evidence: `dqs-spark/src/main/java/com/bank/dqs/checks/SchemaCheck.java` (`evaluateDiff`).

2. `LOW` Test coverage did not lock the hash-only baseline mismatch path, so future refactors could regress back to `NOT_RUN`.
   - Fix: Added `executeFailsWhenBaselineHashDiffersButSchemaJsonMissing` regression test.
   - Evidence: `dqs-spark/src/test/java/com/bank/dqs/checks/SchemaCheckTest.java`.

## Validation

- Ran targeted tests: `mvn test -Dtest="SchemaCheckTest,CheckFactoryTest"` (pass, 25 tests).
- Ran full module tests: `mvn test` (pass, 128 tests).

## Outcome

- Unresolved findings: 0
- Story status updated to `done`
- Sprint tracking synced: `2-7-schema-check-implementation: done`
