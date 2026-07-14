package com.logistics.mcp.contracts;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OpenAPI 契约校验：避免工具定义与下游 API 路径漂移。
 */
class OpenApiContractTest {

    @Test
    void userApiContractContainsExpectedPath() throws Exception {
        String text = Files.readString(Path.of("src/main/resources/openapi/user-api.yaml"));
        assertTrue(text.contains("/api/v1/users/{userId}"));
        assertTrue(text.contains("get:"));
    }

    @Test
    void shipmentApiContractContainsExpectedPath() throws Exception {
        String text = Files.readString(Path.of("src/main/resources/openapi/shipment-api.yaml"));
        assertTrue(text.contains("/api/v1/shipments/{shipmentId}"));
        assertTrue(text.contains("get:"));
    }

    @Test
    void ticketApiContractContainsExpectedPath() throws Exception {
        String text = Files.readString(Path.of("src/main/resources/openapi/ticket-api.yaml"));
        assertTrue(text.contains("/api/v1/tickets"));
        assertTrue(text.contains("post:"));
    }
}
