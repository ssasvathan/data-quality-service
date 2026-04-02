# DQS Spark Job (Java/Maven) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the DQS Spark application in Java (Maven) that reads partitioned Parquet data, evaluates data quality checks from a JSON config, and logs results to the shared Postgres database.

**Architecture:** A Maven-based Java project. `DqsJob` is the entry point — it parses CLI args, reads a JSON rule config, creates a `SparkSession`, reads input Parquet, runs checks via `CheckExecutor`, and writes all results to Postgres via `MetadataRepository` (direct JDBC). `dataset` and `dq_run` records are managed via direct JDBC; `dq_check_result` and `dq_metric_snapshot` rows are batch-inserted the same way.

**Tech Stack:** Java 17, Apache Spark 3.5.0 (`spark-sql_2.13`), PostgreSQL JDBC 42.7.2, Jackson Databind 2.17.0, JUnit 5, H2 (test DB), Maven 3.x

---

### Task 0: Remove Scala/sbt Files

**Files:**
- Delete: `spark-job/` entire directory (all Scala/sbt artefacts)

- [ ] **Step 1: Delete the existing spark-job directory**

```bash
rm -rf /path/to/repo/spark-job
```

Run from repo root: `rm -rf spark-job/`

- [ ] **Step 2: Commit the deletion**

```bash
git rm -r spark-job/
git commit -m "chore: remove scala/sbt spark-job in favour of java/maven"
```

---

### Task 1: Initialize Maven Java Project & Spark Setup

**Files:**
- Create: `spark-job/pom.xml`
- Create: `spark-job/.gitignore`
- Create: `spark-job/src/test/java/com/dqs/SparkSessionTest.java`

- [ ] **Step 1: Write the failing test**

```java
// spark-job/src/test/java/com/dqs/SparkSessionTest.java
package com.dqs;

import org.apache.spark.sql.SparkSession;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SparkSessionTest {

    @Test
    void sparkSessionCanBeInitializedLocally() {
        SparkSession spark = SparkSession.builder()
                .master("local[*]")
                .appName("DqsTest")
                .getOrCreate();
        assertTrue(spark.version().startsWith("3.5"));
        spark.stop();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd spark-job && mvn test`
Expected: FAIL — no `pom.xml` exists yet.

- [ ] **Step 3: Write minimal implementation**

Create `spark-job/.gitignore`:
```
target/
.idea/
*.iml
.bsp/
.metals/
.bloop/
*.class
*.log
.DS_Store
```

