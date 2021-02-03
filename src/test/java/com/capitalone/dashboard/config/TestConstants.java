package com.capitalone.dashboard.config;

import java.util.ArrayList;
import java.util.List;

public final class TestConstants {
    public static final long FIX_TIME_MILLIS = 1609228800000L;
    public static final String USER_KEY = "1234";
    public static final String ORG_KEY = "9876";

    public static final String API_URL = "https://myserver.com/api/v1.3";
    public static final String INSTANCE_URL = "https://myserver.com";

    public static final List<String> PRODUCT_TOKENS = new ArrayList<>();
    public static final List<String> PROJECT_TOKENS = new ArrayList<>();
    public static final List<String> PROJECT_VITALS_TOKENS = new ArrayList<>();

    public static final String PRODUCT_TOKEN_Test1Product = "25987580b94e4bd5a0a70ffe4c68a890e7b218a0a000407bb249c2df2f25fea4";
    public static final String PRODUCT_TOKEN_Test2Product = "5aecc4760a2640afb7c98eb5aab6b22f614450a5404c4beaa683ce84309beab4";
    public static final String PRODUCT_TOKEN_Test3Product = "212d7082147b430dae7d92e4d56d511f21277b9ac8324259be002313d90b5e37";
    public static final String PRODUCT_TOKEN_Test4Product = "254dae3b01d2417f8b094fa6cdc821fd1ce635e8aea44516a899460e1eb0a4e0";

    public static final String PROJECT_TOKEN_Test1Project = "05b07ce07a6f49c6b3ac7ad9fbbd383b443db8a4486c4cfe91065441d31267bd";
    public static final String PROJECT_TOKEN_Test2Project = "e660acc1583745e793ac4435bbccbb9974f9b5bbcee94be09b183e5c34aac039";
    public static final String PROJECT_TOKEN_Test3Project = "95a82d2395bc4da98083bb9ab84cff349ed92e660ec745439ef37818b8ded1a7";
    public static final String PROJECT_TOKEN_Test4Project = "612123ad31d34ff5b47ab2fe9eadba3f3afe3a76d0534c2a9fa8d30db9e563ff";
    public static final String PROJECT_TOKEN_Test5Project = "75d22af901e84194b5b4137854506895d89da4c616f941ae903c26bb3b74d799";
    public static final String PROJECT_TOKEN_Test6Project = "906b622d9f154bef8130025eb5df45cbf75aa8058c554976a17db93e09c57241";



    static {
        PRODUCT_TOKENS.add(PRODUCT_TOKEN_Test1Product);
        PRODUCT_TOKENS.add(PRODUCT_TOKEN_Test2Product);
        PRODUCT_TOKENS.add(PRODUCT_TOKEN_Test3Product);
        PRODUCT_TOKENS.add(PRODUCT_TOKEN_Test4Product);

        PROJECT_TOKENS.add(PROJECT_TOKEN_Test1Project);
        PROJECT_TOKENS.add(PROJECT_TOKEN_Test2Project);
        PROJECT_TOKENS.add(PROJECT_TOKEN_Test3Project);
        PROJECT_TOKENS.add(PROJECT_TOKEN_Test4Project);
        PROJECT_TOKENS.add(PROJECT_TOKEN_Test5Project);
        PROJECT_TOKENS.add(PROJECT_TOKEN_Test6Project);
        
        PROJECT_VITALS_TOKENS. add("c0a91ffcbc034a4e9ba1415dce60ec57fc45d25bf86540019171d5fd8a5a9608");
        PROJECT_VITALS_TOKENS. add("ce5c56d315df4c12a768d8f1e728d7c8d3c1819ec5ec4f829c7ddc49ff718f83");
        PROJECT_VITALS_TOKENS. add("1d41d1de33b24437b125ccd67be3155218b9f4dff4e24b149769023af2c4f7d3");
        PROJECT_VITALS_TOKENS. add("b937cacb0d94421abad4c414a9068bb93836e118c3de49c8aefe6d60f80e049f");
        PROJECT_VITALS_TOKENS. add("462daa35dc104f3c9912f9a274ea5f9299927cf91f5a4325b2cd4b771ff9c94b");
        PROJECT_VITALS_TOKENS. add("51e07c54097047ef8ad1b5b0291a5cd16a668521d7d34d1dbe7cf4adbc6cfb37");
        PROJECT_VITALS_TOKENS. add("a1bdbb688e594ccbaf1124a65142f97ff10c8b453f2e47968137f8d401a74765");
        PROJECT_VITALS_TOKENS. add("ac6030a7d16247748620900b5743d2e39836de27d60e474695cee1b062838d35");
    }



    public static final String ORG_DETAILS_REQUEST = "{\"requestType\":\"getOrganizationDetails\",\"orgToken\":\"" + ORG_KEY + "\",\"userKey\":\"" + USER_KEY +  "\"}";
    public static final String ORG_ALERTS_POLICY_REQUEST = "{\"fromDate\":\"2020-12-29 14:06:02\",\"alertType\":\"REJECTED_BY_POLICY_RESOURCE\",\"requestType\":\"getOrganizationAlertsByType\",\"orgToken\":\""+ORG_KEY+"\",\"userKey\":\""+USER_KEY+"\"}";
    public static final String ORG_ALERTS_SECURITY_REQUEST = "{\"fromDate\":\"2020-12-29 14:06:02\",\"alertType\":\"SECURITY_VULNERABILITY\",\"requestType\":\"getOrganizationAlertsByType\",\"orgToken\":\"9876\",\"userKey\":\"1234\"}";
    public static final String CHANGE_REPORT_REQUEST = "{\"startDateTime\":\"2020-12-29 14:06:02\",\"requestType\":\"getChangesReport\",\"orgToken\":\"9876\",\"userKey\":\"1234\"}";
    public static final String ALL_PRODUCTS_REQUEST = "{\"requestType\":\"getAllProducts\",\"orgToken\":\"9876\",\"userKey\":\"1234\"}";
    public static final String ALL_PROJECTS_REQUEST = "{\"productToken\":\"%s\",\"requestType\":\"getAllProjects\",\"userKey\":\"1234\"}";
    public static final String PROJECT_VITALS_FOR_ORG_REQUEST = "{\"requestType\":\"getOrganizationProjectVitals\",\"orgToken\":\"9876\",\"userKey\":\"1234\"}";

    public static final String PRODUCT_ALERTS_REQUEST_FOR_TOKEN(String token) {
        return "{\"productToken\":\"" + token + "\",\"requestType\":\"getProductAlerts\",\"userKey\":\"" + USER_KEY + "\"}";
    }

    public static final String PROJECT_ALERTS_REQUEST_FOR_TOKEN(String token) {
        return "{\"projectToken\":\"" + token + "\",\"requestType\":\"getProjectAlerts\",\"userKey\":\"" + USER_KEY + "\"}";
    }

    public static final String PROJECT_VITALS_REQUEST_FOR_TOKEN(String token) {
        return "{\"projectToken\":\"" + token + "\",\"requestType\":\"getProjectVitals\",\"userKey\":\"" + USER_KEY + "\"}";
    }
}
