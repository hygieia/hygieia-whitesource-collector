package com.capitalone.dashboard.collector;

import com.capitalone.dashboard.client.RestClient;
import com.capitalone.dashboard.model.LibraryPolicyResult;
import com.capitalone.dashboard.model.LibraryPolicyThreatDisposition;
import com.capitalone.dashboard.model.LibraryPolicyThreatLevel;
import com.capitalone.dashboard.model.LibraryPolicyType;
import com.capitalone.dashboard.model.WhiteSourceComponent;
import com.capitalone.dashboard.model.WhiteSourceProduct;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Component
public class DefaultWhiteSourceClient implements WhiteSourceClient {
    private static final Log LOG = LogFactory.getLog(DefaultWhiteSourceClient.class);
    private static final String API_URL = "/api/v1.3";
    private static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";
    private final RestClient restClient;
    private final WhiteSourceSettings whiteSourceSettings;

    @Autowired
    public DefaultWhiteSourceClient(RestClient restClient, WhiteSourceSettings settings) {
        this.restClient = restClient;
        this.whiteSourceSettings = settings;
    }

    @Override
    public String getOrgDetails(String instanceUrl, String orgToken) {
        try {
            JSONObject jsonObject = makeRestCall(getApiBaseUrl(instanceUrl), Constants.RequestType.getOrganizationDetails, orgToken, null, null);
            String orgName = (String) jsonObject.get(Constants.ORG_NAME);
            return orgName;
        } catch (Exception e) {
            LOG.error("Exception occurred while calling getOrgDetails " + e.getStackTrace());
        }
        return null;
    }

    @Override
    public List<WhiteSourceProduct> getProducts(String instanceUrl, String orgToken) {
        List<WhiteSourceProduct> whiteSourceProducts = new ArrayList<>();
        try {
            JSONObject jsonObject = makeRestCall(getApiBaseUrl(instanceUrl), Constants.RequestType.getAllProducts, orgToken, null, null);
            if (Objects.isNull(jsonObject)) return new ArrayList<>();
            JSONArray jsonArray = (JSONArray) jsonObject.get(Constants.PRODUCTS);
            if (CollectionUtils.isEmpty(jsonArray)) return new ArrayList<>();
            for (Object product : jsonArray) {
                JSONObject wsProduct = (JSONObject) product;
                WhiteSourceProduct whiteSourceProduct = new WhiteSourceProduct();
                whiteSourceProduct.setProductId(getLongValue(wsProduct, Constants.PRODUCT_ID));
                whiteSourceProduct.setProductName(getStringValue(wsProduct, Constants.PRODUCT_NAME));
                whiteSourceProduct.setProductToken(getStringValue(wsProduct, Constants.PRODUCT_TOKEN));
                whiteSourceProducts.add(whiteSourceProduct);
            }
        } catch (Exception e) {
            LOG.error("Exception occurred while retrieving getAllProducts " + e.getMessage());
        }
        return whiteSourceProducts;
    }

    @Override
    public List<WhiteSourceComponent> getAllProjectsForProduct(String instanceUrl, WhiteSourceProduct product, String orgToken, String orgName) {
        List<WhiteSourceComponent> whiteSourceProjects = new ArrayList<>();
        try {
            JSONObject jsonObject = makeRestCall(getApiBaseUrl(instanceUrl), Constants.RequestType.getAllProjects, orgToken, product.getProductToken(), null);
            if (Objects.isNull(jsonObject)) return new ArrayList<>();
            JSONArray jsonArray = (JSONArray) jsonObject.get(Constants.PROJECTS);
            if (jsonArray == null) return new ArrayList<>();
            for (Object project : jsonArray) {
                JSONObject wsProject = (JSONObject) project;
                WhiteSourceComponent whiteSourceProject = new WhiteSourceComponent();
                whiteSourceProject.setProjectName(getStringValue(wsProject, Constants.PROJECT_NAME));
                whiteSourceProject.setProjectToken(getStringValue(wsProject, Constants.PROJECT_TOKEN));
                whiteSourceProject.setProductToken(product.getProductToken());
                whiteSourceProject.setProductName(product.getProductName());
                whiteSourceProject.setOrgName(orgName);
                whiteSourceProjects.add(whiteSourceProject);
            }
        } catch (Exception e) {
            LOG.error("Exception occurred while retrieving getAllProducts " + e.getMessage());
        }
        return whiteSourceProjects;
    }

