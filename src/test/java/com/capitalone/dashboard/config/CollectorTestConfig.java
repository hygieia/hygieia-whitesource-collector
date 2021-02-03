package com.capitalone.dashboard.config;

import com.capitalone.dashboard.client.RestClient;
import com.capitalone.dashboard.client.RestOperationsSupplier;
import com.capitalone.dashboard.settings.WhiteSourceServerSettings;
import com.capitalone.dashboard.settings.WhiteSourceSettings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

@Configuration
public class CollectorTestConfig {
    @Autowired
    RestOperationsSupplier restOperationsSupplier;

    @Bean
    public RestClient restClient() {
        return new RestClient(restOperationsSupplier);
    }

    @Bean
    public WhiteSourceSettings whiteSourceSettings() {
        WhiteSourceSettings whiteSourceSettings = new WhiteSourceSettings();
        whiteSourceSettings.setCron("* * * * * *");
        whiteSourceSettings.setServers(Arrays.asList("https://myserver.com"));
        WhiteSourceServerSettings serverSettings = new WhiteSourceServerSettings();
        serverSettings.setInstanceUrl("https://myserver.com");
        serverSettings.setDeeplink("https://myserver.com/Wss/WSS.html#!project;id=%d");
        serverSettings.setUserKey(TestConstants.USER_KEY);
        serverSettings.setOrgToken(TestConstants.ORG_KEY);
        whiteSourceSettings.setWhiteSourceServerSettings(Arrays.asList(serverSettings));
        return whiteSourceSettings;
    }

}
