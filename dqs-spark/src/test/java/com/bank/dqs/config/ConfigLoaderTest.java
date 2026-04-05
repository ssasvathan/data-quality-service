package com.bank.dqs.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigLoaderTest {

    @TempDir
    Path tempDir;

    // -----------------------------------------------------------------------
    // loadLayer
    // -----------------------------------------------------------------------

    @Test
    void loadLayerFindsFileInCandidateDirectory() throws Exception {
        Files.writeString(tempDir.resolve("application.properties"), "db.url=jdbc:h2:mem:test\n");

        Properties props = new Properties();
        boolean loaded = ConfigLoader.loadLayer(props, "application.properties", List.of(tempDir));

        assertTrue(loaded);
        assertEquals("jdbc:h2:mem:test", props.getProperty("db.url"));
    }

    @Test
    void loadLayerReturnsFalseWhenFileNotFound() throws Exception {
        Properties props = new Properties();
        boolean loaded = ConfigLoader.loadLayer(props, "missing.properties", List.of(tempDir));

        assertFalse(loaded);
        assertTrue(props.isEmpty());
    }

    @Test
    void loadLayerSearchesMultipleCandidates() throws Exception {
        Path secondDir = tempDir.resolve("second");
        Files.createDirectory(secondDir);
        Files.writeString(secondDir.resolve("app.properties"), "key=from-second\n");

        Properties props = new Properties();
        boolean loaded = ConfigLoader.loadLayer(props, "app.properties", List.of(tempDir, secondDir));

        assertTrue(loaded);
        assertEquals("from-second", props.getProperty("key"));
    }

    // -----------------------------------------------------------------------
    // Environment overlay
    // -----------------------------------------------------------------------

    @Test
    void loadAppliesEnvironmentOverlay() throws Exception {
        Files.writeString(tempDir.resolve("application.properties"),
                "db.url=base-url\ndb.user=base-user\n");
        Files.writeString(tempDir.resolve("application-prod.properties"),
                "db.url=prod-url\n");

        // Use system property to set env (cleaned up after test)
        System.setProperty("dqs.env", "prod");
        try {
            Properties props = ConfigLoader.load(List.of(tempDir));
            assertEquals("prod-url", props.getProperty("db.url"), "prod overlay should override base");
            assertEquals("base-user", props.getProperty("db.user"), "non-overridden key should survive");
        } finally {
            System.clearProperty("dqs.env");
        }
    }

    @Test
    void loadWorksWithoutEnvironmentSet() throws Exception {
        Files.writeString(tempDir.resolve("application.properties"), "key=base-value\n");

        // Ensure no env is set via system property
        System.clearProperty("dqs.env");
        Properties props = ConfigLoader.load(List.of(tempDir));

        assertEquals("base-value", props.getProperty("key"));
    }

    // -----------------------------------------------------------------------
    // Placeholder resolution
    // -----------------------------------------------------------------------

    @Test
    void resolvePlaceholderWithDefault() {
        String result = ConfigLoader.resolvePlaceholders(
                "jdbc:postgresql://${DB_HOST_UNLIKELY_SET:localhost}:5432/dqs");
        assertEquals("jdbc:postgresql://localhost:5432/dqs", result);
    }

    @Test
    void resolvePlaceholderFromSystemProperty() {
        System.setProperty("test.config.loader.var", "from-sysprop");
        try {
            String result = ConfigLoader.resolvePlaceholders("${test.config.loader.var:fallback}");
            assertEquals("from-sysprop", result);
        } finally {
            System.clearProperty("test.config.loader.var");
        }
    }

    @Test
    void resolvePlaceholderFromEnvVar() {
        // PATH is virtually always set in any environment
        String result = ConfigLoader.resolvePlaceholders("path=${PATH}");
        String expected = "path=" + System.getenv("PATH");
        assertEquals(expected, result);
    }

    @Test
    void unresolvedPlaceholderWithoutDefaultPreservesRaw() {
        String raw = "${TOTALLY_UNDEFINED_VAR_XYZ_999}";
        String result = ConfigLoader.resolvePlaceholders(raw);
        assertEquals(raw, result);
    }

    @Test
    void multiplePlaceholdersInOneValue() {
        System.setProperty("test.host", "db-host");
        System.setProperty("test.port", "5433");
        try {
            String result = ConfigLoader.resolvePlaceholders(
                    "jdbc:postgresql://${test.host}:${test.port}/dqs");
            assertEquals("jdbc:postgresql://db-host:5433/dqs", result);
        } finally {
            System.clearProperty("test.host");
            System.clearProperty("test.port");
        }
    }

    @Test
    void noPlaceholderReturnsValueUnchanged() {
        assertEquals("plain-value", ConfigLoader.resolvePlaceholders("plain-value"));
    }

    @Test
    void emptyDefaultResolvesToEmpty() {
        String result = ConfigLoader.resolvePlaceholders("${TOTALLY_UNDEFINED_VAR_XYZ_999:}");
        assertEquals("", result);
    }

    @Test
    void resolvePlaceholdersInPropertiesObject() throws Exception {
        Files.writeString(tempDir.resolve("application.properties"),
                "db.host=${TEST_CFG_HOST:localhost}\ndb.url=jdbc:postgresql://${TEST_CFG_HOST:localhost}:5432/dqs\n");

        System.clearProperty("dqs.env");
        Properties props = ConfigLoader.load(List.of(tempDir));

        assertEquals("localhost", props.getProperty("db.host"));
        assertEquals("jdbc:postgresql://localhost:5432/dqs", props.getProperty("db.url"));
    }

    // -----------------------------------------------------------------------
    // resolveEnv
    // -----------------------------------------------------------------------

    @Test
    void resolveEnvPrefersSystemPropertyOverEnvVar() {
        System.setProperty("dqs.env", "staging");
        try {
            assertEquals("staging", ConfigLoader.resolveEnv());
        } finally {
            System.clearProperty("dqs.env");
        }
    }
}
