package com.bank.dqs.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MetricModelTest {

    // ---------------------------------------------------------------------------
    // AC2: MetricNumeric maps to dq_metric_numeric(check_type, metric_name, metric_value)
    // ---------------------------------------------------------------------------

    @Test
    void metricNumericImplementsDqMetric() {
        MetricNumeric metric = new MetricNumeric("FRESHNESS", "hours_since_update", 72.0);
        assertInstanceOf(DqMetric.class, metric);
    }

    @Test
    void metricNumericGettersReturnConstructedValues() {
        MetricNumeric metric = new MetricNumeric("VOLUME", "row_count", 105000.0);

        assertEquals("VOLUME", metric.getCheckType());
        assertEquals("row_count", metric.getMetricName());
        assertEquals(105000.0, metric.getMetricValue(), 0.001);
    }

    @Test
    void metricNumericSupportsZeroValue() {
        MetricNumeric metric = new MetricNumeric("VOLUME", "row_count", 0.0);
        assertEquals(0.0, metric.getMetricValue(), 0.0);
    }

    @Test
    void metricNumericSupportsFractionalValue() {
        MetricNumeric metric = new MetricNumeric("OPS", "null_rate", 0.87);
        assertEquals(0.87, metric.getMetricValue(), 0.0001);
    }

    // ---------------------------------------------------------------------------
    // AC3: MetricDetail maps to dq_metric_detail(check_type, detail_type, detail_value)
    // ---------------------------------------------------------------------------

    @Test
    void metricDetailImplementsDqMetric() {
        MetricDetail detail = new MetricDetail("SCHEMA", "missing_columns", "[\"amount\",\"id\"]");
        assertInstanceOf(DqMetric.class, detail);
    }

    @Test
    void metricDetailGettersReturnConstructedValues() {
        MetricDetail detail = new MetricDetail(
                "SCHEMA",
                "schema_diff",
                "{\"added\":[\"new_col\"],\"removed\":[\"old_col\"]}"
        );

        assertEquals("SCHEMA", detail.getCheckType());
        assertEquals("schema_diff", detail.getDetailType());
        assertEquals("{\"added\":[\"new_col\"],\"removed\":[\"old_col\"]}", detail.getDetailValue());
    }

    @Test
    void metricDetailAcceptsJsonStringValue() {
        // detailValue is a JSON string → stored as JSONB in Postgres
        MetricDetail detail = new MetricDetail("OPS", "high_null_columns",
                "[\"customer_id\",\"email\"]");
        assertEquals("[\"customer_id\",\"email\"]", detail.getDetailValue());
    }

    @Test
    void metricDetailAcceptsNullDetailValue() {
        // Null detail value is valid (some details may be empty)
        MetricDetail detail = new MetricDetail("FRESHNESS", "error_info", null);
        assertEquals("FRESHNESS", detail.getCheckType());
        assertEquals("error_info", detail.getDetailType());
    }

    // ---------------------------------------------------------------------------
    // Constructor validation — MetricNumeric
    // ---------------------------------------------------------------------------

    @Test
    void metricNumericThrowsOnNullCheckType() {
        assertThrows(IllegalArgumentException.class, () ->
                new MetricNumeric(null, "row_count", 100.0));
    }

    @Test
    void metricNumericThrowsOnBlankCheckType() {
        assertThrows(IllegalArgumentException.class, () ->
                new MetricNumeric("  ", "row_count", 100.0));
    }

    @Test
    void metricNumericThrowsOnNullMetricName() {
        assertThrows(IllegalArgumentException.class, () ->
                new MetricNumeric("VOLUME", null, 100.0));
    }

    @Test
    void metricNumericThrowsOnBlankMetricName() {
        assertThrows(IllegalArgumentException.class, () ->
                new MetricNumeric("VOLUME", "  ", 100.0));
    }

    // ---------------------------------------------------------------------------
    // Constructor validation — MetricDetail
    // ---------------------------------------------------------------------------

    @Test
    void metricDetailThrowsOnNullCheckType() {
        assertThrows(IllegalArgumentException.class, () ->
                new MetricDetail(null, "schema_diff", "{}"));
    }

    @Test
    void metricDetailThrowsOnBlankCheckType() {
        assertThrows(IllegalArgumentException.class, () ->
                new MetricDetail("  ", "schema_diff", "{}"));
    }

    @Test
    void metricDetailThrowsOnNullDetailType() {
        assertThrows(IllegalArgumentException.class, () ->
                new MetricDetail("SCHEMA", null, "{}"));
    }

    @Test
    void metricDetailThrowsOnBlankDetailType() {
        assertThrows(IllegalArgumentException.class, () ->
                new MetricDetail("SCHEMA", "  ", "{}"));
    }

    // ---------------------------------------------------------------------------
    // toString — MetricNumeric & MetricDetail
    // ---------------------------------------------------------------------------

    @Test
    void metricNumericToStringContainsAllFields() {
        MetricNumeric metric = new MetricNumeric("VOLUME", "row_count", 42.0);
        String str = metric.toString();
        assertEquals(true, str.contains("VOLUME"));
        assertEquals(true, str.contains("row_count"));
        assertEquals(true, str.contains("42.0"));
    }

    @Test
    void metricDetailToStringContainsAllFields() {
        MetricDetail detail = new MetricDetail("SCHEMA", "missing_columns", "[\"id\"]");
        String str = detail.toString();
        assertEquals(true, str.contains("SCHEMA"));
        assertEquals(true, str.contains("missing_columns"));
        assertEquals(true, str.contains("[\"id\"]"));
    }
}
