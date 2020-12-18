package com.capitalone.dashboard.collector;

import com.capitalone.dashboard.client.RestClient;
import com.capitalone.dashboard.misc.HygieiaException;
import com.capitalone.dashboard.model.Collector;
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
import com.capitalone.dashboard.model.WhiteSourceServerSettings;
import com.capitalone.dashboard.repository.CollectorItemRepository;
import com.capitalone.dashboard.repository.CollectorRepository;
import com.capitalone.dashboard.repository.LibraryPolicyResultsRepository;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.capitalone.dashboard.collector.Constants.YYYY_MM_DD_HH_MM_SS;

@Component
public class DefaultWhiteSourceClient implements WhiteSourceClient {
    private static final Log LOG = LogFactory.getLog(DefaultWhiteSourceClient.class);
    private static final String API_URL = "/api/v1.3";
    private final RestClient restClient;
    private final WhiteSourceSettings whiteSourceSettings;
    private final CollectorItemRepository collectorItemRepository;
    private final LibraryPolicyResultsRepository libraryPolicyResultsRepository;
    private final CollectorRepository collectorRepository;


    @Autowired
    public DefaultWhiteSourceClient(RestClient restClient, WhiteSourceSettings settings, CollectorItemRepository collectorItemRepository,
                                    LibraryPolicyResultsRepository libraryPolicyResultsRepository,
                                    CollectorRepository collectorRepository) {
        this.restClient = restClient;
        this.whiteSourceSettings = settings;
        this.collectorItemRepository = collectorItemRepository;
        this.libraryPolicyResultsRepository = libraryPolicyResultsRepository;
        this.collectorRepository = collectorRepository;
    }

    @Override
    public String getOrgDetails(String instanceUrl, WhiteSourceServerSettings serverSettings) throws HygieiaException {
        try {
            JSONObject jsonObject = makeRestCall(getApiBaseUrl(instanceUrl), Constants.RequestType.getOrganizationDetails, serverSettings.getOrgToken(), null, null,serverSettings.getOrgToken(),null,serverSettings);
            String orgName = (String) jsonObject.get(Constants.ORG_NAME);
            return orgName;
        } catch (Exception e) {
            throw new HygieiaException("Exception occurred while calling getOrgDetails",e.getCause(),HygieiaException.BAD_DATA);
        }
    }

