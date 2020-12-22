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
import com.capitalone.dashboard.model.WhiteSourceProjectVital;
import com.capitalone.dashboard.model.WhiteSourceRequest;
import com.capitalone.dashboard.model.WhiteSourceServerSettings;
import com.capitalone.dashboard.model.WhitesourceOrg;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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

    /**
     * Get Whitesource Org Details
     * @param serverSettings Whitesource Server Setting
     * @return Whitesource Org
     * @throws HygieiaException Hygieia Exception
     */
    @Override
    public WhitesourceOrg getOrgDetails(WhiteSourceServerSettings serverSettings) throws HygieiaException {
        WhitesourceOrg whitesourceOrg = new WhitesourceOrg(serverSettings.getOrgToken(), "");
        try {
            JSONObject jsonObject = makeRestCall(
                    Constants.RequestType.getOrganizationDetails, whitesourceOrg,
                    null, null, null, serverSettings.getOrgToken(), serverSettings);
            String name = (String) jsonObject.get(Constants.ORG_NAME);
            return new WhitesourceOrg(name, whitesourceOrg.getToken());
            //TODO: Refactor Exception Handling
        } catch (Exception e) {
            throw new HygieiaException("Exception occurred while calling getOrgDetails", e.getCause(), HygieiaException.BAD_DATA);
        }
    }

    /**
     * Gets products for a whitesource org
     * @param whitesourceOrg whitesource org
     * @param serverSettings whitesource server settings
     * @return List of Whitesource products
     * @throws HygieiaException Hygieia Exception
     */
    @Override
    public List<WhiteSourceProduct> getProducts(WhitesourceOrg whitesourceOrg, WhiteSourceServerSettings serverSettings) throws HygieiaException {
        long timeToGetProducts = System.currentTimeMillis();
        List<WhiteSourceProduct> whiteSourceProducts = new ArrayList<>();
        try {
            JSONObject jsonObject = makeRestCall( Constants.RequestType.getAllProducts, whitesourceOrg, null, null, null, null, serverSettings);
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
            //TODO: Refactor Exception Handling
        } catch (Exception e) {
            throw new HygieiaException("Exception occurred while retrieving getAllProducts for orgName=" + whitesourceOrg.getName(), e.getCause(), HygieiaException.BAD_DATA);
        }
        timeToGetProducts = System.currentTimeMillis() - timeToGetProducts;
        LOG.info("WhitesourceClient: Time to get products =" + timeToGetProducts);
        return whiteSourceProducts;
    }

    /**
     * Gets all projects for a given product
     * @param whitesourceOrg Whitesource Org
     * @param product Whitesource Product
     * @param serverSettings Whitesource Server Settings
     * @return List of Whitesource Component
     */
    @Override
    public List<WhiteSourceComponent> getAllProjectsForProduct(WhitesourceOrg whitesourceOrg, WhiteSourceProduct product, WhiteSourceServerSettings serverSettings) {
        List<WhiteSourceComponent> whiteSourceProjects = new ArrayList<>();
        try {
            JSONObject jsonObject = makeRestCall(Constants.RequestType.getAllProjects, whitesourceOrg, product.getProductToken(), null, null, null, serverSettings);
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
                whiteSourceProject.setOrgName(whitesourceOrg.getName());
                whiteSourceProjects.add(whiteSourceProject);
            }
            //TODO: Refactor Exception Handling
        } catch (Exception e) {
            LOG.error("Exception occurred while retrieving getAllProjectsForProduct for productName=" + product.getProductName() + ", Exception=" + e.getMessage());
        }
        return whiteSourceProjects;
    }


    /**
     * Gets a set of project tokens that are in Org Alerts By Policy Violations
     * @param whitesourceOrg Whitesource Org
     * @param historyTimestamp Time to go back
     * @param serverSettings Whitesource Server Setting
     * @return Set of Project Tokens
     */
    @Override
    public Set<String> getAffectedProjectsForOrganization(WhitesourceOrg whitesourceOrg, long historyTimestamp, WhiteSourceServerSettings serverSettings) {
        Set<String> affectedProjectTokens = getAffectedProjectTokens(whitesourceOrg, Constants.REJECTED_BY_POLICY, historyTimestamp, serverSettings);
        affectedProjectTokens.addAll(getAffectedProjectTokens(whitesourceOrg, Constants.SECURITY_VULNERABILITY, historyTimestamp, serverSettings));
        return affectedProjectTokens;
    }

    /**
     * Gets a set of project tokens By Policy Violations
     * @param whitesourceOrg Whitesource Org
     * @param alertType Alert Type
     * @param historyTimestamp Time to go back
     * @param serverSettings Whitesource Server Setting
     * @return Set of Project Tokens
     */
    private Set<String> getAffectedProjectTokens(WhitesourceOrg whitesourceOrg, String alertType, long historyTimestamp, WhiteSourceServerSettings serverSettings) {
        Set<String> affectedProjectTokens = new HashSet<>();
        String startDateTime = getTime(historyTimestamp);
        try {
            // Get policy alerts
            JSONObject jsonObject = makeRestCall(Constants.RequestType.getOrganizationAlertsByType, whitesourceOrg, null, null, alertType, startDateTime, serverSettings);
            JSONArray alerts = (JSONArray) Objects.requireNonNull(jsonObject).get(Constants.ALERTS);
            if (!CollectionUtils.isEmpty(alerts)) {
                for (Object a : alerts) {
                    JSONObject alert = (JSONObject) a;
                    String projectToken = getStringValue(alert, Constants.PROJECT_TOKEN);
                    affectedProjectTokens.add(projectToken);
                }
            }
            //TODO: Refactor Exception Handling
        } catch (Exception e) {
            LOG.info("Exception occurred while calling getAffectedProjectsForOrganization for orgToken =" + whitesourceOrg.getToken() + ", Exception=" + e.getMessage());
        }
        return affectedProjectTokens;
    }

    /**
     * Gets product alerts
     * @param whitesourceOrg Whitesource Org
     * @param productToken Product Token
     * @param projectVitalMap Product Vitals Map
     * @param serverSettings Whitesource Server Setting
     * @return Map or Project Token and Corresponding Library Policy Result
     */
    @Override
    public Map<String, LibraryPolicyResult> getProductAlerts(WhitesourceOrg whitesourceOrg, String productToken, Map<String, WhiteSourceProjectVital> projectVitalMap, WhiteSourceServerSettings serverSettings) {
        Map<String, LibraryPolicyResult> libraryPolicyResultMap = new HashMap<>();
        try {
            JSONObject jsonObject = makeRestCall(Constants.RequestType.getProductAlerts, whitesourceOrg, productToken, null, null, null, serverSettings);
            JSONArray alerts = (JSONArray) Objects.requireNonNull(jsonObject).get(Constants.ALERTS);
            if (!CollectionUtils.isEmpty(alerts)) {
                libraryPolicyResultMap = transformProductAlerts(whitesourceOrg, alerts, projectVitalMap, serverSettings);
            }
            //TODO: Refactor Exception Handling
        } catch (Exception e) {
            LOG.info("Exception occurred while calling getProductAlerts for productToken =" + productToken + ", Exception=" + e.getMessage());
        }
        return libraryPolicyResultMap;
    }

    /**
     * Gets Project Alerts
     * @param whiteSourceComponent Whitesource Component
     * @param projectVital Project Vitals Map
     * @param serverSettings Whitesource Server Setting
     * @return Library Policy Result
     */

    @Override
    public LibraryPolicyResult getProjectAlerts(WhiteSourceComponent whiteSourceComponent, WhiteSourceProjectVital projectVital, WhiteSourceServerSettings serverSettings) {
        LibraryPolicyResult libraryPolicyResult = new LibraryPolicyResult();
        try {
            JSONObject jsonObject = makeRestCall(Constants.RequestType.getProjectAlerts, null, null, whiteSourceComponent.getProjectToken(), null, null, serverSettings);
            JSONArray alerts = (JSONArray) Objects.requireNonNull(jsonObject).get(Constants.ALERTS);
            if (projectVital == null) {
                JSONObject projectVitalsObject = makeRestCall(Constants.RequestType.getProjectVitals, null, null, whiteSourceComponent.getProjectToken(), null, null, serverSettings);
                getEvaluationTimeStamp(libraryPolicyResult, projectVitalsObject, serverSettings);
            } else {
                getEvaluationTimeStamp(libraryPolicyResult, projectVital, serverSettings);
            }
            libraryPolicyResult.setCollectorItemId(whiteSourceComponent.getId());
            if (!CollectionUtils.isEmpty(alerts)) {
                transformAlerts(libraryPolicyResult, alerts);
            }
        } catch (Exception e) {
            LOG.info("Exception occurred while calling getProjectAlerts for projectName=" + whiteSourceComponent.getProjectName() + ", Exception=" + e.getMessage());
        }
        return libraryPolicyResult;
    }


    /**
     * Gets Orgnization Change Request Log
     * @param whitesourceOrg Whitesource Org
     * @param historyTimestamp Start time
     * @param serverSettings Whitesource Server Settings
     * @return List of Whitesource Change Request
     */
    @Override
    public List<WhiteSourceChangeRequest> getChangeRequestLog(WhitesourceOrg whitesourceOrg, long historyTimestamp, WhiteSourceServerSettings serverSettings) {
        long timeGetChangeRequestLog = System.currentTimeMillis();
        String startDateT = getTime(historyTimestamp);
        JSONObject jsonObject = makeRestCall(Constants.RequestType.getChangesReport, whitesourceOrg, null, null, null, startDateT, serverSettings);
        JSONArray changes = (JSONArray) Objects.requireNonNull(jsonObject).get(Constants.CHANGES);
        List<WhiteSourceChangeRequest> changeRequests = new ArrayList<>();
        if (CollectionUtils.isEmpty(changes)) {
            return changeRequests;
        } else {
            for (Object c : changes) {
                JSONObject change = (JSONObject) c;
                WhiteSourceChangeRequest whiteSourceChangeRequest = new WhiteSourceChangeRequest();
                whiteSourceChangeRequest.setScope(getStringValue(change, Constants.SCOPE));
                whiteSourceChangeRequest.setStartDateTime(timestamp(change, Constants.START_DATE_TIME));
                whiteSourceChangeRequest.setChangeAspect(getStringValue(change, Constants.CHANGE_ASPECT));
                whiteSourceChangeRequest.setChangeCategory(getStringValue(change, Constants.CHANGE_CATEGORY));
                whiteSourceChangeRequest.setChangeClass(getStringValue(change, Constants.CHANGE_CLASS));
                whiteSourceChangeRequest.setOrgName(getStringValue(change, Constants.ORG_NAME));
                whiteSourceChangeRequest.setProductName(getStringValue(change, Constants.PRODUCT_NAME));
                whiteSourceChangeRequest.setProjectName(getStringValue(change, Constants.PROJECT_NAME));
                whiteSourceChangeRequest.setScopeName(getStringValue(change, Constants.SCOPE_NAME));
                whiteSourceChangeRequest.setChangeScopeId(getLongValue(change, Constants.CHANGE_SCOPE_ID));
                whiteSourceChangeRequest.setOperator(getStringValue(change, Constants.OPERATOR));
                whiteSourceChangeRequest.setUserEmail(getStringValue(change, Constants.USER_EMAIL));
                whiteSourceChangeRequest.setBeforeChange(getListValue(change, Constants.BEFORE_CHANGE));
                whiteSourceChangeRequest.setAfterChange(getListValue(change, Constants.AFTER_CHANGE));
                changeRequests.add(whiteSourceChangeRequest);
            }
        }
        timeGetChangeRequestLog = System.currentTimeMillis() - timeGetChangeRequestLog;
        LOG.info("WhitesourceCilent: Time to get all change request logs: " + timeGetChangeRequestLog);
        return changeRequests;
    }

    /**
     * Gets org level project vitals
     * @param whitesourceOrg Whitesource Org
     * @param serverSettings Whitesource Server Setting
     * @return Map of project token and project vital
     */
    @Override
    public Map<String, WhiteSourceProjectVital> getOrgProjectVitals(WhitesourceOrg whitesourceOrg, WhiteSourceServerSettings serverSettings) {
        long timeGetProjectVitals = System.currentTimeMillis();
        Map<String, WhiteSourceProjectVital> projectVitalMap = new HashMap<>();
        JSONObject jsonObject = makeRestCall(Constants.RequestType.getOrganizationProjectVitals, whitesourceOrg, null, null, null, null, serverSettings);
        JSONArray vitals = (JSONArray) Objects.requireNonNull(jsonObject).get(Constants.PROJECT_VITALS);
        for (Object v : vitals) {
            JSONObject vital = (JSONObject) v;
            WhiteSourceProjectVital whiteSourceProjectVital = new WhiteSourceProjectVital();
            whiteSourceProjectVital.setName(getStringValue(vital, Constants.NAME));
            whiteSourceProjectVital.setId(getLongValue(vital, Constants.ID));
            String token = getStringValue(vital, Constants.TOKEN);
            whiteSourceProjectVital.setToken(token);
            whiteSourceProjectVital.setCreationDate(convertTimestamp(timeUtils(dateTime(vital, Constants.LAST_UPDATED_DATE))));
            whiteSourceProjectVital.setLastUpdateDate(convertTimestamp(timeUtils(dateTime(vital, Constants.CREATIONDATE))));
            projectVitalMap.put(token, whiteSourceProjectVital);
        }
        timeGetProjectVitals = System.currentTimeMillis() - timeGetProjectVitals;
        LOG.info("WhitesourceClient: Time to get all project vitals: " + timeGetProjectVitals);
        return projectVitalMap;
    }

    ////////////////////////////////////////////       Helper and private methods below /////////////////////////////////////////


    /**
     * Transforms all product alerts into corresponding Library Policy Results and returns in a Map of Project Token
     * and Library Policy Result
     * @param whitesourceOrg Whitesource Org
     * @param alerts
     * @param projectVitalMap
     * @param serverSettings
     * @return
     */
    private Map<String, LibraryPolicyResult> transformProductAlerts(WhitesourceOrg whitesourceOrg, JSONArray alerts, Map<String, WhiteSourceProjectVital> projectVitalMap, WhiteSourceServerSettings serverSettings) {
        Map<String, LibraryPolicyResult> libraryPolicyResultMap = new HashMap<>();
        for (Object alert : alerts) {
            String projectToken = getStringValue((JSONObject) alert, Constants.PROJECT_TOKEN);
            LibraryPolicyResult libraryPolicyResult = libraryPolicyResultMap.get(projectToken);
            translateAlertJSON((JSONObject) alert, libraryPolicyResult);
            WhiteSourceProjectVital projectVital = projectVitalMap.get(projectToken);
            if (projectVital == null) {
                JSONObject projectVitalsObject = makeRestCall(Constants.RequestType.getProjectVitals, whitesourceOrg, null, projectToken, null, null, serverSettings);
                getEvaluationTimeStamp(libraryPolicyResult, projectVitalsObject, serverSettings);
            } else {
                getEvaluationTimeStamp(libraryPolicyResult, projectVital, serverSettings);
            }
            libraryPolicyResultMap.put(projectToken, libraryPolicyResult);
        }
        return libraryPolicyResultMap;
    }


    /**
     * Transforms alerts into Library Policy Fields
     * @param libraryPolicyResult Library Policy Result that needs to be enriched with alerts data
     * @param alerts project alerts
     */
    private void transformAlerts(LibraryPolicyResult libraryPolicyResult, JSONArray alerts) {
        for (Object alert : alerts) {
            translateAlertJSON((JSONObject) alert, libraryPolicyResult);
        }
    }


    /**
     * Helper method to Translates alert json object
     * @param alert JSONObject of project alert
     * @param libraryPolicyResult Library Policy Result that need to be enriched
     */
    private void translateAlertJSON (JSONObject alert, LibraryPolicyResult libraryPolicyResult) {
        if (libraryPolicyResult == null) {
            libraryPolicyResult = new LibraryPolicyResult();
        }
        String alertType = getStringValue(alert, Constants.TYPE);
        JSONObject library = (JSONObject) Objects.requireNonNull(alert).get(Constants.LIBRARY);
        String creationDate = getStringValue(alert, Constants.CREATION_DATE);
        String description = (StringUtils.isNotEmpty(getStringValue(alert, Constants.DESCRIPTION))) ? getStringValue(alert, Constants.DESCRIPTION) : "None";
        String componentName = getStringValue(library, Constants.FILENAME);
        // add threat for license
        JSONArray licenses = (JSONArray) Objects.requireNonNull(library).get(Constants.LICENSES);
        setAllLibraryLicensesAlerts(licenses, libraryPolicyResult, componentName, String.valueOf(getDays(creationDate)), getLicenseThreatLevel(alertType, description), description);
        // add threat for Security vulns
        JSONObject vulns = (JSONObject) Objects.requireNonNull(alert).get(Constants.VULNERABILITY);
        if (!CollectionUtils.isEmpty(vulns)) {
            setSecurityVulns(vulns, libraryPolicyResult, componentName, String.valueOf(getDays(creationDate)), description);
        }
        libraryPolicyResult.setTimestamp(System.currentTimeMillis());
    }

    /**
     * Generic helper method to execute rest call
     * @param requestType Request Type
     * @param whitesourceOrg Whitesource Org
     * @param productToken product token
     * @param projectToken project token
     * @param alertType alert type
     * @param startDateTime start date time
     * @param serverSettings server settings
     * @return JSON Object
     */
    private JSONObject makeRestCall(Constants.RequestType requestType, WhitesourceOrg whitesourceOrg, String productToken,
                                    String projectToken, String alertType, String startDateTime,
                                    WhiteSourceServerSettings serverSettings) {
        LOG.info("collecting analysis for orgName=" + whitesourceOrg.getName() + " and requestType=" + requestType);
        JSONObject requestJSON = getRequest(requestType, whitesourceOrg.getToken(), productToken, projectToken, startDateTime, serverSettings, alertType);
        JSONParser parser = new JSONParser();
        String orgNameToLog = whitesourceOrg == null ? "UNKNOWN" : whitesourceOrg.getName();
        try {
            ResponseEntity<String> response = restClient.makeRestCallPost(getApiBaseUrl(serverSettings.getInstanceUrl()), new HttpHeaders(), requestJSON);
            if ((response == null) || (response.toString().isEmpty())) return null;
            return (JSONObject) parser.parse(response.getBody());
        } catch (Exception e) {
            LOG.error("Exception occurred while calling REST for orgName=" + orgNameToLog + " and requestType=" + requestType + ", Exception=" + e.getMessage());
        }
        return null;
    }


    // Gets project evaluation time stamp
    public void getEvaluationTimeStamp(LibraryPolicyResult libraryPolicyResult, JSONObject projectVitalsObject, WhiteSourceServerSettings serverSettings) {
        JSONArray projectVitals = (JSONArray) Objects.requireNonNull(projectVitalsObject).get(Constants.PROJECT_VITALS);
        if (!CollectionUtils.isEmpty(projectVitals)) {
            for (Object projectVital : projectVitals) {
                JSONObject projectVitalObject = (JSONObject) projectVital;
                libraryPolicyResult.setEvaluationTimestamp(convertTimestamp(timeUtils(dateTime(projectVitalObject, Constants.LAST_UPDATED_DATE))));
                Long projectId = getLongValue(projectVitalObject, Constants.ID);
                libraryPolicyResult.setReportUrl(String.format(serverSettings.getDeeplink(), projectId));
            }
        }
    }

    // Gets evaluation time stamp
    public static void getEvaluationTimeStamp(LibraryPolicyResult libraryPolicyResult, WhiteSourceProjectVital projectVital, WhiteSourceServerSettings serverSettings) {
        libraryPolicyResult.setEvaluationTimestamp(projectVital.getLastUpdateDate());
        Long projectId = projectVital.getId();
        libraryPolicyResult.setReportUrl(String.format(serverSettings.getDeeplink(), projectId));
    }


    // Decodes json payload
    private static Object decodeJsonPayload(String payload) throws HygieiaException {
        if (payload == null || StringUtils.isEmpty(payload)) {
            throw new HygieiaException("WhiteSource request is not a valid json.", HygieiaException.JSON_FORMAT_ERROR);
        }
        byte[] decodedBytes = Base64.getDecoder().decode(payload);
        String decodedPayload = new String(decodedBytes);
        JSONParser parser = new JSONParser();
        Object obj = null;
        try {
            obj = parser.parse(decodedPayload);

        } catch (ParseException e) {
            e.printStackTrace();
        }
        return obj;
    }


    // Get library Policy Data from repository
    public LibraryPolicyResult getLibraryPolicyData(CollectorItem component, LibraryPolicyResult libraryPolicyResult) {
        return libraryPolicyResultsRepository.findByCollectorItemIdAndEvaluationTimestamp(
                component.getId(), libraryPolicyResult.getEvaluationTimestamp());
    }



    // Convert timestamp to whitesource format
    private  long convertTimestamp(String timestamp) {
        long time = 0;
        if (StringUtils.isNotEmpty(timestamp)) {
            time = DateTimeFormat.forPattern(YYYY_MM_DD_HH_MM_SS).withZone(DateTimeZone.forID(whiteSourceSettings.getZone())).parseMillis(timestamp);
        }
        return time;
    }

    // Helper method
    private String timeUtils(String sourceTime) {
        DateTimeFormatter inputFormat = DateTimeFormat.forPattern(YYYY_MM_DD_HH_MM_SS + " Z");
        DateTimeFormatter outputFormat = DateTimeFormat.forPattern(YYYY_MM_DD_HH_MM_SS);
        DateTime sourceDateTime = inputFormat.parseDateTime(sourceTime);
        DateTime destinationDateTime = sourceDateTime.toDateTime(DateTimeZone.forID(whiteSourceSettings.getZone()));
        return destinationDateTime.toString(outputFormat);
    }

    public String process(WhiteSourceRequest whiteSourceRequest) throws HygieiaException {
        JSONObject projectVital = (JSONObject) decodeJsonPayload(whiteSourceRequest.getProjectVitals());
        JSONArray alerts = (JSONArray) decodeJsonPayload(whiteSourceRequest.getAlerts());
        String orgName = whiteSourceRequest.getOrgName();
        if (projectVital == null) {
            throw new HygieiaException("WhiteSource request : Project Vital is null for project", HygieiaException.BAD_DATA);
        }
        String name = (String) projectVital.get("name");
        String token = (String) projectVital.get("token");
        String lastUpdatedDate = (String) projectVital.get("lastUpdatedDate");
        LibraryPolicyResult libraryPolicyResult = new LibraryPolicyResult();
        long timestamp = convertTimestamp(timeUtils(lastUpdatedDate));
        libraryPolicyResult.setEvaluationTimestamp(timestamp);
        CollectorItem collectorItem = collectorItemRepository.findByOrgNameAndProjectNameAndProjectToken(orgName, name, token);
        LOG.info("WhiteSourceRequest collecting  analysis for orgName= " + orgName + " name : " + name + " token : " + token + " timestamp : " + timestamp);
        if (collectorItem != null) {
            LibraryPolicyResult lp = getLibraryPolicyData(collectorItem, libraryPolicyResult);
            if (lp != null) {
                LOG.info("Record already exist in LibraryPolicy " + lp.getId());
                return "Record already exist in LibraryPolicy  " + lp.getId();
            }
        } else {
            throw new HygieiaException("WhiteSource request : Invalid Whitesource project", HygieiaException.BAD_DATA);
        }
        transformAlerts(libraryPolicyResult, alerts);
        libraryPolicyResult.setCollectorItemId(collectorItem.getId());
        libraryPolicyResult.setBuildUrl(whiteSourceRequest.getBuildUrl());
        libraryPolicyResult = libraryPolicyResultsRepository.save(libraryPolicyResult);
        if (!collectorItem.isEnabled()) {
            collectorItem.setEnabled(true);
            collectorItemRepository.save(collectorItem);
        }
        LOG.info("Successfully updated library policy result  " + libraryPolicyResult.getId());
        return " Successfully updated library policy result " + libraryPolicyResult.getId();
    }

    public List<WhiteSourceComponent> getWhiteSourceComponents(String orgName, String productName, String projectName) {
        Collector collector = collectorRepository.findByName(Constants.WHITE_SOURCE);
        Map<String, Object> options = getOptions(orgName, productName, projectName);
        Iterable<CollectorItem> collectorItems = collectorItemRepository.findAllByOptionMapAndCollectorIdsIn(options, Stream.of(collector.getId()).collect(Collectors.toList()));
        List<WhiteSourceComponent> whiteSourceComponents = new ArrayList<>();
        for (CollectorItem collectorItem : collectorItems) {
            whiteSourceComponents.add(buildWhiteSourceComponent(collectorItem));
        }
        return whiteSourceComponents;
    }

    private static WhiteSourceComponent buildWhiteSourceComponent(CollectorItem collectorItem) {
        WhiteSourceComponent whiteSourceComponent = new WhiteSourceComponent();
        whiteSourceComponent.setOrgName((String) collectorItem.getOptions().get(Constants.ORG_NAME));
        whiteSourceComponent.setProductName((String) collectorItem.getOptions().get(Constants.PRODUCT_NAME));
        whiteSourceComponent.setProjectName((String) collectorItem.getOptions().get(Constants.PROJECT_NAME));
        whiteSourceComponent.setProjectToken((String) collectorItem.getOptions().get(Constants.PROJECT_TOKEN));
        whiteSourceComponent.setProductToken((String) collectorItem.getOptions().get(Constants.PRODUCT_TOKEN));
        whiteSourceComponent.setId(collectorItem.getId());
        whiteSourceComponent.setCollectorId(collectorItem.getCollectorId());
        whiteSourceComponent.setEnabled(collectorItem.isEnabled());
        whiteSourceComponent.setLastUpdated(collectorItem.getLastUpdated());
        return whiteSourceComponent;
    }

    private static Map<String, Object> getOptions(String orgName, String productName, String projectName) {
        Map<String, Object> options = new HashMap<>();
        options.put(Constants.ORG_NAME, orgName);
        options.put(Constants.PRODUCT_NAME, productName);
        options.put(Constants.PROJECT_NAME, projectName);
        return options;
    }


    private static void setAllLibraryLicensesAlerts(JSONArray licenses, LibraryPolicyResult libraryPolicyResult, String componentName, String age, LibraryPolicyThreatLevel severity, String policyName) {
        libraryPolicyResult.addThreat(LibraryPolicyType.License, severity, LibraryPolicyThreatDisposition.Open, Constants.OPEN, componentName, age, Constants.ZERO, policyName);
    }

    private static void setSecurityVulns(JSONObject vuln, LibraryPolicyResult libraryPolicyResult, String componentName, String age, String policyName) {
        libraryPolicyResult.addThreat(LibraryPolicyType.Security, LibraryPolicyThreatLevel.fromString(getSecurityVulnSeverity(vuln)), LibraryPolicyThreatDisposition.Open, Constants.OPEN, componentName, age, getScore(vuln), policyName);
    }

    private static String getScore(JSONObject vuln) {
        Double score = toDouble(vuln, Constants.SCORE1);
        if (Objects.nonNull(score)) return score.toString();
        return Constants.ZERO;
    }

    private static String getSecurityVulnSeverity(JSONObject vuln) {
        String severity = getStringValue(vuln, Constants.SEVERITY);
        if (Objects.nonNull(severity)) return severity;
        return Constants.NONE;
    }

    private LibraryPolicyThreatLevel getLicenseThreatLevel(String alertType, String description) {
        if (!CollectionUtils.isEmpty(whiteSourceSettings.getCriticalLicensePolicyTypes()) && getLicenseSeverity(whiteSourceSettings.getCriticalLicensePolicyTypes(), alertType, description)) {
            return LibraryPolicyThreatLevel.Critical;
        }
        if (!CollectionUtils.isEmpty(whiteSourceSettings.getHighLicensePolicyTypes()) && getLicenseSeverity(whiteSourceSettings.getHighLicensePolicyTypes(), alertType, description)) {
            return LibraryPolicyThreatLevel.High;
        }
        if (!CollectionUtils.isEmpty(whiteSourceSettings.getMediumLicensePolicyTypes()) && getLicenseSeverity(whiteSourceSettings.getMediumLicensePolicyTypes(), alertType, description)) {
            return LibraryPolicyThreatLevel.Medium;
        }
        if (!CollectionUtils.isEmpty(whiteSourceSettings.getLowLicensePolicyTypes()) && getLicenseSeverity(whiteSourceSettings.getLowLicensePolicyTypes(), alertType, description)) {
            return LibraryPolicyThreatLevel.Low;
        }
        return LibraryPolicyThreatLevel.None;
    }

    private static boolean getLicenseSeverity(List<LicensePolicyType> licensePolicyTypes, String alertType, String description) {
        return licensePolicyTypes.stream().anyMatch(licensePolicyType -> licensePolicyType.getPolicyName().equalsIgnoreCase(alertType)
                && licensePolicyType.getDescriptions().stream().anyMatch(description::contains));
    }

    private static String getApiBaseUrl(String instanceUrl) {
        return instanceUrl + API_URL;
    }


    private static String getStringValue(JSONObject jsonObject, String key) {
        if (jsonObject == null || jsonObject.get(key) == null) return null;
        return (String) jsonObject.get(key);
    }

    private static List<String> getListValue(JSONObject jsonObject, String key) {
        if (jsonObject == null || jsonObject.get(key) == null) return null;
        JSONArray jsonArray = (JSONArray) jsonObject.get(key);
        List<String> list = new ArrayList<>();
        for (Object o : jsonArray) {
            list.add((String) o);
        }
        return list;
    }

    private static Long getLongValue(JSONObject jsonObject, String key) {
        if (jsonObject == null || jsonObject.get(key) == null) return null;
        return (Long) jsonObject.get(key);
    }

    private static Double toDouble(JSONObject json, String key) {
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


    private static JSONObject getRequest(Constants.RequestType requestType, String orgToken, String productToken, String projectToken, String startDateTime, WhiteSourceServerSettings serverSettings, String alertType) {
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
            case getProductAlerts:
                requestJSON.put(Constants.PRODUCT_TOKEN, productToken);
                return requestJSON;
            case getAllProducts:
            case getOrganizationDetails:
            case getOrganizationProjectVitals:
                requestJSON.put(Constants.ORG_TOKEN, orgToken);
                return requestJSON;
            case getChangesReport:
                requestJSON.put(Constants.START_DATE_TIME, startDateTime);
                requestJSON.put(Constants.ORG_TOKEN, orgToken);
                requestJSON.put(Constants.SCOPE, Constants.PROJECT);
            case getOrganizationAlertsByType:
                requestJSON.put(Constants.ORG_TOKEN, orgToken);
                requestJSON.put(Constants.ALERT_TYPE, alertType);
                requestJSON.put(Constants.FROM_DATE, startDateTime);
            default:
                return requestJSON;
        }
    }

    private static long timestamp(JSONObject json, String key) {
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

    private static String dateTime(JSONObject json, String key) {
        Object obj = json.get(key);
        if (obj != null) {
            return obj.toString();
        }
        return null;
    }

    private static String getTime(long timestamp) {
        DateFormat format = new SimpleDateFormat(YYYY_MM_DD_HH_MM_SS);
        return format.format(new Date(timestamp));
    }

}