    @Override
    public LibraryPolicyResult getProjectInventory(String instanceUrl, WhiteSourceComponent whiteSourceComponent) {
        LibraryPolicyResult libraryPolicyResult = new LibraryPolicyResult();
        try {
            JSONObject jsonObject = makeRestCall(getApiBaseUrl(instanceUrl), Constants.RequestType.getProjectInventory, null, null, whiteSourceComponent.getProjectToken());
            JSONObject projectVitals = (JSONObject) Objects.requireNonNull(jsonObject).get(Constants.PROJECT_VITALS);
            if (Objects.isNull(projectVitals)) {
                return null;
            } else {
                libraryPolicyResult.setCollectorItemId(whiteSourceComponent.getId());
                libraryPolicyResult.setTimestamp(timestamp(projectVitals, Constants.LAST_UPDATED_DATE));
                libraryPolicyResult.setEvaluationTimestamp(timestamp(projectVitals, Constants.LAST_UPDATED_DATE));
                libraryPolicyResult.setProjectName(getStringValue(projectVitals, Constants.NAME));
                libraryPolicyResult.setProductName(getStringValue(projectVitals, Constants.PRODUCT_NAME));
                JSONArray libraries = (JSONArray) Objects.requireNonNull(jsonObject).get(Constants.LIBRARIES);
                if (!CollectionUtils.isEmpty(libraries)) {
                    for (Object lib : libraries) {
                        JSONObject library = (JSONObject) lib;
                        String componentName = getStringValue(library, Constants.NAME);
                        // add threat for license
                        JSONArray licenses = (JSONArray) Objects.requireNonNull(library).get(Constants.LICENSES);
                        if (!CollectionUtils.isEmpty(licenses)) {
                            setAllLibraryLicenses(licenses, libraryPolicyResult, componentName);
                        }
                        // add threat for Security vulns
                        JSONArray vulns = (JSONArray) Objects.requireNonNull(library).get(Constants.VULNERABILITIES);
                        if (!CollectionUtils.isEmpty(vulns)) {
                            setAllSecurityVulns(vulns, libraryPolicyResult, componentName);
                        }

                    }
                }
            }

        } catch (Exception e) {
            LOG.info("Exception occurred while calling getProjectInventory " + e.getMessage());
        }
        return libraryPolicyResult;
    }


