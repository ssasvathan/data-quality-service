package com.bank.dqs;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the CLI argument parsing logic extracted from DqsJob.
 *
 * <p>Tests target the package-private {@code DqsJob.parseArgs(String[])} static method
 * and the {@code DqsJob.DqsJobArgs} record. No SparkSession required.
 */
class DqsJobArgParserTest {

    // ---------------------------------------------------------------------------
    // AC3: --parent-path is parsed correctly
    // ---------------------------------------------------------------------------

    @Test
    void parseArgsExtractsParentPath() {
        String[] args = {"--parent-path", "/prod/abc/data"};

        DqsJob.DqsJobArgs result = DqsJob.parseArgs(args);

        assertEquals("/prod/abc/data", result.parentPath(),
                "parentPath must equal the value passed after --parent-path");
    }

    // ---------------------------------------------------------------------------
    // AC3: --date absent → defaults to LocalDate.now()
    // ---------------------------------------------------------------------------

    @Test
    void parseArgsDefaultsToTodayWhenDateAbsent() {
        String[] args = {"--parent-path", "/prod/abc/data"};
        LocalDate before = LocalDate.now();

        DqsJob.DqsJobArgs result = DqsJob.parseArgs(args);

        LocalDate after = LocalDate.now();
        assertTrue(
                !result.partitionDate().isBefore(before) && !result.partitionDate().isAfter(after),
                "partitionDate should default to today's date when --date is absent"
        );
    }

    // ---------------------------------------------------------------------------
    // AC3: --date 20260325 → LocalDate.of(2026, 3, 25)
    // ---------------------------------------------------------------------------

    @Test
    void parseArgsExtractsDateArg() {
        String[] args = {"--parent-path", "/prod/abc/data", "--date", "20260325"};

        DqsJob.DqsJobArgs result = DqsJob.parseArgs(args);

        assertEquals(LocalDate.of(2026, 3, 25), result.partitionDate(),
                "partitionDate must parse yyyyMMdd format correctly");
    }

    // ---------------------------------------------------------------------------
    // AC3: Missing --parent-path → IllegalArgumentException
    // ---------------------------------------------------------------------------

    @Test
    void parseArgsThrowsOnMissingParentPath() {
        String[] args = {"--date", "20260325"};

        assertThrows(IllegalArgumentException.class,
                () -> DqsJob.parseArgs(args),
                "parseArgs must throw IllegalArgumentException when --parent-path is absent");
    }

    // ---------------------------------------------------------------------------
    // AC3: Wrong date format → IllegalArgumentException
    // ---------------------------------------------------------------------------

    @Test
    void parseArgsThrowsOnInvalidDateFormat() {
        String[] args = {"--parent-path", "/prod/abc/data", "--date", "2026-03-25"};

        assertThrows(IllegalArgumentException.class,
                () -> DqsJob.parseArgs(args),
                "parseArgs must throw IllegalArgumentException for date format other than yyyyMMdd");
    }

    // ---------------------------------------------------------------------------
    // Null and empty args → IllegalArgumentException
    // ---------------------------------------------------------------------------

    @Test
    void parseArgsThrowsOnNullArgs() {
        assertThrows(IllegalArgumentException.class,
                () -> DqsJob.parseArgs(null),
                "parseArgs must throw IllegalArgumentException when args is null");
    }

    @Test
    void parseArgsThrowsOnEmptyArgs() {
        assertThrows(IllegalArgumentException.class,
                () -> DqsJob.parseArgs(new String[]{}),
                "parseArgs must throw IllegalArgumentException when args is empty");
    }

    // ---------------------------------------------------------------------------
    // Dangling flag (flag without value) → IllegalArgumentException
    // ---------------------------------------------------------------------------

    @Test
    void parseArgsThrowsOnDanglingParentPathFlag() {
        String[] args = {"--parent-path"};

        assertThrows(IllegalArgumentException.class,
                () -> DqsJob.parseArgs(args),
                "parseArgs must throw IllegalArgumentException when --parent-path has no value");
    }

    @Test
    void parseArgsThrowsOnDanglingDateFlag() {
        String[] args = {"--parent-path", "/prod/abc/data", "--date"};

        assertThrows(IllegalArgumentException.class,
                () -> DqsJob.parseArgs(args),
                "parseArgs must throw IllegalArgumentException when --date has no value");
    }
}
