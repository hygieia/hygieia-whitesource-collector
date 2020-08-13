package com.capitalone.dashboard.collector;

import com.capitalone.dashboard.client.RestClient;
import com.capitalone.dashboard.misc.HygieiaException;
import com.capitalone.dashboard.model.CollectorItem;
import com.capitalone.dashboard.model.LibraryPolicyResult;
import com.capitalone.dashboard.model.LibraryPolicyThreatDisposition;
import com.capitalone.dashboard.model.LibraryPolicyThreatLevel;
import com.capitalone.dashboard.model.LibraryPolicyType;
import com.capitalone.dashboard.model.LicensePolicyType;
import com.capitalone.dashboard.model.WhiteSourceChangeRequest;
import com.capitalone.dashboard.model.WhiteSourceComponent;
import com.capitalone.dashboard.model.WhiteSourceProduct;
import com.capitalone.dashboard.model.WhiteSourceRequest;
import com.capitalone.dashboard.repository.CollectorItemRepository;
import com.capitalone.dashboard.repository.LibraryPolicyResultsRepository;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.joda.time.format.DateTimeFormat;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Component
public class DefaultWhiteSourceClient implements WhiteSourceClient {
    private static final Log LOG = LogFactory.getLog(DefaultWhiteSourceClient.class);
    private static final String API_URL = "/api/v1.3";
    private final RestClient restClient;
    private final WhiteSourceSettings whiteSourceSettings;
    private final CollectorItemRepository collectorItemRepository;
    private final LibraryPolicyResultsRepository libraryPolicyResultsRepository;


    @Autowired
    public DefaultWhiteSourceClient(RestClient restClient, WhiteSourceSettings settings, CollectorItemRepository collectorItemRepository, LibraryPolicyResultsRepository libraryPolicyResultsRepository) {
        this.restClient = restClient;
        this.whiteSourceSettings = settings;
        this.collectorItemRepository = collectorItemRepository;
        this.libraryPolicyResultsRepository = libraryPolicyResultsRepository;
    }

    @Override
    public String getOrgDetails(String instanceUrl, String orgToken) throws HygieiaException {
        try {
            JSONObject jsonObject = makeRestCall(getApiBaseUrl(instanceUrl), Constants.RequestType.getOrganizationDetails, orgToken, null, null,orgToken,null);
            String orgName = (String) jsonObject.get(Constants.ORG_NAME);
            return orgName;
        } catch (Exception e) {
            throw new HygieiaException("Exception occurred while calling getOrgDetails",e.getCause(),HygieiaException.BAD_DATA);
        }
    }

    @Override
    public List<WhiteSourceProduct> getProducts(String instanceUrl, String orgToken,String orgName) throws HygieiaException {
        List<WhiteSourceProduct> whiteSourceProducts = new ArrayList<>();
        try {
            JSONObject jsonObject = makeRestCall(getApiBaseUrl(instanceUrl), Constants.RequestType.getAllProducts, orgToken, null, null,orgName, null);
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
            throw new HygieiaException("Exception occurred while retrieving getAllProducts for orgName="+orgName,e.getCause(),HygieiaException.BAD_DATA);
        }
        return whiteSourceProducts;
    }

    @Override
    public List<WhiteSourceComponent> getAllProjectsForProduct(String instanceUrl, WhiteSourceProduct product, String orgToken, String orgName) {
        List<WhiteSourceComponent> whiteSourceProjects = new ArrayList<>();
        try {
            JSONObject jsonObject = makeRestCall(getApiBaseUrl(instanceUrl), Constants.RequestType.getAllProjects, orgToken, product.getProductToken(), null,orgName,null);
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
            LOG.error("Exception occurred while retrieving getAllProjectsForProduct for productName="+product.getProductName()+", Exception=" + e.getMessage());
        }
        return whiteSourceProjects;
    }

    @Override
    public LibraryPolicyResult getProjectInventory(String instanceUrl, WhiteSourceComponent whiteSourceComponent) {
        LibraryPolicyResult libraryPolicyResult = new LibraryPolicyResult();
        try {
            JSONObject jsonObject = makeRestCall(getApiBaseUrl(instanceUrl), Constants.RequestType.getProjectInventory, null, null, whiteSourceComponent.getProjectToken(),whiteSourceComponent.getOrgName(),null);
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
            LOG.info("Exception occurred while calling getProjectInventory for projectName="+whiteSourceComponent.getProjectName()+", Exception=" + e.getMessage());
        }
        return libraryPolicyResult;
    }


