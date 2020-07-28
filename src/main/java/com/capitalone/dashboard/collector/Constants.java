package com.capitalone.dashboard.collector;

public final class Constants {

    public static final String NONE = "None";
    public static final String PRODUCTS = "products";
    public static final String PRODUCT_ID = "productId";
    public static final String PRODUCT_NAME = "productName";
    public static final String PRODUCT_TOKEN = "productToken";
    public static final String PROJECTS = "projects";
    public static final String PROJECT_NAME = "projectName";
    public static final String PROJECT_TOKEN = "projectToken";
    public static final String PROJECT_VITALS = "projectVitals";
    public static final String LAST_UPDATED_DATE = "lastUpdatedDate";
    public static final String NAME = "name";
    public static final String LIBRARIES = "libraries";
    public static final String LICENSES = "licenses";
    public static final String VULNERABILITIES = "vulnerabilities";
    public static final String TYPE = "type";
    public static final String LEVEL = "level";
    public static final String LIBRARY = "library";
    public static final String CREATION_DATE = "creation_date";
    public static final String DESCRIPTION = "description";
    public static final String FILENAME = "filename";
    public static final String TIME = "time";
    public static final String VULNERABILITY = "vulnerability";
    public static final String OPEN = "Open";
    public static final String CVSS_3_SCORE = "cvss3_score";
    public static final String CVSS_3_SEVERITY = "cvss3_severity";
    public static final String SCORE1 = "score";
    public static final String SEVERITY = "severity";
    public static final String ORG_NAME = "orgName";
    public static final String REQUEST_TYPE = "requestType";
    public static final String USER_KEY = "userKey";
    public static final String ORG_TOKEN = "orgToken";
    public static final String ZERO = "0";
    public static final String ALERTS = "alerts";

    public enum RequestType {
        getAllProducts,
        getAllProjects,
        getOrganizationDetails,
        getProjectAlerts,
        getProjectInventory;

        public static RequestType fromString(String value) {
            for (RequestType requestType : values()) {
                if (requestType.toString().equalsIgnoreCase(value)) {
                    return requestType;
                }
            }
            throw new IllegalArgumentException(value + " is not a valid requestType.");
        }
    }
}

