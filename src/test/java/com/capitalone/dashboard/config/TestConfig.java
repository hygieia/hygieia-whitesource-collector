package com.capitalone.dashboard.config;

import com.capitalone.dashboard.auth.AuthenticationResponseService;
import com.capitalone.dashboard.collector.DefaultWhiteSourceClient;
import com.capitalone.dashboard.service.BuildCommonService;
import com.capitalone.dashboard.service.CollectorService;
import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


/**
 * Spring context configuration for Testing purposes
 */
@Configuration
public class TestConfig {
    @Bean
    public AuthenticationResponseService authenticationResponseService() {
        return Mockito.mock(AuthenticationResponseService.class);
    }

    @Bean
    public CollectorService collectorService() {
        return Mockito.mock(CollectorService.class);
    }

    @Bean
    public BuildCommonService buildCommonService() { return Mockito.mock(BuildCommonService.class); }

    @Bean
    public DefaultWhiteSourceClient defaultWhiteSourceClient() { return Mockito.mock(DefaultWhiteSourceClient.class); }

}