    @Override
    public LibraryPolicyResult getProjectAlerts(String instanceUrl, WhiteSourceComponent whiteSourceComponent) {
        String url = getApiBaseUrl(instanceUrl);
        LibraryPolicyResult libraryPolicyResult = new LibraryPolicyResult();
        try {
            JSONObject jsonObject = makeRestCall(getApiBaseUrl(instanceUrl), Constants.RequestType.getProjectAlerts, null, null, whiteSourceComponent.getProjectToken(),whiteSourceComponent.getOrgName(),null);
            JSONArray alerts = (JSONArray) Objects.requireNonNull(jsonObject).get(Constants.ALERTS);
            if (CollectionUtils.isEmpty(alerts)) {
                return null;
            } else {
                JSONObject projectVitalsObject = makeRestCall(getApiBaseUrl(instanceUrl), Constants.RequestType.getProjectVitals, null, null, whiteSourceComponent.getProjectToken(),whiteSourceComponent.getOrgName(),null);
                getEvaluationTimeStamp(libraryPolicyResult, projectVitalsObject);
                libraryPolicyResult.setCollectorItemId(whiteSourceComponent.getId());
                libraryPolicyResult.setTimestamp(System.currentTimeMillis());
                transform(libraryPolicyResult, alerts);
            }
        } catch (Exception e) {
            LOG.info("Exception occurred while calling getProjectAlerts for projectName="+whiteSourceComponent.getProjectName()+", Exception=" + e.getMessage());
        }
        return libraryPolicyResult;
    }


    @Override
    public List<WhiteSourceChangeRequest> getChangeRequestLog(String instanceUrl, String orgToken, String orgName, long historyTimestamp) {
        String startDateT = getTime(historyTimestamp);
        JSONObject jsonObject = makeRestCall(getApiBaseUrl(instanceUrl),Constants.RequestType.getChangesReport,orgToken,null,null,orgName,startDateT);
        JSONArray changes = (JSONArray) Objects.requireNonNull(jsonObject).get(Constants.CHANGES);
        List<WhiteSourceChangeRequest> changeRequests = new ArrayList<>();
        if(CollectionUtils.isEmpty(changes)){
            return changeRequests;
        }else{
            for (Object c : changes) {
                JSONObject change = (JSONObject) c;
                if(! getStringValue(change, Constants.SCOPE).equalsIgnoreCase(Constants.PROJECT)) continue;
                WhiteSourceChangeRequest whiteSourceChangeRequest = new WhiteSourceChangeRequest();
                whiteSourceChangeRequest.setScope(getStringValue(change, Constants.SCOPE));
                whiteSourceChangeRequest.setStartDateTime(timestamp(change, Constants.START_DATE_TIME));
                whiteSourceChangeRequest.setChangeAspect(getStringValue(change, Constants.CHANGE_ASPECT));
                whiteSourceChangeRequest.setChangeCategory(getStringValue(change, Constants.CHANGE_CATEGORY));
                whiteSourceChangeRequest.setChangeClass(getStringValue(change, Constants.CHANGE_CLASS));
                whiteSourceChangeRequest.setOrgName(getStringValue(change,Constants.ORG_NAME));
                whiteSourceChangeRequest.setProductName(getStringValue(change,Constants.PRODUCT_NAME));
                whiteSourceChangeRequest.setProjectName(getStringValue(change,Constants.PROJECT_NAME));
                changeRequests.add(whiteSourceChangeRequest);
            }
        }
        return changeRequests;
    }



    private JSONObject makeRestCall(String url, Constants.RequestType requestType, String orgToken, String productToken, String projectToken, String orgName, String startDateTime) {
        LOG.info("collecting analysis for orgName=" + orgName + " and requestType=" + requestType);
        JSONObject requestJSON = getRequest(requestType, orgToken, productToken, projectToken, startDateTime);
        JSONParser parser = new JSONParser();
        try {
            ResponseEntity<String> response = restClient.makeRestCallPost(url, new HttpHeaders(), requestJSON);
            if ((response == null) || (response.toString().isEmpty())) return null;
            return (JSONObject) parser.parse(response.getBody());
        } catch (Exception e) {
            LOG.error("Exception occurred while calling REST for orgName=" + orgName + " and requestType=" + requestType + ", Exception=" + e.getMessage());
        }
        return null;
    }

