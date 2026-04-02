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
