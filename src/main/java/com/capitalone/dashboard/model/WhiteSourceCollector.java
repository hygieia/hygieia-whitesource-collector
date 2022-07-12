package com.capitalone.dashboard.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class WhiteSourceCollector extends Collector {
    private List<String> whiteSourceServers = new ArrayList<>();

    public List<String> getWhiteSourceServers() {
        return whiteSourceServers;
    }

    public void setWhiteSourceServers(List<String> whiteSourceServers) {
        this.whiteSourceServers = whiteSourceServers;
    }

    public static WhiteSourceCollector prototype(List<String> server) {
        WhiteSourceCollector protoType = new WhiteSourceCollector();
        protoType.setName("WhiteSource");
        protoType.setCollectorType(CollectorType.LibraryPolicy);
        protoType.setOnline(true);
        protoType.setEnabled(true);
        protoType.getWhiteSourceServers().addAll(server);
        Map<String, Object> allOptions = new HashMap<>();
        allOptions.put(WhiteSourceComponent.PRODUCT_NAME, "");
        allOptions.put(WhiteSourceComponent.PROJECT_NAME, "");
        allOptions.put(WhiteSourceComponent.PRODUCT_TOKEN, "");
        allOptions.put(WhiteSourceComponent.PROJECT_TOKEN, "");
        allOptions.put(WhiteSourceComponent.ORG_NAME, "");
        allOptions.put(WhiteSourceComponent.LOCAL_CONFIG, "");
        protoType.setAllFields(allOptions);

        Map<String, Object> uniqueOptions = new HashMap<>();
        uniqueOptions.put(WhiteSourceComponent.PROJECT_TOKEN, "");
        uniqueOptions.put(WhiteSourceComponent.ORG_NAME, "");
        protoType.setUniqueFields(uniqueOptions);
        return protoType;
    }
}