    @Override
    public void getEvaluationTimeStamp(LibraryPolicyResult libraryPolicyResult, JSONObject projectVitalsObject) {
        JSONArray projectVitals = (JSONArray)  Objects.requireNonNull(projectVitalsObject).get(Constants.PROJECT_VITALS);
        if (!CollectionUtils.isEmpty(projectVitals)) {
            for (Object projectVital : projectVitals){
                JSONObject projectVitalObject = (JSONObject) projectVital;
                libraryPolicyResult.setEvaluationTimestamp(timestamp(projectVitalObject, Constants.LAST_UPDATED_DATE));
            }
        }
    }


    private Object decodeJsonPayload (String payload) throws HygieiaException{
        if(payload == null || StringUtils.isEmpty(payload)) {
            throw new HygieiaException("WhiteSource request is not a valid json.", HygieiaException.JSON_FORMAT_ERROR);
        }
        byte[] decodedBytes = Base64.getDecoder().decode(payload);
        String decodedPayload = new String(decodedBytes);
        JSONParser parser = new JSONParser();
        Object obj  = null;
        try {
            obj =  parser.parse(decodedPayload);

        } catch (ParseException e) {
            e.printStackTrace();
        }
        return obj;
    }

    private boolean isNewQualityData(CollectorItem component, LibraryPolicyResult libraryPolicyResult) {
        return libraryPolicyResultsRepository.findByCollectorItemIdAndTimestamp(
                component.getId(), libraryPolicyResult.getEvaluationTimestamp()) == null;
    }

    private LibraryPolicyResult getQualityData(CollectorItem component, LibraryPolicyResult libraryPolicyResult) {
        return libraryPolicyResultsRepository.findByCollectorItemIdAndTimestamp(
                component.getId(), libraryPolicyResult.getEvaluationTimestamp());
    }


