package com.bank.dqs;

import com.bank.dqs.config.ConfigLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Integration-level tests verifying DqsJob's config loading via ConfigLoader.
 */
class DqsJobConfigLoadingTest {

    @TempDir
    Path tempDir;

    @Test
    void loadResolvesPropertiesFromFilesystemCandidate() throws Exception {
        Files.writeString(tempDir.resolve("application.properties"), """
                db.url=jdbc:postgresql://db-host:5432/dqs
                db.user=test_user
                db.password=test_password
                """);

        System.clearProperty("dqs.env");
        Properties props = ConfigLoader.load(List.of(tempDir));

        assertEquals("jdbc:postgresql://db-host:5432/dqs", props.getProperty("db.url"));
        assertEquals("test_user", props.getProperty("db.user"));
        assertEquals("test_password", props.getProperty("db.password"));
    }

    @Test
    void loadResolvesPlaceholdersWithDefaults() throws Exception {
        Files.writeString(tempDir.resolve("application.properties"), """
                db.url=${DB_URL:jdbc:postgresql://localhost:5432/postgres}
                db.user=${DB_USER:postgres}
                """);

        System.clearProperty("dqs.env");
        Properties props = ConfigLoader.load(List.of(tempDir));

        assertEquals("jdbc:postgresql://localhost:5432/postgres", props.getProperty("db.url"));
        assertEquals("postgres", props.getProperty("db.user"));
    }

    @Test
    void loadWithEnvOverlayOverridesBaseProperties() throws Exception {
        Files.writeString(tempDir.resolve("application.properties"), """
                db.url=jdbc:postgresql://localhost:5432/postgres
                db.user=postgres
                db.password=localdev
                """);
        Files.writeString(tempDir.resolve("application-uat.properties"), """
                db.url=jdbc:postgresql://uat-host:5432/dqs
                db.user=dqs_uat
                """);

        System.setProperty("dqs.env", "uat");
        try {
            Properties props = ConfigLoader.load(List.of(tempDir));

            assertEquals("jdbc:postgresql://uat-host:5432/dqs", props.getProperty("db.url"));
            assertEquals("dqs_uat", props.getProperty("db.user"));
            assertEquals("localdev", props.getProperty("db.password"),
                    "Non-overridden keys should retain base value");
        } finally {
            System.clearProperty("dqs.env");
        }
    }
}
