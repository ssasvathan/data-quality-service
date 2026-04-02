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
