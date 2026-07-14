package com.logistics.mcp.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * 工具注册表服务：通过反射扫描应用中所有被 @Tool 注解的方法，构建工具元数据。
 */
@Service
public class ToolRegistryService {

    private final ApplicationContext applicationContext;

    public ToolRegistryService(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * 获取所有已注册工具的元数据列表。
     * @return 工具元数据列表，按工具方法所在类推断分组（composite / domain / data）
     */
    public List<ToolMetadata> listAllTools() {
        List<ToolMetadata> tools = new ArrayList<>();
        
        // 遍历所有 beans，查找有 @Tool 方法的类
        String[] beanNames = applicationContext.getBeanDefinitionNames();
        for (String beanName : beanNames) {
            Object bean = applicationContext.getBean(beanName);
            Class<?> beanClass = bean.getClass();
            
            // 遍历类的所有方法
            for (Method method : beanClass.getDeclaredMethods()) {
                if (method.isAnnotationPresent(Tool.class)) {
                    Tool toolAnnotation = method.getAnnotation(Tool.class);
                    String toolName = method.getName();
                    String description = toolAnnotation.description();
                    String layer = inferLayer(beanClass);
                    
                    tools.add(new ToolMetadata(toolName, layer, description));
                }
            }
        }
        
        return tools;
    }

    /**
     * 按 layer 分组获取工具。
     * @param layer composite / domain / data
     * @return 指定分组内的工具列表
     */
    public List<ToolMetadata> listToolsByLayer(String layer) {
        return listAllTools().stream()
                .filter(t -> t.layer().equals(layer))
                .toList();
    }

    /**
     * 从 bean 类名推断工具分组。
     * @param beanClass bean 类
     * @return composite / domain / data
     */
    private String inferLayer(Class<?> beanClass) {
        String className = beanClass.getSimpleName().toLowerCase();
        
        if (className.contains("composite")) {
            return "composite";
        } else if (className.contains("domain")) {
            return "domain";
        } else {
            return "data";
        }
    }
}
