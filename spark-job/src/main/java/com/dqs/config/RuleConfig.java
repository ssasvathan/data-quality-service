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
