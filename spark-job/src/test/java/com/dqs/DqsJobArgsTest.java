package com.dqs;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class DqsJobArgsTest {

    @Test
    void validateArgsThrowsWhenTooFewArgs() {
        assertThrows(IllegalArgumentException.class,
                () -> DqsJob.validateArgs(new String[]{"only-one"}));
    }

    @Test
    void validateArgsThrowsWhenTooManyArgs() {
        assertThrows(IllegalArgumentException.class,
                () -> DqsJob.validateArgs(new String[]{"a","b","c","d","e","f","g","h"}));
    }

    @Test
    void validateArgsPassesForExactlySevenArgs() {
        assertDoesNotThrow(() -> DqsJob.validateArgs(
                new String[]{"/path/config.json", "/data/input", "localhost", "5432", "postgres", "user", "pass"}));
    }
}
