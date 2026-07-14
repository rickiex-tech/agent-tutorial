package com.logistics.mcp;

import com.logistics.mcp.tools.composite.CustomerServiceTicketTool;
import com.logistics.mcp.tools.data.DataDomainTools;
import com.logistics.mcp.tools.domain.ShipmentDomainTools;
import com.logistics.mcp.tools.domain.TicketDomainTools;
import com.logistics.mcp.tools.domain.UserDomainTools;
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

    public static void main(String[] args) {
        SpringApplication.run(LogisticsMcpServerApplication.class, args);
    }

    /**
     * 将领域工具与业务编排工具注册为 MCP tools。
     * 智能体（MCP client）只会看到这几个高层工具，而非底层 300+ API。
     */
    @Bean
    public ToolCallbackProvider logisticsTools(UserDomainTools userTools,
                                               ShipmentDomainTools shipmentTools,
                                               TicketDomainTools ticketTools,
                                               CustomerServiceTicketTool customerServiceTicketTool,
                                               DataDomainTools dataDomainTools,
                                               ToolExposureProperties toolExposureProperties) {
        List<Object> toolObjects = new ArrayList<>();
        if (toolExposureProperties.businessEnabled()) {
            toolObjects.add(userTools);
            toolObjects.add(shipmentTools);
            toolObjects.add(ticketTools);
            toolObjects.add(customerServiceTicketTool);
        }
        if (toolExposureProperties.dataEnabled()) {
            toolObjects.add(dataDomainTools);
        }
        return MethodToolCallbackProvider.builder()
                .toolObjects(toolObjects.toArray())
                .build();
    }
}
