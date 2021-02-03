package com.capitalone.dashboard.config;

import com.capitalone.dashboard.client.RestOperationsSupplier;
import java.util.HashMap;
import java.util.Map;

import com.capitalone.dashboard.testutil.TestResponse;
import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.scheduling.TaskScheduler;

@ComponentScan(
        basePackages = {"com.capitalone.dashboard.collector", "com.capitalone.dashboard.model"},
        includeFilters = {@Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                value = {TaskScheduler.class}
        ), @Filter(
                type = FilterType.REGEX,
                pattern = {"com.capitalone.dashboard.collector.*CollectorTask.class"}
        ), @Filter(
                type = FilterType.REGEX,
                pattern = {"com.capitalone.dashboard.collector.*Settings.class"}
        )}
)
public class TestRestConfig {
    public TestRestConfig() {
    }

    @Bean
    public RestOperationsSupplier restOperationsSupplier() {
        Map<TestRestKey, TestResponse> responseMap = new HashMap();
        return new CollectorTestRestOperations(responseMap);
    }

    @Bean
    public TaskScheduler taskScheduler() {
        return (TaskScheduler)Mockito.mock(TaskScheduler.class);
    }
}
