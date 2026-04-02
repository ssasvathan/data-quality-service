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
