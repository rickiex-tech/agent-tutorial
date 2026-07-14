package com.logistics.mcp;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "logistics.mcp.tools")
public record ToolExposureProperties(boolean businessEnabled, boolean dataEnabled) {

    public ToolExposureProperties {
        if (!businessEnabled && !dataEnabled) {
            throw new IllegalArgumentException("At least one tool group must be enabled");
        }
    }
}