Create `spark-job/pom.xml`:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.dqs</groupId>
    <artifactId>dqs-spark-job</artifactId>
    <version>1.0</version>

    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <spark.version>3.5.0</spark.version>
        <jackson.version>2.17.0</jackson.version>
    </properties>

    <dependencies>
        <!-- Spark SQL — provided scope is automatically on the test classpath in Maven -->
        <dependency>
            <groupId>org.apache.spark</groupId>
            <artifactId>spark-sql_2.13</artifactId>
            <version>${spark.version}</version>
            <scope>provided</scope>
        </dependency>

        <!-- PostgreSQL JDBC -->
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <version>42.7.2</version>
        </dependency>

        <!-- Jackson for JSON parsing -->
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
            <version>${jackson.version}</version>
        </dependency>

        <!-- JUnit 5 -->
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>5.10.2</version>
            <scope>test</scope>
        </dependency>

        <!-- H2 in-memory DB for MetadataRepository tests -->
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <version>2.2.224</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.12.1</version>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.2.5</version>
                <configuration>
                    <forkCount>1</forkCount>
                    <reuseForks>false</reuseForks>
                    <!--
                        Use Java 21 for the forked test JVM.
                        System default is Java 25, which removed Subject.getSubject()
                        relied on by Hadoop UGI (used internally by Spark). Java 21 is
                        an officially supported Spark 3.5 target.
                    -->
                    <jvm>/usr/lib/jvm/java-21-openjdk-amd64/bin/java</jvm>
                    <argLine>
                        --add-opens=java.base/java.lang=ALL-UNNAMED
                        --add-opens=java.base/java.lang.invoke=ALL-UNNAMED
                        --add-opens=java.base/java.lang.reflect=ALL-UNNAMED
                        --add-opens=java.base/java.io=ALL-UNNAMED
                        --add-opens=java.base/java.net=ALL-UNNAMED
                        --add-opens=java.base/java.nio=ALL-UNNAMED
                        --add-opens=java.base/java.util=ALL-UNNAMED
                        --add-opens=java.base/java.util.concurrent=ALL-UNNAMED
                        --add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED
                        --add-opens=java.base/sun.nio.ch=ALL-UNNAMED
                        --add-opens=java.base/sun.nio.cs=ALL-UNNAMED
                        --add-opens=java.base/sun.util.calendar=ALL-UNNAMED
                        --add-opens=java.security.jgss/sun.security.krb5=ALL-UNNAMED
                    </argLine>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd spark-job && mvn test`
Expected: PASS. Maven downloads dependencies on the first run (may take several minutes). The test creates a local SparkSession, asserts version starts with "3.5", and stops.

- [ ] **Step 5: Commit**

```bash
git add spark-job/
git commit -m "build: init maven java project for dqs spark job"
```

---

### Task 2: Implement JSON Rule Configuration Loader

**Files:**
- Create: `spark-job/src/main/java/com/dqs/config/CheckConfig.java`
- Create: `spark-job/src/main/java/com/dqs/config/RuleConfig.java`
- Create: `spark-job/src/test/java/com/dqs/config/RuleConfigTest.java`

- [ ] **Step 1: Write the failing test**

```java
// spark-job/src/test/java/com/dqs/config/RuleConfigTest.java
package com.dqs.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class RuleConfigTest {

    @Test
    void parseRuleConfigJson() {
        String json = "{\"dataset\": \"UET0\", \"checks\": [{\"checkType\": \"rowCount\", \"min\": 100}]}";
        RuleConfig config = RuleConfig.fromJson(json);

        assertEquals("UET0", config.getDataset());
        assertEquals(1, config.getChecks().size());
        assertEquals("rowCount", config.getChecks().get(0).getCheckType());
        assertEquals(100, config.getChecks().get(0).getMin());
    }

    @Test
    void parseRuleConfigWithMinAbsent() {
        String json = "{\"dataset\": \"UET0\", \"checks\": [{\"checkType\": \"rowCount\"}]}";
        RuleConfig config = RuleConfig.fromJson(json);

        assertNull(config.getChecks().get(0).getMin());
    }

    @Test
    void parseRuleConfigMalformedJsonThrows() {
        assertThrows(IllegalArgumentException.class, () -> RuleConfig.fromJson("{bad json}"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd spark-job && mvn test -pl . -Dtest=RuleConfigTest`
Expected: FAIL — `RuleConfig` class does not exist.

- [ ] **Step 3: Write minimal implementation**

```java
// spark-job/src/main/java/com/dqs/config/CheckConfig.java
package com.dqs.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CheckConfig {
    private final String checkType;
    private final Integer min;

    @JsonCreator
    public CheckConfig(
            @JsonProperty("checkType") String checkType,
            @JsonProperty("min") Integer min) {
        this.checkType = checkType;
        this.min = min;
    }

    public String getCheckType() { return checkType; }
    public Integer getMin() { return min; }
}
```

```java
// spark-job/src/main/java/com/dqs/config/RuleConfig.java
package com.dqs.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class RuleConfig {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String dataset;
    private final List<CheckConfig> checks;

    @JsonCreator
    public RuleConfig(
            @JsonProperty("dataset") String dataset,
            @JsonProperty("checks") List<CheckConfig> checks) {
        this.dataset = dataset;
        this.checks = checks;
    }

    /** Parses a JSON string into a RuleConfig; throws IllegalArgumentException on malformed input. */
    public static RuleConfig fromJson(String json) {
        try {
            return MAPPER.readValue(json, RuleConfig.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse rule config: " + e.getMessage(), e);
        }
    }

    public String getDataset() { return dataset; }
    public List<CheckConfig> getChecks() { return checks; }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd spark-job && mvn test -Dtest=RuleConfigTest`
Expected: PASS — 3 tests pass.

- [ ] **Step 5: Commit**

```bash
git add spark-job/src/
git commit -m "feat: implement json rule configuration loader"
```

---

### Task 3: Implement JDBC Postgres Writer

**Files:**
- Create: `spark-job/src/main/java/com/dqs/db/PostgresWriter.java`
- Create: `spark-job/src/test/java/com/dqs/db/PostgresWriterTest.java`

- [ ] **Step 1: Write the failing test**

```java
// spark-job/src/test/java/com/dqs/db/PostgresWriterTest.java
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd spark-job && mvn test -Dtest=PostgresWriterTest`
Expected: FAIL — `PostgresWriter` class does not exist.

- [ ] **Step 3: Write minimal implementation**

```java
// spark-job/src/main/java/com/dqs/db/PostgresWriter.java
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
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd spark-job && mvn test -Dtest=PostgresWriterTest`
Expected: PASS — 2 tests pass.

- [ ] **Step 5: Commit**

```bash
git add spark-job/src/
git commit -m "feat: implement jdbc connection helper for spark"
```

---

### Task 4: Implement Metadata Repository

Manages `dataset`, `dq_run`, `dq_check_result`, and `dq_metric_snapshot` records via direct JDBC. Tests use H2 in-memory database in PostgreSQL compatibility mode.

**Files:**
- Create: `spark-job/src/main/java/com/dqs/db/CheckResult.java`
- Create: `spark-job/src/main/java/com/dqs/db/MetricSnapshot.java`
- Create: `spark-job/src/main/java/com/dqs/db/MetadataRepository.java`
- Create: `spark-job/src/test/java/com/dqs/db/MetadataRepositoryTest.java`

- [ ] **Step 1: Write the failing test**

```java
// spark-job/src/test/java/com/dqs/db/MetadataRepositoryTest.java
package com.dqs.db;

import org.junit.jupiter.api.*;
import java.sql.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class MetadataRepositoryTest {

    private Connection conn;
    private MetadataRepository repo;

    @BeforeEach
    void setUp() throws SQLException {
        conn = DriverManager.getConnection(
                "jdbc:h2:mem:dqstest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "sa", "");
        try (Statement st = conn.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS dataset (
                    dataset_id SERIAL PRIMARY KEY,
                    src_sys_nm VARCHAR(255) NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    CONSTRAINT uq_dataset UNIQUE(src_sys_nm)
                )""");
            st.execute("""
                CREATE TABLE IF NOT EXISTS dq_run (
                    run_id SERIAL PRIMARY KEY,
                    dataset_id INTEGER REFERENCES dataset(dataset_id),
                    run_timestamp TIMESTAMP NOT NULL,
                    status VARCHAR(50) NOT NULL,
                    duration_ms BIGINT
                )""");
            st.execute("""
                CREATE TABLE IF NOT EXISTS dq_check_result (
                    check_id SERIAL PRIMARY KEY,
                    run_id INTEGER REFERENCES dq_run(run_id),
                    dataset_id INTEGER REFERENCES dataset(dataset_id),
                    check_type VARCHAR(100) NOT NULL,
                    column_name VARCHAR(255),
                    status VARCHAR(50) NOT NULL,
                    failure_reason TEXT
                )""");
            st.execute("""
                CREATE TABLE IF NOT EXISTS dq_metric_snapshot (
                    metric_id SERIAL PRIMARY KEY,
                    run_id INTEGER REFERENCES dq_run(run_id),
                    dataset_id INTEGER REFERENCES dataset(dataset_id),
                    metric_name VARCHAR(100) NOT NULL,
                    column_name VARCHAR(255),
                    metric_value NUMERIC NOT NULL
                )""");
        }
        repo = new MetadataRepository(conn);
    }

    @AfterEach
    void tearDown() throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("DROP ALL OBJECTS");
        }
        conn.close();
    }

    @Test
    void upsertDatasetCreatesNewRecord() throws SQLException {
        int id = repo.upsertDataset("MY_SYS");
        assertTrue(id > 0);
    }

    @Test
    void upsertDatasetIsIdempotent() throws SQLException {
        int id1 = repo.upsertDataset("MY_SYS");
        int id2 = repo.upsertDataset("MY_SYS");
        assertEquals(id1, id2);
    }

    @Test
    void openAndCloseRun() throws SQLException {
        int datasetId = repo.upsertDataset("MY_SYS");
        int runId = repo.openRun(datasetId);
        assertTrue(runId > 0);

        repo.closeRun(runId, "PASSED", 1234L);

        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT status, duration_ms FROM dq_run WHERE run_id=?")) {
            ps.setInt(1, runId);
            ResultSet rs = ps.executeQuery();
            assertTrue(rs.next());
            assertEquals("PASSED", rs.getString("status"));
            assertEquals(1234L, rs.getLong("duration_ms"));
        }
    }

    @Test
    void insertCheckResults() throws SQLException {
        int datasetId = repo.upsertDataset("MY_SYS");
        int runId = repo.openRun(datasetId);

        List<CheckResult> results = List.of(
            new CheckResult("rowCount", null, "PASSED", null),
            new CheckResult("rowCount", "amount", "FAILED", "Row count 5 is below minimum 10")
        );
        repo.insertCheckResults(runId, datasetId, results);

        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM dq_check_result WHERE run_id=?")) {
            ps.setInt(1, runId);
            ResultSet rs = ps.executeQuery();
            rs.next();
            assertEquals(2, rs.getInt(1));
        }
    }

    @Test
    void insertMetrics() throws SQLException {
        int datasetId = repo.upsertDataset("MY_SYS");
        int runId = repo.openRun(datasetId);

        List<MetricSnapshot> metrics = List.of(
            new MetricSnapshot("rowCount", null, 42.0)
        );
        repo.insertMetrics(runId, datasetId, metrics);

        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT metric_value FROM dq_metric_snapshot WHERE run_id=?")) {
            ps.setInt(1, runId);
            ResultSet rs = ps.executeQuery();
            assertTrue(rs.next());
            assertEquals(42.0, rs.getDouble("metric_value"), 0.001);
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd spark-job && mvn test -Dtest=MetadataRepositoryTest`
Expected: FAIL — classes do not exist yet.

- [ ] **Step 3: Write minimal implementation**

```java
// spark-job/src/main/java/com/dqs/db/CheckResult.java
package com.dqs.db;

public class CheckResult {
    private final String checkType;
    private final String columnName;    // nullable
    private final String status;        // "PASSED" or "FAILED"
    private final String failureReason; // nullable

    public CheckResult(String checkType, String columnName, String status, String failureReason) {
        this.checkType = checkType;
        this.columnName = columnName;
        this.status = status;
        this.failureReason = failureReason;
    }

    public String getCheckType() { return checkType; }
    public String getColumnName() { return columnName; }
    public String getStatus() { return status; }
    public String getFailureReason() { return failureReason; }
}
```

```java
// spark-job/src/main/java/com/dqs/db/MetricSnapshot.java
package com.dqs.db;

public class MetricSnapshot {
    private final String metricName;
    private final String columnName; // nullable
    private final double metricValue;

    public MetricSnapshot(String metricName, String columnName, double metricValue) {
        this.metricName = metricName;
        this.columnName = columnName;
        this.metricValue = metricValue;
    }

    public String getMetricName() { return metricName; }
    public String getColumnName() { return columnName; }
    public double getMetricValue() { return metricValue; }
}
```

```java
// spark-job/src/main/java/com/dqs/db/MetadataRepository.java
package com.dqs.db;

import java.sql.*;
import java.util.List;

public class MetadataRepository implements AutoCloseable {
    private final Connection conn;

    public MetadataRepository(Connection conn) {
        this.conn = conn;
    }

    /**
     * Inserts a dataset row if src_sys_nm is new; returns the existing dataset_id if it already exists.
     */
    public int upsertDataset(String srcSysNm) throws SQLException {
        String sql = "INSERT INTO dataset(src_sys_nm) VALUES(?)" +
                     " ON CONFLICT(src_sys_nm) DO UPDATE SET src_sys_nm=EXCLUDED.src_sys_nm" +
                     " RETURNING dataset_id";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, srcSysNm);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt("dataset_id");
            }
        }
    }

    /** Opens a new dq_run record with status RUNNING and returns its run_id. */
    public int openRun(int datasetId) throws SQLException {
        String sql = "INSERT INTO dq_run(dataset_id, run_timestamp, status) VALUES(?, NOW(), 'RUNNING')" +
                     " RETURNING run_id";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, datasetId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt("run_id");
            }
        }
    }

    /** Closes a dq_run by updating its status and duration. */
    public void closeRun(int runId, String status, long durationMs) throws SQLException {
        String sql = "UPDATE dq_run SET status=?, duration_ms=? WHERE run_id=?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setLong(2, durationMs);
            ps.setInt(3, runId);
            ps.executeUpdate();
        }
    }

    /** Batch-inserts check results for a run. */
    public void insertCheckResults(int runId, int datasetId, List<CheckResult> results) throws SQLException {
        String sql = "INSERT INTO dq_check_result(run_id, dataset_id, check_type, column_name, status, failure_reason)" +
                     " VALUES(?,?,?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (CheckResult r : results) {
                ps.setInt(1, runId);
                ps.setInt(2, datasetId);
                ps.setString(3, r.getCheckType());
                ps.setString(4, r.getColumnName());
                ps.setString(5, r.getStatus());
                ps.setString(6, r.getFailureReason());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /** Batch-inserts metric snapshots for a run. */
    public void insertMetrics(int runId, int datasetId, List<MetricSnapshot> metrics) throws SQLException {
        String sql = "INSERT INTO dq_metric_snapshot(run_id, dataset_id, metric_name, column_name, metric_value)" +
                     " VALUES(?,?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (MetricSnapshot m : metrics) {
                ps.setInt(1, runId);
                ps.setInt(2, datasetId);
                ps.setString(3, m.getMetricName());
                ps.setString(4, m.getColumnName());
                ps.setDouble(5, m.getMetricValue());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    @Override
    public void close() throws Exception {
        conn.close();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd spark-job && mvn test -Dtest=MetadataRepositoryTest`
Expected: PASS — 5 tests pass.

- [ ] **Step 5: Commit**

```bash
git add spark-job/src/
git commit -m "feat: implement metadata repository with direct jdbc"
```

---

### Task 5: Implement Check Executor

Evaluates data quality checks against a Spark `Dataset<Row>`, returning `CheckResult` and `MetricSnapshot` lists. Supports `rowCount` check type.

**Files:**
- Create: `spark-job/src/main/java/com/dqs/checks/CheckExecutor.java`
- Create: `spark-job/src/test/java/com/dqs/checks/CheckExecutorTest.java`

- [ ] **Step 1: Write the failing test**

```java
// spark-job/src/test/java/com/dqs/checks/CheckExecutorTest.java
package com.dqs.checks;

import com.dqs.config.CheckConfig;
import com.dqs.config.RuleConfig;
import com.dqs.db.CheckResult;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructType;
import org.junit.jupiter.api.*;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class CheckExecutorTest {

    private static SparkSession spark;

    @BeforeAll
    static void setUpSpark() {
        spark = SparkSession.builder()
                .master("local[*]")
                .appName("CheckExecutorTest")
                .getOrCreate();
    }

    @AfterAll
    static void tearDownSpark() {
        spark.stop();
    }

    private Dataset<Row> dfWithRows(int count) {
        List<Row> rows = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) rows.add(RowFactory.create("row" + i));
        StructType schema = new StructType().add("value", DataTypes.StringType);
        return spark.createDataFrame(rows, schema);
    }

    @Test
    void rowCountPassesWhenCountMeetsMinimum() {
        RuleConfig config = new RuleConfig("DS", List.of(new CheckConfig("rowCount", 3)));
        CheckExecutor.Results results = new CheckExecutor().execute(dfWithRows(5), config);

        assertEquals(1, results.getCheckResults().size());
        assertEquals("PASSED", results.getCheckResults().get(0).getStatus());
        assertEquals(1, results.getMetrics().size());
        assertEquals(5.0, results.getMetrics().get(0).getMetricValue(), 0.001);
        assertFalse(results.hasFailed());
    }

    @Test
    void rowCountFailsWhenCountBelowMinimum() {
        RuleConfig config = new RuleConfig("DS", List.of(new CheckConfig("rowCount", 10)));
        CheckExecutor.Results results = new CheckExecutor().execute(dfWithRows(2), config);

        assertEquals("FAILED", results.getCheckResults().get(0).getStatus());
        assertTrue(results.hasFailed());
        assertNotNull(results.getCheckResults().get(0).getFailureReason());
    }

    @Test
    void rowCountPassesWhenMinIsNull() {
        RuleConfig config = new RuleConfig("DS", List.of(new CheckConfig("rowCount", null)));
        CheckExecutor.Results results = new CheckExecutor().execute(dfWithRows(0), config);

        assertEquals("PASSED", results.getCheckResults().get(0).getStatus());
    }

    @Test
    void unknownCheckTypeProducesFailedResult() {
        RuleConfig config = new RuleConfig("DS", List.of(new CheckConfig("nonExistentCheck", null)));
        CheckExecutor.Results results = new CheckExecutor().execute(dfWithRows(5), config);

        assertEquals("FAILED", results.getCheckResults().get(0).getStatus());
        assertTrue(results.getCheckResults().get(0).getFailureReason().contains("Unknown check type"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd spark-job && mvn test -Dtest=CheckExecutorTest`
Expected: FAIL — `CheckExecutor` class does not exist.

- [ ] **Step 3: Write minimal implementation**

```java
// spark-job/src/main/java/com/dqs/checks/CheckExecutor.java
package com.dqs.checks;

import com.dqs.config.CheckConfig;
import com.dqs.config.RuleConfig;
import com.dqs.db.CheckResult;
import com.dqs.db.MetricSnapshot;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import java.util.ArrayList;
import java.util.List;

public class CheckExecutor {

    /** Runs all checks defined in config against df and returns results + metrics. */
    public Results execute(Dataset<Row> df, RuleConfig config) {
        List<CheckResult> checkResults = new ArrayList<>();
        List<MetricSnapshot> metrics = new ArrayList<>();

        for (CheckConfig check : config.getChecks()) {
            if ("rowCount".equals(check.getCheckType())) {
                long count = df.count();
                metrics.add(new MetricSnapshot("rowCount", null, (double) count));

                boolean passed = check.getMin() == null || count >= check.getMin();
                String reason = passed ? null
                        : String.format("Row count %d is below minimum %d", count, check.getMin());
                checkResults.add(new CheckResult("rowCount", null, passed ? "PASSED" : "FAILED", reason));
            } else {
                checkResults.add(new CheckResult(
                        check.getCheckType(), null, "FAILED",
                        "Unknown check type: " + check.getCheckType()));
            }
        }

        return new Results(checkResults, metrics);
    }

    public static class Results {
        private final List<CheckResult> checkResults;
        private final List<MetricSnapshot> metrics;

        public Results(List<CheckResult> checkResults, List<MetricSnapshot> metrics) {
            this.checkResults = checkResults;
            this.metrics = metrics;
        }

        public List<CheckResult> getCheckResults() { return checkResults; }
        public List<MetricSnapshot> getMetrics() { return metrics; }

        public boolean hasFailed() {
            return checkResults.stream().anyMatch(r -> "FAILED".equals(r.getStatus()));
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd spark-job && mvn test -Dtest=CheckExecutorTest`
Expected: PASS — 4 tests pass.

- [ ] **Step 5: Commit**

```bash
git add spark-job/src/
git commit -m "feat: implement check executor for spark dataframe checks"
```

---

### Task 6: Implement DqsJob Main Entry Point

Wires together `RuleConfig`, `PostgresWriter`, `MetadataRepository`, `SparkSession`, and `CheckExecutor` into a runnable Spark job.

**Files:**
- Create: `spark-job/src/main/java/com/dqs/DqsJob.java`
- Create: `spark-job/src/test/java/com/dqs/DqsJobArgsTest.java`

- [ ] **Step 1: Write the failing test**

```java
// spark-job/src/test/java/com/dqs/DqsJobArgsTest.java
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd spark-job && mvn test -Dtest=DqsJobArgsTest`
Expected: FAIL — `DqsJob` class does not exist.

- [ ] **Step 3: Write minimal implementation**

```java
// spark-job/src/main/java/com/dqs/DqsJob.java
package com.dqs;

import com.dqs.checks.CheckExecutor;
import com.dqs.config.RuleConfig;
import com.dqs.db.MetadataRepository;
import com.dqs.db.PostgresWriter;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;

public class DqsJob {

    /**
     * Validates that exactly 7 positional args were supplied.
     * Args: config-json-path input-parquet-path db-host db-port db-name db-user db-pass
     */
    static void validateArgs(String[] args) {
        if (args.length != 7) {
            throw new IllegalArgumentException(
                    "Usage: DqsJob <config-json-path> <input-parquet-path> " +
                    "<db-host> <db-port> <db-name> <db-user> <db-pass>");
        }
    }

    public static void main(String[] args) throws Exception {
        validateArgs(args);

        String configPath  = args[0];
        String inputPath   = args[1];
        String dbHost      = args[2];
        int    dbPort      = Integer.parseInt(args[3]);
        String dbName      = args[4];
        String dbUser      = args[5];
        String dbPass      = args[6];

        // Parse rule config
        String configJson = new String(Files.readAllBytes(Paths.get(configPath)));
        RuleConfig config = RuleConfig.fromJson(configJson);

        // Set up DB connection
        PostgresWriter writer = new PostgresWriter(dbHost, dbPort, dbName, dbUser, dbPass);
        Connection conn = DriverManager.getConnection(writer.getJdbcUrl(), writer.connectionProperties());

        try (MetadataRepository repo = new MetadataRepository(conn)) {
            int datasetId = repo.upsertDataset(config.getDataset());
            long startMs = System.currentTimeMillis();
            int runId = repo.openRun(datasetId);

            SparkSession spark = SparkSession.builder().appName("DqsJob").getOrCreate();
            try {
                Dataset<Row> df = spark.read().parquet(inputPath);

                CheckExecutor executor = new CheckExecutor();
                CheckExecutor.Results results = executor.execute(df, config);

                repo.insertCheckResults(runId, datasetId, results.getCheckResults());
                repo.insertMetrics(runId, datasetId, results.getMetrics());

                String status = results.hasFailed() ? "FAILED" : "PASSED";
                repo.closeRun(runId, status, System.currentTimeMillis() - startMs);

            } catch (Exception e) {
                repo.closeRun(runId, "ERROR", System.currentTimeMillis() - startMs);
                throw e;
            } finally {
                spark.stop();
            }
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd spark-job && mvn test -Dtest=DqsJobArgsTest`
Expected: PASS — 3 tests pass.

- [ ] **Step 5: Run all tests to confirm nothing is broken**

Run: `cd spark-job && mvn test`
Expected: All tests pass across all 6 test classes.

- [ ] **Step 6: Commit**

```bash
git add spark-job/src/
git commit -m "feat: implement dqsjob main entry point"
```
