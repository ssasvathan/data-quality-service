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
