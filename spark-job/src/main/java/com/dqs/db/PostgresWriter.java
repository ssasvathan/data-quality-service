package com.dqs.db;

import java.util.Properties;

public class PostgresWriter {
    private final String jdbcUrl;
    private final String user;
    private final String pass;

    public PostgresWriter(String host, int port, String db, String user, String pass) {
        if (host == null || host.isEmpty()) throw new IllegalArgumentException("host must not be empty");
        if (db == null || db.isEmpty()) throw new IllegalArgumentException("db must not be empty");
        if (port <= 0 || port > 65535)
            throw new IllegalArgumentException("port must be 1-65535, got: " + port);
        this.jdbcUrl = "jdbc:postgresql://" + host + ":" + port + "/" + db;
        this.user = user;
        this.pass = pass;
    }

    public String getJdbcUrl() { return jdbcUrl; }

    /** Returns a fresh mutable Properties instance on each call — callers may mutate it freely. */
    public Properties connectionProperties() {
        Properties props = new Properties();
        props.setProperty("user", user);
        props.setProperty("password", pass);
        props.setProperty("driver", "org.postgresql.Driver");
        return props;
    }
}
