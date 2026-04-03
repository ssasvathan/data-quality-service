# Deferred Work

Items deferred during code reviews, noted here for future sprint planning.

## Deferred from: code review of 1-2-implement-core-schema-with-temporal-pattern (2026-04-03)

- No Java unit test for `DqsConstants.java` (`dqs-spark/src/main/java/com/bank/dqs/model/DqsConstants.java`) — AC3 only requires the constant to be "documented" in dqs-spark; no Java test task was included in story 1-2 scope. Consider adding a JUnit test for the constant value in a future story or as part of story 1-3 when more Java model content is added.
