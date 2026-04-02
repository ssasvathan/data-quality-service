package com.dqs.db;

import org.junit.jupiter.api.Test;
import java.util.Properties;
import static org.junit.jupiter.api.Assertions.*;

public class PostgresWriterTest {

    @Test
    void buildsCorrectJdbcUrlAndConnectionProperties() {
        PostgresWriter writer = new PostgresWriter("localhost", 5433, "postgres", "postgres", "localdev");
        Properties props = writer.connectionProperties();

        assertEquals("jdbc:postgresql://localhost:5433/postgres", writer.getJdbcUrl());
        assertEquals("postgres", props.getProperty("user"));
        assertEquals("localdev", props.getProperty("password"));
        assertEquals("org.postgresql.Driver", props.getProperty("driver"));
    }

    @Test
    void rejectsInvalidConstructorArguments() {
        assertThrows(IllegalArgumentException.class,
                () -> new PostgresWriter("", 5433, "postgres", "postgres", "pw"));
        assertThrows(IllegalArgumentException.class,
                () -> new PostgresWriter("localhost", 0, "postgres", "postgres", "pw"));
        assertThrows(IllegalArgumentException.class,
                () -> new PostgresWriter("localhost", 5433, "", "postgres", "pw"));
    }
}
