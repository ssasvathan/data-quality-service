package com.dqs;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class DqsJobArgsTest {

    @Test
    void validateArgsThrowsWhenTooFewArgs() {
        assertThrows(IllegalArgumentException.class, () -> DqsJob.validateArgs(new String[]{"1", "2", "3", "4", "5", "6", "7"}));
    }

    @Test
    void validateArgsThrowsWhenTooManyArgs() {
        assertThrows(IllegalArgumentException.class, () -> DqsJob.validateArgs(new String[]{"1", "2", "3", "4", "5", "6", "7", "8", "9"}));
    }

    @Test
    void validateArgsPassesForExactlyEightArgs() {
        assertDoesNotThrow(() -> DqsJob.validateArgs(new String[]{"1", "2", "3", "4", "5", "6", "7", "8"}));
    }
}
