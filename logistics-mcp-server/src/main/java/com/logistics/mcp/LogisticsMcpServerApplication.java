package com.logistics.mcp;

import com.logistics.mcp.tools.BusinessTool;
import com.logistics.mcp.tools.DataTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.ArrayList;
import java.util.List;

@SpringBootApplication
@EnableConfigurationProperties(ToolExposureProperties.class)
public class LogisticsMcpServerApplication {

    private static final Logger log = LoggerFactory.getLogger(LogisticsMcpServerApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(LogisticsMcpServerApplication.class, args);
    }

    /**
     * 将领域工具与业务编排工具注册为 MCP tools。
     *
     * <p>采用标记接口自动扫描：Spring 容器中所有实现 {@link BusinessTool} 的 Bean
     * 会被自动注入为 {@code List<BusinessTool>}，实现 {@link DataTool} 的 Bean
     * 会被自动注入为 {@code List<DataTool>}。新增工具时只需让类实现对应接口
     * 并标注 {@code @Service} + {@code @Tool}，无需修改此方法。
     *
     * <p>开关逻辑：{@code business-enabled} 控制业务工具暴露，{@code data-enabled}
     * 控制数据工具暴露，至少开启一个。
     */
    @Bean
    public ToolCallbackProvider logisticsTools(List<BusinessTool> businessTools,
                                               List<DataTool> dataTools,
                                               ToolExposureProperties toolExposureProperties) {
        List<Object> toolObjects = new ArrayList<>();
        if (toolExposureProperties.businessEnabled()) {
            log.info("注册业务工具，共 {} 个：{}", businessTools.size(), businessTools);
            toolObjects.addAll(businessTools);
        }
        if (toolExposureProperties.dataEnabled()) {
            log.info("注册数据工具，共 {} 个：{}", dataTools.size(), dataTools);
            toolObjects.addAll(dataTools);
        }
        log.info("MCP 工具注册完成，共 {} 个工具对象", toolObjects.size());
        return MethodToolCallbackProvider.builder()
                .toolObjects(toolObjects.toArray())
                .build();
    }
}
