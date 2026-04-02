package com.dqs.checks;

import com.dqs.db.CheckResult;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructType;
import org.junit.jupiter.api.*;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class CheckExecutorTest {
    private static SparkSession spark;

    @BeforeAll
    static void setUpSpark() {
        spark = SparkSession.builder()
                .master("local[*]")
                .config("spark.sql.session.timeZone", "UTC")
                .appName("CheckExecutorTest")
                .getOrCreate();
    }

    @AfterAll
    static void tearDownSpark() {
        spark.stop();
    }

    private Dataset<Row> dfWithRows(int count) {
        List<Row> rows = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) rows.add(RowFactory.create(String.valueOf(i), "2026-03-23T23:50:00Z"));
        StructType schema = new StructType()
            .add("value", DataTypes.StringType)
            .add("source_event_timestamp", DataTypes.StringType);
        return spark.createDataFrame(rows, schema);
    }

    @Test
    void volumeCheckFlagsAnomaliesOutsideStdDev() {
        List<Double> history = List.of(100.0, 105.0, 95.0); 
        CheckExecutor.Results res = new CheckExecutor().execute(dfWithRows(50), "parquet", history, null, null);
        
        CheckResult volCheck = res.getCheckResults().stream().filter(r -> "volume".equals(r.getCheckType())).findFirst().get();
        assertEquals("FAILED", volCheck.getStatus());
    }
    
    @Test
    void schemaCheckPassesMatchingFingerprint() {
        Dataset<Row> df = dfWithRows(1);
        double hash = (double) Math.abs(df.schema().json().hashCode());
        CheckExecutor.Results res = new CheckExecutor().execute(df, "parquet", Collections.emptyList(), hash, null);
        
        CheckResult schemaCheck = res.getCheckResults().stream().filter(r -> "schema".equals(r.getCheckType())).findFirst().get();
        assertEquals("PASSED", schemaCheck.getStatus());
    }
    
    @Test
    void freshnessCheckFlagsEarlyStoppage() {
        Dataset<Row> df = dfWithRows(5); // max timestamp is 23:50 -> 1430 mins
        List<Double> history = List.of(1440.0, 1440.0);
        CheckExecutor.Results passRes = new CheckExecutor().execute(df, "parquet", null, null, history);
        assertEquals("PASSED", passRes.getCheckResults().stream().filter(r -> "freshness".equals(r.getCheckType())).findFirst().get().getStatus());
        
        List<Row> earlyRows = List.of(RowFactory.create("1", "2026-03-23T22:00:00Z"));
        Dataset<Row> earlyDf = spark.createDataFrame(earlyRows, df.schema());
        
        CheckExecutor.Results failRes = new CheckExecutor().execute(earlyDf, "parquet", null, null, history);
        assertEquals("FAILED", failRes.getCheckResults().stream().filter(r -> "freshness".equals(r.getCheckType())).findFirst().get().getStatus());
    }
}
