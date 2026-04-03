package com.bank.dqs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for DqsJob DB property loading helpers.
 */
class DqsJobConfigLoadingTest {

    @TempDir
    Path tempDir;

    @Test
    void loadPropertiesFromFileIfPresentLoadsValues() throws Exception {
        Path configFile = tempDir.resolve("application.properties");
        Files.writeString(configFile, """
                db.url=jdbc:postgresql://db-host:5432/dqs
                db.user=test_user
                db.password=test_password
                """);

        Properties props = new Properties();
        boolean loaded = DqsJob.loadPropertiesFromFileIfPresent(props, configFile);

        assertTrue(loaded, "Expected config file to be loaded");
        assertEquals("jdbc:postgresql://db-host:5432/dqs", props.getProperty("db.url"));
        assertEquals("test_user", props.getProperty("db.user"));
        assertEquals("test_password", props.getProperty("db.password"));
    }

    @Test
    void loadPropertiesFromFileIfPresentReturnsFalseForMissingFile() throws Exception {
        Path missing = tempDir.resolve("missing.properties");

        Properties props = new Properties();
        boolean loaded = DqsJob.loadPropertiesFromFileIfPresent(props, missing);

        assertFalse(loaded, "Missing config file should not be loaded");
        assertTrue(props.isEmpty(), "Properties should remain empty when file is missing");
    }

    @Test
    void loadApplicationPropertiesFallsBackToFilesystemCandidates() throws Exception {
        Path configFile = tempDir.resolve("application.properties");
        Files.writeString(configFile, """
                custom.only.for.test=filesystem-fallback-loaded
                """);

        Properties props = DqsJob.loadApplicationProperties(List.of(configFile));

        assertEquals("filesystem-fallback-loaded", props.getProperty("custom.only.for.test"),
                "Expected fallback filesystem config to be loaded when classpath resource is absent");
    }
}
