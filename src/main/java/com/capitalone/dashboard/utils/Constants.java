package com.capitalone.dashboard.utils;

public final class Constants {

    public static final String NONE = "None";
    public static final String PRODUCTS = "products";
    public static final String PRODUCT_ID = "productId";
    public static final String PRODUCT_NAME = "productName";
    public static final String PRODUCT_TOKEN = "productToken";
    public static final String PROJECTS = "projects";
    public static final String PROJECT_NAME = "projectName";
    public static final String PROJECT_TOKEN = "projectToken";
    public static final String ALT_IDENTIFIER = "altIdentifier";
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
    public static final String CREATIONDATE = "creationDate";
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
    public static final String CHANGES = "changes";
    public static final String PROJECT = "PROJECT";
    public static final String SCOPE = "scope";
    public static final String START_DATE_TIME = "startDateTime";
    public static final String CHANGE_ASPECT = "changeAspect";
    public static final String CHANGE_CATEGORY = "changeCategory";
    public static final String CHANGE_CLASS = "changeClass";
    public static final String WHITE_SOURCE = "WhiteSource";
    public static final String yyyy_MM_dd_HH_mm_ss = "yyyy-MM-dd HH:mm:ss";
    public static final String yyyy_MM_dd = "yyyy-MM-dd";
    public static final String yyyy_MM_dd_HH_mm_ss_z = yyyy_MM_dd_HH_mm_ss + " Z";
    public static final String SCOPE_NAME = "scopeName";
    public static final String CHANGE_SCOPE_ID = "changeScopeId";
    public static final String OPERATOR = "operator";
    public static final String USER_EMAIL = "userEmail";
    public static final String ORG_ID = "orgId";
    public static final String BEFORE_CHANGE = "beforeChange";
    public static final String AFTER_CHANGE = "afterChange";
    public static final String LIBRARY_SCOPE = "LIBRARY";
    public static final String ID = "id";
    public static final String TOKEN = "token";
    public static final String FROM_DATE = "fromDate";
    public static final String ALERT_TYPE = "alertType";
    public static final String REJECTED_BY_POLICY = "REJECTED_BY_POLICY_RESOURCE";
    public static final String SECURITY_VULNERABILITY = "SECURITY_VULNERABILITY";
    public static final String DEFAULT_WHITESOURCE_TIMEZONE = "UTC";


    public enum RequestType {
        getAllProducts,
        getAllProjects,
        getOrganizationDetails,
        getProjectAlerts,
        getProductAlerts,
        getOrganizationAlertsByType,
        getProjectInventory,
        getChangesReport,
        getProjectVitals,
        getOrganizationProjectVitals;

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

