package com.dqs.checks;

import com.dqs.config.CheckConfig;
import com.dqs.config.RuleConfig;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructType;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class CheckExecutorTest {

    private SparkSession spark;

    @BeforeEach
    void setUpSpark() {
        spark = SparkSession.builder()
                .master("local[*]")
                .appName("CheckExecutorTest")
                .config("spark.sql.shuffle.partitions", "1")
                .getOrCreate();
    }

    @AfterEach
    void tearDownSpark() {
        if (spark != null) spark.stop();
    }

    private Dataset<Row> dfWithRows(int count) {
        List<Row> rows = new ArrayList<>();
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