    @Override
    public LibraryPolicyResult getProjectAlerts(String instanceUrl, WhiteSourceComponent whiteSourceComponent) {
        String url = getApiBaseUrl(instanceUrl);
        LibraryPolicyResult libraryPolicyResult = new LibraryPolicyResult();
        try {
            JSONObject jsonObject = makeRestCall(getApiBaseUrl(instanceUrl), Constants.RequestType.getProjectAlerts, null, null, whiteSourceComponent.getProjectToken());
            JSONArray alerts = (JSONArray) Objects.requireNonNull(jsonObject).get(Constants.ALERTS);
            if (CollectionUtils.isEmpty(alerts)) {
                return null;
            } else {
                libraryPolicyResult.setCollectorItemId(whiteSourceComponent.getId());
                libraryPolicyResult.setTimestamp(System.currentTimeMillis());
                for (Object a : alerts) {
                    JSONObject alert = (JSONObject) a;
                    String alertType = getStringValue(alert, Constants.TYPE);
                    String alertLevel = getStringValue(alert, Constants.LEVEL);
                    JSONObject library = (JSONObject) Objects.requireNonNull(alert).get(Constants.LIBRARY);
                    String creationDate = getStringValue(alert, Constants.CREATION_DATE);
                    String description = getStringValue(alert, Constants.DESCRIPTION);
                    String componentName = getStringValue(library, Constants.FILENAME);
                    libraryPolicyResult.setEvaluationTimestamp(getLongValue(alert, Constants.TIME));
                    // add threat for license
                    JSONArray licenses = (JSONArray) Objects.requireNonNull(library).get(Constants.LICENSES);
                    if (!CollectionUtils.isEmpty(licenses)) {
                        setAllLibraryLicensesAlerts(licenses, libraryPolicyResult, componentName, getDays(creationDate) + "", getLicenseThreatLevel(alertType, alertLevel, description));
                    }
                    // add threat for Security vulns
                    JSONObject vulns = (JSONObject) Objects.requireNonNull(alert).get(Constants.VULNERABILITY);
                    if (!CollectionUtils.isEmpty(vulns)) {
                        setSecurityVulns(vulns, libraryPolicyResult, componentName, getDays(creationDate) + "");
                    }

                }
            }
        } catch (Exception e) {
            LOG.info("Exception occurred while calling getProjectAlerts " + e.getMessage());
        }
        return libraryPolicyResult;
    }

    private JSONObject makeRestCall(String url, Constants.RequestType requestType, String orgToken, String productToken, String projectToken) {
        LOG.info("collecting analysis for ===> " + url + " and requestType : " + requestType);
        JSONObject requestJSON = getRequest(requestType, orgToken, productToken, projectToken);
        JSONParser parser = new JSONParser();
        try {
            ResponseEntity<String> response = restClient.makeRestCallPost(url, new HttpHeaders(), requestJSON);
            if ((response == null) || (response.toString().isEmpty())) return null;
            return (JSONObject) parser.parse(response.getBody());
        } catch (Exception e) {
            LOG.error("Exception occurred while calling " + url + " and requestType : " + requestType + " with error ===>  " + e.getMessage());
        }
        return null;
    }

    private void setAllLibraryLicenses(JSONArray licenses, LibraryPolicyResult libraryPolicyResult, String componentName) {
        for (Object l : licenses) {
            libraryPolicyResult.addThreat(LibraryPolicyType.License, LibraryPolicyThreatLevel.High, LibraryPolicyThreatDisposition.Open, Constants.OPEN, componentName, Constants.ZERO, Constants.ZERO);
        }
    }

    private void setAllLibraryLicensesAlerts(JSONArray licenses, LibraryPolicyResult libraryPolicyResult, String componentName, String age, LibraryPolicyThreatLevel severity) {
        for (Object l : licenses) {
            libraryPolicyResult.addThreat(LibraryPolicyType.License, severity, LibraryPolicyThreatDisposition.Open, Constants.OPEN, componentName, age, Constants.ZERO);
        }
    }

    private void setAllSecurityVulns(JSONArray vulns, LibraryPolicyResult libraryPolicyResult, String componentName) {
        for (Object vulnerability : vulns) {
            JSONObject vuln = (JSONObject) vulnerability;
            Double cvss3_score = toDouble(vuln, Constants.CVSS_3_SCORE);
            String cvss3_severity = getStringValue(vuln, Constants.CVSS_3_SEVERITY);
            libraryPolicyResult.addThreat(LibraryPolicyType.Security, LibraryPolicyThreatLevel.fromString(cvss3_severity), LibraryPolicyThreatDisposition.Open, Constants.OPEN, componentName, Constants.ZERO, cvss3_score.toString());
        }
    }

    private void setSecurityVulns(JSONObject vuln, LibraryPolicyResult libraryPolicyResult, String componentName, String age) {
        libraryPolicyResult.addThreat(LibraryPolicyType.Security, LibraryPolicyThreatLevel.fromString(getSecurityVulnSeverity(vuln)), LibraryPolicyThreatDisposition.Open, Constants.OPEN, componentName, age, getScore(vuln));
    }