    @Override
    public void transform(LibraryPolicyResult libraryPolicyResult, JSONArray alerts) {
        for (Object a : alerts) {
            JSONObject alert = (JSONObject) a;
            String alertType = getStringValue(alert, Constants.TYPE);
            String alertLevel = getStringValue(alert, Constants.LEVEL);
            JSONObject library = (JSONObject) Objects.requireNonNull(alert).get(Constants.LIBRARY);
            String creationDate = getStringValue(alert, Constants.CREATION_DATE);
            String description = getStringValue(alert, Constants.DESCRIPTION);
            String componentName = getStringValue(library, Constants.FILENAME);
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



    private long convertTimestamp (String timestamp){

        long time = 0;

        if(StringUtils.isNotEmpty(timestamp)){
            time = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss").parseMillis(timestamp);

        }

        return time;
    }


    public String process(WhiteSourceRequest whiteSourceRequest) throws HygieiaException {
        JSONObject projectVital = (JSONObject) decodeJsonPayload(whiteSourceRequest.getProjectVitals());
        JSONArray alerts = (JSONArray) decodeJsonPayload(whiteSourceRequest.getAlerts());
        String orgName = whiteSourceRequest.getOrgName();
        String name =  (String) projectVital.get("name");
        String productName =  (String) projectVital.get("productName");
        String lastUpdatedDate = (String) projectVital.get("lastUpdatedDate");
        LibraryPolicyResult libraryPolicyResult = new LibraryPolicyResult();
        long timestamp = convertTimestamp(lastUpdatedDate);
        libraryPolicyResult.setEvaluationTimestamp(timestamp);
        CollectorItem collectorItem = collectorItemRepository.findByOrgNameAndProjectNameAndProductName(orgName, name, productName);
        LOG.info("WhiteSourceRequest collecting  analysis for orgName= "+ orgName + "name : " + name + "productName : " + productName + "timestamp : "+ timestamp );
        if(collectorItem != null){
            LibraryPolicyResult lp = getQualityData(collectorItem,libraryPolicyResult);
            if(lp != null){
                LOG.info("Record already exist in LibraryPolicy " + lp.getId());
                return "Record already exist in LibraryPolicy  " + lp.getId();
            }
        }
        transform(libraryPolicyResult, alerts);
        libraryPolicyResult.setTimestamp(System.currentTimeMillis());
        libraryPolicyResult.setCollectorItemId(collectorItem.getId());
        libraryPolicyResult.setBuildUrl(whiteSourceRequest.getBuildUrl());
        return processCollectorItem(collectorItem,libraryPolicyResult);
    }



    public String processCollectorItem(CollectorItem collectorItem, LibraryPolicyResult libraryPolicyResult){
        libraryPolicyResult =libraryPolicyResultsRepository.save(libraryPolicyResult);
        LOG.info("Successfully updated library policy result  "+ libraryPolicyResult.getId());
        return " Successfully updated library policy result " + libraryPolicyResult.getId();
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
        if (!CollectionUtils.isEmpty(whiteSourceSettings.getCriticalLicensePolicyTypes()) && getLicenseSeverity(whiteSourceSettings.getCriticalLicensePolicyTypes(),alertType,description)) {
            return LibraryPolicyThreatLevel.Critical;
        }
        if (!CollectionUtils.isEmpty(whiteSourceSettings.getHighLicensePolicyTypes()) && getLicenseSeverity(whiteSourceSettings.getHighLicensePolicyTypes(),alertType,description)) {
            return LibraryPolicyThreatLevel.High;
        }
        if (!CollectionUtils.isEmpty(whiteSourceSettings.getMediumLicensePolicyTypes()) && getLicenseSeverity(whiteSourceSettings.getMediumLicensePolicyTypes(),alertType,description)) {
            return LibraryPolicyThreatLevel.Medium;
        }
        if (!CollectionUtils.isEmpty(whiteSourceSettings.getLowLicensePolicyTypes()) && getLicenseSeverity(whiteSourceSettings.getLowLicensePolicyTypes(),alertType,description)) {
            return LibraryPolicyThreatLevel.Low;
        }
        return LibraryPolicyThreatLevel.None;
    }

    private boolean getLicenseSeverity(List<LicensePolicyType> licensePolicyTypes, String alertType, String description){
        return licensePolicyTypes.stream().anyMatch(licensePolicyType -> licensePolicyType.getPolicyName().equalsIgnoreCase(alertType)
        && licensePolicyType.getDescriptions().contains(description));
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


    private JSONObject getRequest(Constants.RequestType requestType, String orgToken, String productToken, String projectToken, String startDateTime) {
        JSONObject requestJSON = new JSONObject();
        requestJSON.put(Constants.REQUEST_TYPE, requestType.toString());
        requestJSON.put(Constants.USER_KEY, whiteSourceSettings.getUserKey());
        switch (requestType) {
            case getProjectInventory:
            case getProjectAlerts:
            case getProjectVitals:
                requestJSON.put(Constants.PROJECT_TOKEN, projectToken);
                return requestJSON;
            case getAllProjects:
                requestJSON.put(Constants.PRODUCT_TOKEN, productToken);
                return requestJSON;
            case getAllProducts:
            case getOrganizationDetails:
                requestJSON.put(Constants.ORG_TOKEN, orgToken);
                return requestJSON;
            case getChangesReport:
                requestJSON.put(Constants.START_DATE_TIME,startDateTime);
                requestJSON.put(Constants.ORG_TOKEN, orgToken);
                requestJSON.put(Constants.SCOPE, Constants.PROJECT);
            default:
                return requestJSON;
        }
    }

    private long timestamp(JSONObject json, String key) {
        Object obj = json.get(key);
        if (obj != null) {
            try {
                return new SimpleDateFormat(Constants.YYYY_MM_DD_HH_MM_SS).parse(obj.toString()).getTime();
            } catch (java.text.ParseException e) {
                LOG.error(obj + " is not in expected format " + Constants.YYYY_MM_DD_HH_MM_SS, e);
            }
        }
        return 0;
    }

    private String getTime(long timestamp){
        DateFormat format = new SimpleDateFormat(Constants.YYYY_MM_DD_HH_MM_SS);
        return format.format(new Date(timestamp));
    }

}