    @Override
    public List<WhiteSourceProduct> getProducts(String instanceUrl, String orgToken,String orgName, WhiteSourceServerSettings serverSettings) throws HygieiaException {
        List<WhiteSourceProduct> whiteSourceProducts = new ArrayList<>();
        try {
            final String searchCriteria = whiteSourceSettings.getSearchCriteria();
            JSONObject jsonObject = makeRestCall(getApiBaseUrl(instanceUrl), Constants.RequestType.getAllProducts, orgToken, null, null,orgName, null, serverSettings);
            if (Objects.isNull(jsonObject)) return new ArrayList<>();
            JSONArray jsonArray = (JSONArray) jsonObject.get(Constants.PRODUCTS);
            if (CollectionUtils.isEmpty(jsonArray)) return new ArrayList<>();
            for (Object product : jsonArray) {
                JSONObject wsProduct = (JSONObject) product;
                WhiteSourceProduct whiteSourceProduct = new WhiteSourceProduct();
                String wsProductName = getStringValue(wsProduct, Constants.PRODUCT_NAME);
                if(!processRecord(getGithubOrgname(wsProductName),searchCriteria)) continue;
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
    public List<WhiteSourceComponent> getAllProjectsForProduct(String instanceUrl, WhiteSourceProduct product, String orgToken, String orgName, WhiteSourceServerSettings serverSettings) {
        List<WhiteSourceComponent> whiteSourceProjects = new ArrayList<>();
        try {
            JSONObject jsonObject = makeRestCall(getApiBaseUrl(instanceUrl), Constants.RequestType.getAllProjects, orgToken, product.getProductToken(), null,orgName,null,serverSettings);
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
    public LibraryPolicyResult getProjectInventory(String instanceUrl, WhiteSourceComponent whiteSourceComponent,WhiteSourceServerSettings serverSettings) {
        LibraryPolicyResult libraryPolicyResult = new LibraryPolicyResult();
        try {
            JSONObject jsonObject = makeRestCall(getApiBaseUrl(instanceUrl), Constants.RequestType.getProjectInventory, null, null, whiteSourceComponent.getProjectToken(),whiteSourceComponent.getOrgName(),null,serverSettings);
            JSONObject projectVitals = (JSONObject) Objects.requireNonNull(jsonObject).get(Constants.PROJECT_VITALS);
            if (Objects.isNull(projectVitals)) {
                return null;
            } else {
                libraryPolicyResult.setCollectorItemId(whiteSourceComponent.getId());
                libraryPolicyResult.setTimestamp(convertTimestamp(timeUtils(dateTime(projectVitals, Constants.LAST_UPDATED_DATE))));
                libraryPolicyResult.setEvaluationTimestamp(convertTimestamp(timeUtils(dateTime(projectVitals, Constants.LAST_UPDATED_DATE))));
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
    public LibraryPolicyResult getProjectAlerts(String instanceUrl, WhiteSourceComponent whiteSourceComponent, WhiteSourceServerSettings serverSettings) {
        LibraryPolicyResult libraryPolicyResult = new LibraryPolicyResult();
        try {
            JSONObject jsonObject = makeRestCall(getApiBaseUrl(instanceUrl), Constants.RequestType.getProjectAlerts, null, null, whiteSourceComponent.getProjectToken(),whiteSourceComponent.getOrgName(),null,serverSettings);
            JSONArray alerts = (JSONArray) Objects.requireNonNull(jsonObject).get(Constants.ALERTS);
            JSONObject projectVitalsObject = makeRestCall(getApiBaseUrl(instanceUrl), Constants.RequestType.getProjectVitals, null, null, whiteSourceComponent.getProjectToken(),whiteSourceComponent.getOrgName(),null,serverSettings);
            getEvaluationTimeStamp(libraryPolicyResult, projectVitalsObject,serverSettings);
            libraryPolicyResult.setCollectorItemId(whiteSourceComponent.getId());
            libraryPolicyResult.setTimestamp(System.currentTimeMillis());
            if(!CollectionUtils.isEmpty(alerts)){
                transform(libraryPolicyResult, alerts);
            }
        } catch (Exception e) {
            LOG.info("Exception occurred while calling getProjectAlerts for projectName="+whiteSourceComponent.getProjectName()+", Exception=" + e.getMessage());
        }
        return libraryPolicyResult;
    }


    @Override
    public List<WhiteSourceChangeRequest> getChangeRequestLog(String instanceUrl, String orgToken, String orgName, long historyTimestamp,WhiteSourceServerSettings serverSettings) {
        String startDateT = getTime(historyTimestamp);
        JSONObject jsonObject = makeRestCall(getApiBaseUrl(instanceUrl),Constants.RequestType.getChangesReport,orgToken,null,null,orgName,startDateT,serverSettings);
        JSONArray changes = (JSONArray) Objects.requireNonNull(jsonObject).get(Constants.CHANGES);
        List<WhiteSourceChangeRequest> changeRequests = new ArrayList<>();
        if(CollectionUtils.isEmpty(changes)){
            return changeRequests;
        }else{
            for (Object c : changes) {
                JSONObject change = (JSONObject) c;
                WhiteSourceChangeRequest whiteSourceChangeRequest = new WhiteSourceChangeRequest();
                whiteSourceChangeRequest.setScope(getStringValue(change, Constants.SCOPE));
                whiteSourceChangeRequest.setStartDateTime(timestamp(change, Constants.START_DATE_TIME));
                whiteSourceChangeRequest.setChangeAspect(getStringValue(change, Constants.CHANGE_ASPECT));
                whiteSourceChangeRequest.setChangeCategory(getStringValue(change, Constants.CHANGE_CATEGORY));
                whiteSourceChangeRequest.setChangeClass(getStringValue(change, Constants.CHANGE_CLASS));
                whiteSourceChangeRequest.setOrgName(getStringValue(change,Constants.ORG_NAME));
                whiteSourceChangeRequest.setProductName(getStringValue(change,Constants.PRODUCT_NAME));
                whiteSourceChangeRequest.setProjectName(getStringValue(change,Constants.PROJECT_NAME));
                whiteSourceChangeRequest.setScopeName(getStringValue(change, Constants.SCOPE_NAME));
                whiteSourceChangeRequest.setChangeScopeId(getLongValue(change, Constants.CHANGE_SCOPE_ID));
                whiteSourceChangeRequest.setOperator(getStringValue(change, Constants.OPERATOR));
                whiteSourceChangeRequest.setUserEmail(getStringValue(change, Constants.USER_EMAIL));
                whiteSourceChangeRequest.setBeforeChange(getListValue(change, Constants.BEFORE_CHANGE));
                whiteSourceChangeRequest.setAfterChange(getListValue(change, Constants.AFTER_CHANGE));
                changeRequests.add(whiteSourceChangeRequest);
            }
        }
        return changeRequests;
    }



    private JSONObject makeRestCall(String url, Constants.RequestType requestType, String orgToken, String productToken, String projectToken, String orgName, String startDateTime, WhiteSourceServerSettings serverSettings) {
        LOG.info("collecting analysis for orgName=" + orgName + " and requestType=" + requestType);
        JSONObject requestJSON = getRequest(requestType, orgToken, productToken, projectToken, startDateTime,serverSettings);
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
    public void getEvaluationTimeStamp(LibraryPolicyResult libraryPolicyResult, JSONObject projectVitalsObject, WhiteSourceServerSettings serverSettings) {
        JSONArray projectVitals = (JSONArray)  Objects.requireNonNull(projectVitalsObject).get(Constants.PROJECT_VITALS);
        if (!CollectionUtils.isEmpty(projectVitals)) {
            for (Object projectVital : projectVitals){
                JSONObject projectVitalObject = (JSONObject) projectVital;
                libraryPolicyResult.setEvaluationTimestamp(convertTimestamp(timeUtils(dateTime(projectVitalObject, Constants.LAST_UPDATED_DATE))));
                Long projectId = getLongValue(projectVitalObject, Constants.ID);
                libraryPolicyResult.setReportUrl(String.format(serverSettings.getDeeplink(),projectId));
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

    public LibraryPolicyResult getQualityData(CollectorItem component, LibraryPolicyResult libraryPolicyResult) {
        return libraryPolicyResultsRepository.findByCollectorItemIdAndEvaluationTimestamp(
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
            String description = (StringUtils.isNotEmpty(getStringValue(alert, Constants.DESCRIPTION))) ? getStringValue(alert, Constants.DESCRIPTION): "None";
            String componentName = getStringValue(library, Constants.FILENAME);
            // add threat for license
            JSONArray licenses = (JSONArray) Objects.requireNonNull(library).get(Constants.LICENSES);
            setAllLibraryLicensesAlerts(licenses, libraryPolicyResult, componentName, getDays(creationDate) + "", getLicenseThreatLevel(alertType, alertLevel, description),description);
            // add threat for Security vulns
            JSONObject vulns = (JSONObject) Objects.requireNonNull(alert).get(Constants.VULNERABILITY);
            if (!CollectionUtils.isEmpty(vulns)) {
                setSecurityVulns(vulns, libraryPolicyResult, componentName, getDays(creationDate) + "",description);
            }

        }
    }

    private long convertTimestamp(String timestamp) {
        long time = 0;
        if (StringUtils.isNotEmpty(timestamp)) {
            time = DateTimeFormat.forPattern(YYYY_MM_DD_HH_MM_SS).parseMillis(timestamp);
        }
        return time;
    }

    private String timeUtils(String sourceTime) {
        DateTimeFormatter inputFormat = DateTimeFormat.forPattern(YYYY_MM_DD_HH_MM_SS+" Z");
        DateTimeFormatter outputFormat = DateTimeFormat.forPattern(YYYY_MM_DD_HH_MM_SS);
        DateTime sourceDateTime = inputFormat.parseDateTime(sourceTime);
        DateTime destinationDateTime = sourceDateTime.toDateTime(DateTimeZone.forID(whiteSourceSettings.getZone()));
        return destinationDateTime.toString(outputFormat);
    }

    public String process(WhiteSourceRequest whiteSourceRequest) throws HygieiaException {
        JSONObject projectVital = (JSONObject) decodeJsonPayload(whiteSourceRequest.getProjectVitals());
        JSONArray alerts = (JSONArray) decodeJsonPayload(whiteSourceRequest.getAlerts());
        String orgName = whiteSourceRequest.getOrgName();
        String name =  (String) projectVital.get("name");
        String token =  (String) projectVital.get("token");
        String lastUpdatedDate = (String) projectVital.get("lastUpdatedDate");
        LibraryPolicyResult libraryPolicyResult = new LibraryPolicyResult();
        long timestamp = convertTimestamp(timeUtils(lastUpdatedDate));
        libraryPolicyResult.setEvaluationTimestamp(timestamp);
        CollectorItem collectorItem = collectorItemRepository.findByOrgNameAndProjectNameAndProjectToken(orgName, name, token);
        LOG.info("WhiteSourceRequest collecting  analysis for orgName= "+ orgName + " name : " + name + " token : " + token + " timestamp : "+ timestamp );
        if(collectorItem != null){
            LibraryPolicyResult lp = getQualityData(collectorItem,libraryPolicyResult);
            if(lp != null){
                LOG.info("Record already exist in LibraryPolicy " + lp.getId());
                return "Record already exist in LibraryPolicy  " + lp.getId();
            }
        }else{
            throw new HygieiaException("WhiteSource request : Invalid Whitesource project",HygieiaException.BAD_DATA);
        }
        transform(libraryPolicyResult, alerts);
        libraryPolicyResult.setTimestamp(System.currentTimeMillis());
        libraryPolicyResult.setCollectorItemId(collectorItem.getId());
        libraryPolicyResult.setBuildUrl(whiteSourceRequest.getBuildUrl());
        libraryPolicyResult =libraryPolicyResultsRepository.save(libraryPolicyResult);
        if(!collectorItem.isEnabled()){
            collectorItem.setEnabled(true);
            collectorItemRepository.save(collectorItem);
        }
        LOG.info("Successfully updated library policy result  "+ libraryPolicyResult.getId());
        return " Successfully updated library policy result " + libraryPolicyResult.getId();
    }

    public List<WhiteSourceComponent> getWhiteSourceComponents(String orgName, String productName, String projectName){
        Collector collector = collectorRepository.findByName(Constants.WHITE_SOURCE);
        Map<String, Object> options = getOptions(orgName, productName, projectName);
        Iterable<CollectorItem> collectorItems = collectorItemRepository.findAllByOptionMapAndCollectorIdsIn(options, Stream.of(collector.getId()).collect(Collectors.toList()));
        List<WhiteSourceComponent> whiteSourceComponents = new ArrayList<>();
        for (CollectorItem collectorItem : collectorItems) {
            whiteSourceComponents.add(buildWhiteSourceComponent(collectorItem));
        }
        return whiteSourceComponents;
    }

    private WhiteSourceComponent buildWhiteSourceComponent(CollectorItem collectorItem) {
        WhiteSourceComponent whiteSourceComponent = new WhiteSourceComponent();
        whiteSourceComponent.setOrgName((String)collectorItem.getOptions().get(Constants.ORG_NAME));
        whiteSourceComponent.setProductName((String)collectorItem.getOptions().get(Constants.PRODUCT_NAME));
        whiteSourceComponent.setProjectName((String)collectorItem.getOptions().get(Constants.PROJECT_NAME));
        whiteSourceComponent.setProjectToken((String)collectorItem.getOptions().get(Constants.PROJECT_TOKEN));
        whiteSourceComponent.setProductToken((String)collectorItem.getOptions().get(Constants.PRODUCT_TOKEN));
        whiteSourceComponent.setId(collectorItem.getId());
        whiteSourceComponent.setCollectorId(collectorItem.getCollectorId());
        whiteSourceComponent.setEnabled(collectorItem.isEnabled());
        whiteSourceComponent.setLastUpdated(collectorItem.getLastUpdated());
        return  whiteSourceComponent;
    }

    private Map<String, Object> getOptions(String orgName, String productName, String projectName) {
        Map<String, Object> options = new HashMap<>();
        options.put(Constants.ORG_NAME,orgName);
        options.put(Constants.PRODUCT_NAME,productName);
        options.put(Constants.PROJECT_NAME,projectName);
        return options;
    }


    private void setAllLibraryLicenses(JSONArray licenses, LibraryPolicyResult libraryPolicyResult, String componentName) {
        for (Object l : licenses) {
            libraryPolicyResult.addThreat(LibraryPolicyType.License, LibraryPolicyThreatLevel.High, LibraryPolicyThreatDisposition.Open, Constants.OPEN, componentName, Constants.ZERO, Constants.ZERO);
        }
    }

    private void setAllLibraryLicensesAlerts(JSONArray licenses, LibraryPolicyResult libraryPolicyResult, String componentName, String age, LibraryPolicyThreatLevel severity, String policyName) {
        libraryPolicyResult.addThreat(LibraryPolicyType.License, severity, LibraryPolicyThreatDisposition.Open, Constants.OPEN, componentName, age, Constants.ZERO, policyName);
    }

    private void setAllSecurityVulns(JSONArray vulns, LibraryPolicyResult libraryPolicyResult, String componentName) {
        for (Object vulnerability : vulns) {
            JSONObject vuln = (JSONObject) vulnerability;
            Double cvss3_score = toDouble(vuln, Constants.CVSS_3_SCORE);
            String cvss3_severity = getStringValue(vuln, Constants.CVSS_3_SEVERITY);
            libraryPolicyResult.addThreat(LibraryPolicyType.Security, LibraryPolicyThreatLevel.fromString(cvss3_severity), LibraryPolicyThreatDisposition.Open, Constants.OPEN, componentName, Constants.ZERO, cvss3_score.toString());
        }
    }

    private void setSecurityVulns(JSONObject vuln, LibraryPolicyResult libraryPolicyResult, String componentName, String age, String policyName) {
        libraryPolicyResult.addThreat(LibraryPolicyType.Security, LibraryPolicyThreatLevel.fromString(getSecurityVulnSeverity(vuln)), LibraryPolicyThreatDisposition.Open, Constants.OPEN, componentName, age, getScore(vuln),policyName);
    }

    private String getScore(JSONObject vuln) {
        Double score = toDouble(vuln, Constants.SCORE1);
        if (Objects.nonNull(score)) return score.toString();
        return Constants.ZERO;
    }

    private String getSecurityVulnSeverity(JSONObject vuln) {
        String severity = getStringValue(vuln, Constants.SEVERITY);
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
        && licensePolicyType.getDescriptions().stream().anyMatch(description::contains));
    }

    private String getApiBaseUrl(String instanceUrl) {
        return instanceUrl + API_URL;
    }


    private String getStringValue(JSONObject jsonObject, String key) {
        if (jsonObject == null || jsonObject.get(key) == null) return null;
        return (String) jsonObject.get(key);
    }

    private List getListValue(JSONObject jsonObject, String key) {
        if (jsonObject == null || jsonObject.get(key) == null) return null;
        JSONArray jsonArray = (JSONArray) jsonObject.get(key);
        List<String> list = new ArrayList<>();
        for (Object o : jsonArray) {
            list.add((String)o);
        }
        return list;
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


    private JSONObject getRequest(Constants.RequestType requestType, String orgToken, String productToken, String projectToken, String startDateTime, WhiteSourceServerSettings serverSettings) {
        JSONObject requestJSON = new JSONObject();
        requestJSON.put(Constants.REQUEST_TYPE, requestType.toString());
        requestJSON.put(Constants.USER_KEY, serverSettings.getUserKey());
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
                return new SimpleDateFormat(YYYY_MM_DD_HH_MM_SS).parse(obj.toString()).getTime();
            } catch (java.text.ParseException e) {
                LOG.error(obj + " is not in expected format " + YYYY_MM_DD_HH_MM_SS, e);
            }
        }
        return 0;
    }

    private String dateTime(JSONObject json, String key) {
        Object obj = json.get(key);
        if (obj != null) {
           return obj.toString();
        }
        return null;
    }

    private String getTime(long timestamp){
        DateFormat format = new SimpleDateFormat(YYYY_MM_DD_HH_MM_SS);
        return format.format(new Date(timestamp));
    }

    boolean processRecord(String value, String pattern) {
        if(StringUtils.isEmpty(pattern)) return true;
        String matchPattern = "^[" + pattern + "]+.*$";
        return (value.matches(matchPattern));
    }

    String getGithubOrgname(String productName) {
        String num ="^[\\$GH_]+[\\$0-9]+[\\$_]+.*$";
        String no_num = "^[\\$GH]+[\\$_]+.*$";
        if(productName.matches(num)) {
            String[] split = productName.split("_",3);
            if(split.length==3) {
                return split[2];
            }
        } else if(productName.matches(no_num)) {
            return StringUtils.substringAfter(productName,"GH_");
        }
        return "";
    }

}