    private String getScore(JSONObject vuln) {
        Double cvss3_score = toDouble(vuln, Constants.CVSS_3_SCORE);
        Double score = toDouble(vuln, Constants.SCORE1);
        if (Objects.nonNull(cvss3_score)) return cvss3_score.toString();
        if (Objects.nonNull(score)) return score.toString();
        return Constants.ZERO;
    }

    private String getSecurityVulnSeverity(JSONObject vuln) {
        String cvss3_severity = getStringValue(vuln, Constants.CVSS_3_SEVERITY);
        String severity = getStringValue(vuln, Constants.SEVERITY);
        if (Objects.nonNull(cvss3_severity)) return cvss3_severity;
        if (Objects.nonNull(severity)) return severity;
        return Constants.NONE;
    }

    private LibraryPolicyThreatLevel getLicenseThreatLevel(String alertType, String alertLevel, String description) {
        if (!CollectionUtils.isEmpty(whiteSourceSettings.getHighLicensePolicyTypes()) && whiteSourceSettings.getHighLicensePolicyTypes().contains(alertType)) {
            return LibraryPolicyThreatLevel.High;
        }
        if (!CollectionUtils.isEmpty(whiteSourceSettings.getMediumLicensePolicyTypes()) && whiteSourceSettings.getMediumLicensePolicyTypes().contains(alertType)) {
            return LibraryPolicyThreatLevel.Medium;
        }
        if (!CollectionUtils.isEmpty(whiteSourceSettings.getLowLicensePolicyTypes()) && whiteSourceSettings.getLowLicensePolicyTypes().contains(alertType)) {
            return LibraryPolicyThreatLevel.Low;
        }
        return LibraryPolicyThreatLevel.None;
    }

    private String getApiBaseUrl(String instanceUrl) {
        return instanceUrl + API_URL;
    }


    private String getStringValue(JSONObject jsonObject, String key) {
        if (jsonObject == null || jsonObject.get(key) == null) return null;
        return (String) jsonObject.get(key);
    }

    private Long getLongValue(JSONObject jsonObject, String key) {
        if (jsonObject == null || jsonObject.get(key) == null) return null;
        return (Long) jsonObject.get(key);
    }

    private Double toDouble(JSONObject json, String key) {
        Object obj = json.get(key);
        return obj == null ? null : (Double) obj;
    }

    private static long getDays(String creationDate) {
        long days = 0;
        try {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
            Date past = format.parse(creationDate);
            Date now = new Date();
            days = TimeUnit.MILLISECONDS.toDays(now.getTime() - past.getTime());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return days;
    }


    private JSONObject getRequest(Constants.RequestType requestType, String orgToken, String productToken, String projectToken) {
        JSONObject requestJSON = new JSONObject();
        requestJSON.put(Constants.REQUEST_TYPE, requestType.toString());
        requestJSON.put(Constants.USER_KEY, whiteSourceSettings.getUserKey());
        switch (requestType) {
            case getProjectInventory:
            case getProjectAlerts:
                requestJSON.put(Constants.PROJECT_TOKEN, projectToken);
                return requestJSON;
            case getAllProjects:
                requestJSON.put(Constants.PRODUCT_TOKEN, productToken);
                return requestJSON;
            case getAllProducts:
            case getOrganizationDetails:
                requestJSON.put(Constants.ORG_TOKEN, orgToken);
                return requestJSON;
            default:
                return requestJSON;
        }
    }

    private long timestamp(JSONObject json, String key) {
        Object obj = json.get(key);
        if (obj != null) {
            try {
                return new SimpleDateFormat(DATE_FORMAT).parse(obj.toString()).getTime();
            } catch (java.text.ParseException e) {
                LOG.error(obj + " is not in expected format " + DATE_FORMAT, e);
            }
        }
        return 0;
    }

}
