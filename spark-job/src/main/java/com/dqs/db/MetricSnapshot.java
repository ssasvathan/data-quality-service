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
