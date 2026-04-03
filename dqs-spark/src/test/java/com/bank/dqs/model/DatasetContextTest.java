package com.bank.dqs.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DatasetContextTest {

    // ---------------------------------------------------------------------------
    // AC6: Constructor validation
    // ---------------------------------------------------------------------------

    @Test
    void constructorThrowsOnNullDatasetName() {
        assertThrows(IllegalArgumentException.class, () ->
                new DatasetContext(null, "LOB", LocalDate.now(), "/path", null,
                        DatasetContext.FORMAT_AVRO));
    }

    @Test
    void constructorThrowsOnBlankDatasetName() {
        assertThrows(IllegalArgumentException.class, () ->
                new DatasetContext("   ", "LOB", LocalDate.now(), "/path", null,
                        DatasetContext.FORMAT_AVRO));
    }

    @Test
    void constructorThrowsOnEmptyDatasetName() {
        assertThrows(IllegalArgumentException.class, () ->
                new DatasetContext("", "LOB", LocalDate.now(), "/path", null,
                        DatasetContext.FORMAT_PARQUET));
    }

    @Test
    void constructorThrowsOnNullPartitionDate() {
        assertThrows(IllegalArgumentException.class, () ->
                new DatasetContext("ds", "LOB", null, "/path", null,
                        DatasetContext.FORMAT_PARQUET));
    }

    @Test
    void constructorThrowsOnNullParentPath() {
        assertThrows(IllegalArgumentException.class, () ->
                new DatasetContext("ds", "LOB", LocalDate.now(), null, null,
                        DatasetContext.FORMAT_PARQUET));
    }

    @Test
    void constructorThrowsOnBlankParentPath() {
        assertThrows(IllegalArgumentException.class, () ->
                new DatasetContext("ds", "LOB", LocalDate.now(), "  ", null,
                        DatasetContext.FORMAT_PARQUET));
    }

    @Test
    void constructorThrowsOnNullFormat() {
        assertThrows(IllegalArgumentException.class, () ->
                new DatasetContext("ds", "LOB", LocalDate.now(), "/path", null, null));
    }

    // ---------------------------------------------------------------------------
    // AC6: df field is nullable (test-friendly — no SparkSession needed)
    // ---------------------------------------------------------------------------

    @Test
    void constructorAllowsNullDf() {
        assertDoesNotThrow(() ->
                new DatasetContext("ds", "LOB", LocalDate.now(), "/path", null,
                        DatasetContext.FORMAT_PARQUET));
    }

    // ---------------------------------------------------------------------------
    // AC6: lookupCode is nullable (some datasets may not have enrichment)
    // ---------------------------------------------------------------------------

    @Test
    void constructorAllowsNullLookupCode() {
        assertDoesNotThrow(() ->
                new DatasetContext("ds", null, LocalDate.now(), "/path", null,
                        DatasetContext.FORMAT_PARQUET));
    }

    // ---------------------------------------------------------------------------
    // AC6: All fields accessible via getters
    // ---------------------------------------------------------------------------

    @Test
    void gettersReturnConstructedValues() {
        LocalDate date = LocalDate.of(2026, 4, 3);
        DatasetContext ctx = new DatasetContext(
                "lob=retail/src_sys_nm=alpha/dataset=sales_daily",
                "LOB001",
                date,
                "/prod/abc/data/consumerzone",
                null,
                DatasetContext.FORMAT_PARQUET
        );

        assertEquals("lob=retail/src_sys_nm=alpha/dataset=sales_daily", ctx.getDatasetName());
        assertEquals("LOB001", ctx.getLookupCode());
        assertEquals(date, ctx.getPartitionDate());
        assertEquals("/prod/abc/data/consumerzone", ctx.getParentPath());
        assertNull(ctx.getDf());
        assertEquals(DatasetContext.FORMAT_PARQUET, ctx.getFormat());
    }

    @Test
    void gettersReturnLegacyPathValues() {
        DatasetContext ctx = new DatasetContext(
                "src_sys_nm=omni/dataset=customer_profile",
                "LOB_LEGACY",
                LocalDate.of(2026, 3, 30),
                "/prod/abc/data/consumerzone",
                null,
                DatasetContext.FORMAT_AVRO
        );

        assertEquals("src_sys_nm=omni/dataset=customer_profile", ctx.getDatasetName());
        assertEquals("LOB_LEGACY", ctx.getLookupCode());
        assertEquals(DatasetContext.FORMAT_AVRO, ctx.getFormat());
    }

    // ---------------------------------------------------------------------------
    // AC6: Format constants are defined
    // ---------------------------------------------------------------------------

    @Test
    void formatConstantsAreDefined() {
        assertEquals("AVRO", DatasetContext.FORMAT_AVRO);
        assertEquals("PARQUET", DatasetContext.FORMAT_PARQUET);
        assertEquals("UNKNOWN", DatasetContext.FORMAT_UNKNOWN);
    }

    // ---------------------------------------------------------------------------
    // toString
    // ---------------------------------------------------------------------------

    @Test
    void toStringContainsKeyFields() {
        DatasetContext ctx = new DatasetContext(
                "lob=retail/src_sys_nm=alpha/dataset=sales_daily",
                "LOB001",
                LocalDate.of(2026, 4, 3),
                "/prod/data",
                null,
                DatasetContext.FORMAT_PARQUET
        );
        String str = ctx.toString();
        assertEquals(true, str.contains("sales_daily"));
        assertEquals(true, str.contains("LOB001"));
        assertEquals(true, str.contains("PARQUET"));
    }
}
