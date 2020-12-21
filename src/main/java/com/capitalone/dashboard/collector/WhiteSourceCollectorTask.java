package com.capitalone.dashboard.collector;


import com.capitalone.dashboard.misc.HygieiaException;
import com.capitalone.dashboard.model.CollectionError;
import com.capitalone.dashboard.model.Count;
import com.capitalone.dashboard.model.LibraryPolicyReference;
import com.capitalone.dashboard.model.LibraryPolicyResult;
import com.capitalone.dashboard.model.WhiteSourceChangeRequest;
import com.capitalone.dashboard.model.WhiteSourceCollector;
import com.capitalone.dashboard.model.WhiteSourceComponent;
import com.capitalone.dashboard.model.WhiteSourceProduct;
import com.capitalone.dashboard.model.WhiteSourceProjectVital;
import com.capitalone.dashboard.model.WhiteSourceServerSettings;
import com.capitalone.dashboard.repository.BaseCollectorRepository;
import com.capitalone.dashboard.repository.LibraryPolicyResultsRepository;
import com.capitalone.dashboard.repository.LibraryReferenceRepository;
import com.capitalone.dashboard.repository.WhiteSourceCollectorRepository;
import com.capitalone.dashboard.repository.WhiteSourceComponentRepository;
import com.capitalone.dashboard.repository.WhiteSourceCustomComponentRepository;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;


@Component
public class WhiteSourceCollectorTask extends CollectorTask<WhiteSourceCollector> {
    private static final Log LOG = LogFactory.getLog(WhiteSourceCollectorTask.class);
    private final WhiteSourceCollectorRepository whiteSourceCollectorRepository;
    private final WhiteSourceComponentRepository whiteSourceComponentRepository;
    private final LibraryPolicyResultsRepository libraryPolicyResultsRepository;
    private final WhiteSourceClient whiteSourceClient;
    private final WhiteSourceSettings whiteSourceSettings;
    private final WhiteSourceCustomComponentRepository whiteSourceCustomComponentRepository;
    private final LibraryReferenceRepository libraryReferenceRepository;



    @Autowired
    public WhiteSourceCollectorTask(TaskScheduler taskScheduler,
                                    WhiteSourceCollectorRepository whiteSourceCollectorRepository,
                                    WhiteSourceComponentRepository whiteSourceComponentRepository,
                                    LibraryPolicyResultsRepository libraryPolicyResultsRepository,
                                    WhiteSourceClient whiteSourceClient,
                                    WhiteSourceSettings whiteSourceSettings,
                                    WhiteSourceCustomComponentRepository whiteSourceCustomComponentRepository,
                                    LibraryReferenceRepository libraryReferenceRepository) {
        super(taskScheduler, Constants.WHITE_SOURCE);
        this.whiteSourceCollectorRepository = whiteSourceCollectorRepository;
        this.whiteSourceComponentRepository = whiteSourceComponentRepository;
        this.libraryPolicyResultsRepository = libraryPolicyResultsRepository;
        this.whiteSourceClient = whiteSourceClient;
        this.whiteSourceSettings = whiteSourceSettings;
        this.whiteSourceCustomComponentRepository = whiteSourceCustomComponentRepository;
        this.libraryReferenceRepository = libraryReferenceRepository;
    }

    @Override
    public WhiteSourceCollector getCollector() {
        return WhiteSourceCollector.prototype(whiteSourceSettings.getServers());
    }

    @Override
    public BaseCollectorRepository<WhiteSourceCollector> getCollectorRepository() {
        return whiteSourceCollectorRepository;
    }

    @Override
    public String getCron() {
        return whiteSourceSettings.getCron();
    }

    public long getWaitTime() {
        return whiteSourceSettings.getWaitTime();
    }


    public int getRequestRateLimit() {
        return whiteSourceSettings.getRequestRateLimit();
    }

    public long getRequestRateLimitTimeWindow() {
        return whiteSourceSettings.getRequestRateLimitTimeWindow();
    }

    @Override
    public void collect(WhiteSourceCollector collector) {
        long start = System.currentTimeMillis();
        AtomicLong timeGetProducts = new AtomicLong(System.currentTimeMillis());
        AtomicLong timeGetProjects = new AtomicLong(System.currentTimeMillis());
        AtomicLong timeGetChangeRequestLog = new AtomicLong(System.currentTimeMillis());
        AtomicLong timeGetProjectVitals = new AtomicLong(System.currentTimeMillis());
        Count count = new Count();
        List<WhiteSourceComponent> existingComponents = whiteSourceComponentRepository.findByCollectorIdIn(Stream.of(collector.getId()).collect(Collectors.toList()));
        Set<WhiteSourceComponent> existingComponentsSet = new HashSet<>(existingComponents);
        collector.getWhiteSourceServers().forEach(instanceUrl -> {
            log(instanceUrl);
            whiteSourceSettings.getWhiteSourceServerSettings().forEach(whiteSourceServerSettings -> {
                String logMessage = "WhiteSourceCollector :";
                Map<String, LibraryPolicyReference> libraryLookUp = new HashMap<>();
                try {
                    // Get org details
                    String orgToken = whiteSourceServerSettings.getOrgToken();
                    String orgName = whiteSourceClient.getOrgDetails(instanceUrl, whiteSourceServerSettings);

                    // Get the change request log
                    long historyTimestamp = getHistoryTimestamp(collector.getLastExecuted());
                    LOG.info("Look back time for processing changeRequestLog="+historyTimestamp+", collector lastExecutedTime="+collector.getLastExecuted());
                    timeGetChangeRequestLog.set(System.currentTimeMillis());
                    List<WhiteSourceChangeRequest> changeRequests = whiteSourceClient.getChangeRequestLog(instanceUrl,orgToken,orgName,historyTimestamp,whiteSourceServerSettings);
                    timeGetChangeRequestLog.set(System.currentTimeMillis() - timeGetChangeRequestLog.get());
                    LOG.info("WhitesourceCollectorTask: Time to get all change request logs: " + timeGetChangeRequestLog.get());
                    Set<WhiteSourceChangeRequest> changeSet = new HashSet<>(changeRequests);


                    //For the first run OR anytime after that if there is a change in a project, fetch all projects
                    List<WhiteSourceComponent> projects = new ArrayList<>();
                    if (collector.getLastExecuted() == 0 || checkForProjectChange(changeSet)) {
                        timeGetProducts.set(System.currentTimeMillis());
                        List<WhiteSourceProduct> products = whiteSourceClient.getProducts(instanceUrl, orgToken, orgName, whiteSourceServerSettings);
                        timeGetProducts.set(System.currentTimeMillis() - timeGetProducts.get());
                        LOG.info("WhitesourceCollectorTask: Time to get all products: " + timeGetProducts.get());

                        timeGetProjects.set(System.currentTimeMillis());
                        products.stream().map(product -> whiteSourceClient.getAllProjectsForProduct(instanceUrl, product, orgToken, orgName, whiteSourceServerSettings)).forEach(projects::addAll);
                        timeGetProjects.set(System.currentTimeMillis() - timeGetProjects.get());
                        LOG.info("WhitesourceCollectorTask: Time to get all projects: " + timeGetProjects.get());

                        count.addFetched(projects.size());
                        addNewApplications(projects, existingComponentsSet, collector, count);
                    } else {
                        LOG.info("WhitesourceCollectorTask: No need to fetch all projects: ");
                    }

                    // Get project vitals in one shot
                    timeGetProjectVitals.set(System.currentTimeMillis());
                    Map<String, WhiteSourceProjectVital> projectVitalMap = whiteSourceClient.getOrgProjectVitals(instanceUrl,orgToken,orgName,whiteSourceServerSettings);
                    timeGetProjectVitals.set(System.currentTimeMillis() - timeGetProjectVitals.get());
                    LOG.info("WhitesourceCollectorTask: Time to get all project vitals: " + timeGetProjectVitals.get());

                    //Refresh scan data
                    refreshData(collector, enabledApplications(collector), instanceUrl,count,libraryLookUp,orgName, orgToken, changeSet,projectVitalMap, whiteSourceServerSettings);

                    //Save Library Reference Data
                    libraryReferenceRepository.save(libraryLookUp.values());

                    logMessage="SUCCESS, orgName="+orgName+", fetched projects="+projects.size()+", New projects="+count.getAdded()+", updated-projects="+count.getUpdated()+", updated instance-data="+count.getInstanceCount();
                }catch (HygieiaException he) {
                    logMessage= "EXCEPTION occurred, "+he.getClass().getCanonicalName();
                    LOG.error("Unexpected error occurred while collecting data for url=" + instanceUrl, he);
                } finally {
                    LOG.info(String.format("status=%s",logMessage));
                }
            });
        });
        long end = System.currentTimeMillis();
        long elapsedTime = (end-start) / 1000;

        LOG.info(String.format("WhitesourceCollectorTask:collector stop, totalProcessSeconds=%d,  totalFetchedProjects=%d, totalNewProjects=%d, totalUpdatedProjects=%d, totalUpdatedInstanceData=%d ",
                elapsedTime, count.getFetched(), count.getAdded(), count.getUpdated(), count.getInstanceCount()));
    }


    private void refreshData(WhiteSourceCollector collector, List<WhiteSourceComponent> enabledProjects, String instanceUrl, Count count, Map<String, LibraryPolicyReference> libraryLookUp,
                             String orgName, String orgToken, Set<WhiteSourceChangeRequest> changeRequests, Map<String, WhiteSourceProjectVital> projectVitalMap, WhiteSourceServerSettings serverSettings) {
        int rateCount = 0;
        long startTime = System.currentTimeMillis();
        long timeGetAlerts = System.currentTimeMillis();

        int requestRateLimit  = getRequestRateLimit();
        long requestRateLimitTimeWindow = getRequestRateLimitTimeWindow();
        long waitTime = getWaitTime();

        List<WhiteSourceComponent> exception429TooManyRequestsComponentsList = new ArrayList<>();
        int counter = 0;
        Map<WhiteSourceChangeRequest,WhiteSourceChangeRequest> changeRequestMap = changeRequests.stream().collect(Collectors.toMap(Function.identity(),Function.identity()));
        Map<String,LibraryPolicyResult> libraryPolicyResultMap = new HashMap<>();

        Set<String> enabledProductTokens = enabledProjects.stream().map(WhiteSourceComponent::getProductToken).collect(Collectors.toSet());
        LOG.info("WhitesourceCollectorTask: Refresh Data - Total enabled products : " + (CollectionUtils.isEmpty(enabledProductTokens) ? 0 : enabledProductTokens.size()));
        LOG.info("WhitesourceCollectorTask: Refresh Data - Total enabled projects : " + (CollectionUtils.isEmpty(enabledProjects) ? 0 : enabledProjects.size()));

        // First will get project tokens that have at least 1 alert - get that from the org level alert
        Set<String> affectedProjectTokens = new HashSet<>();
        Set<String> affectedProductTokens = new HashSet<>();
        if (collector.getLastExecuted() > 0) {
            affectedProjectTokens = whiteSourceClient.getAffectedProjectsForOrganization(instanceUrl, orgName, orgToken, getHistoryTimestamp(collector.getLastExecuted()), serverSettings);
            affectedProjectTokens.addAll(getAffectedProjectsFromChanges(changeRequests,orgName));
            for (WhiteSourceComponent whiteSourceComponent : enabledProjects) {
                if (affectedProjectTokens.contains(whiteSourceComponent.getProjectToken())) {
                    affectedProductTokens.add(whiteSourceComponent.getProductToken());
                }
            }
            timeGetAlerts = System.currentTimeMillis();
            affectedProductTokens.stream().map(ept -> whiteSourceClient.getProductAlerts(instanceUrl, orgName, ept, projectVitalMap, serverSettings)).forEach(libraryPolicyResultMap::putAll);
            timeGetAlerts = System.currentTimeMillis() - timeGetAlerts;
            LOG.info("WhitesourceCollectorTask: Refresh Data - time to get only affected project alerts: " + timeGetAlerts);
        } else {
            timeGetAlerts = System.currentTimeMillis();
            enabledProductTokens.stream().map(ept -> whiteSourceClient.getProductAlerts(instanceUrl, orgName, ept, projectVitalMap, serverSettings)).forEach(libraryPolicyResultMap::putAll);
            timeGetAlerts = System.currentTimeMillis() - timeGetAlerts;
            LOG.info("WhitesourceCollectorTask: Refresh Data - time to get all product alerts: " + timeGetAlerts);
        }

        for(WhiteSourceComponent component : enabledProjects) {
                if (component.checkErrorOrReset(whiteSourceSettings.getErrorResetWindow(), whiteSourceSettings.getErrorThreshold())) {
                    try {
                        LibraryPolicyResult libraryPolicyResult = libraryPolicyResultMap.get(component.getProjectToken());
                        if (Objects.nonNull(libraryPolicyResult)) {
                            LibraryPolicyResult libraryPolicyResultExisting = getLibraryPolicyResultExisting(component.getId(),libraryPolicyResult.getEvaluationTimestamp());
                            // add to lookup
                             processLibraryLookUp(libraryPolicyResult,libraryLookUp,orgName,component,changeRequestMap);
                            libraryPolicyResult.setCollectorItemId(component.getId());
                            counter++;
                            if (Objects.nonNull(libraryPolicyResultExisting)) {
                                libraryPolicyResult.setId(libraryPolicyResultExisting.getId());
                            }
                            libraryPolicyResultsRepository.save(libraryPolicyResult);
                            component.setLastUpdated(System.currentTimeMillis());
                            whiteSourceComponentRepository.save(component);
                        }
                    } catch (HttpStatusCodeException hc) {
                        exception429TooManyRequestsComponentsList.add(component);
                        LOG.error("Error fetching details for:" + component.getDescription(), hc);
                        CollectionError error = new CollectionError(hc.getStatusCode().toString(), hc.getMessage());
                        component.getErrors().add(error);
                    } catch (RestClientException re) {
                        LOG.error("Error fetching details for:" + component.getDescription(), re);
                        CollectionError error = new CollectionError(CollectionError.UNKNOWN_HOST, instanceUrl);
                        component.getErrors().add(error);
                    }


                    if (requestRateLimit > 0 && throttleRequests(startTime, rateCount, waitTime, requestRateLimit, requestRateLimitTimeWindow)) {
                        startTime = System.currentTimeMillis();
                        rateCount = 0;
                    }
                } else {
                    LOG.info(component.getProjectName() + ":: errorThreshold exceeded");
                }
        }
        count.addInstanceCount(counter);
    }

    private Set<String> getAffectedProjectsFromChanges (Set<WhiteSourceChangeRequest> changes, String orgName) {
        return changes.stream()
                .filter(changeRequest -> Constants.LIBRARY_SCOPE.equalsIgnoreCase(changeRequest.getScope()))
                .map(WhiteSourceChangeRequest::getScopeName)
                .map(libraryName -> libraryReferenceRepository.findByLibraryNameAndOrgName(libraryName, orgName))
                .filter(Objects::nonNull)
                .map(LibraryPolicyReference::getProjectReferences)
                .filter(components -> !CollectionUtils.isEmpty(components))
                .flatMap(Collection::stream)
                .map(WhiteSourceComponent::getProjectToken)
                .collect(Collectors.toSet());
    }

    private void processLibraryLookUp(LibraryPolicyResult libraryPolicyResult, Map<String,LibraryPolicyReference> libraryLookUp,
                                      String orgName , WhiteSourceComponent whiteSourceComponent, Map<WhiteSourceChangeRequest,WhiteSourceChangeRequest> changeRequestMap){
        Collection<Set<LibraryPolicyResult.Threat>> libs = libraryPolicyResult.getThreats().values();
        libs.forEach(lib-> lib.stream().map(LibraryPolicyResult.Threat::getComponents).forEach(components -> {
            // Add or update lprs
            components.stream().map(WhiteSourceCollectorTask::getComponentName).filter(Objects::nonNull).forEach(name -> {
                LibraryPolicyReference lpr = libraryLookUp.get(name);
                if (Objects.isNull(lpr)) {
                    lpr = libraryReferenceRepository.findByLibraryNameAndOrgName(name, orgName);
                    if (Objects.nonNull(lpr)) {
                        libraryLookUp.put(name, lpr);
                    }
                }
                WhiteSourceChangeRequest whiteSourceChangeRequest = new WhiteSourceChangeRequest();
                whiteSourceChangeRequest.setScopeName(name);
                WhiteSourceChangeRequest changed = changeRequestMap.get(whiteSourceChangeRequest);
                if (Objects.nonNull(lpr)) {
                    lpr.setLibraryName(name);
                    addOrUpdateLibraryPolicyReference(orgName, whiteSourceComponent, lpr, changed);
                } else {
                    lpr = new LibraryPolicyReference();
                    lpr.setLibraryName(name);
                    addOrUpdateLibraryPolicyReference(orgName, whiteSourceComponent, lpr, changed);
                }
                libraryLookUp.put(name, lpr);
            });
        }));
    }

    private void addOrUpdateLibraryPolicyReference(String orgName, WhiteSourceComponent whiteSourceComponent, LibraryPolicyReference libraryPolicyReference, WhiteSourceChangeRequest changed) {
        if (!checkForIgnoredLibrary(changed, libraryPolicyReference, orgName)) {
            setLibraryPolicyReference(whiteSourceComponent, libraryPolicyReference, changed);
        }
    }

    private static void setLibraryPolicyReference(WhiteSourceComponent whiteSourceComponent, LibraryPolicyReference libraryPolicyReference, WhiteSourceChangeRequest changed) {
        List<WhiteSourceComponent> projectReferences = libraryPolicyReference.getProjectReferences();
        libraryPolicyReference.setLastUpdated(System.currentTimeMillis());
        libraryPolicyReference.setOrgName(whiteSourceComponent.getOrgName());
        HashMap<WhiteSourceComponent, WhiteSourceComponent> referencesMap = (HashMap<WhiteSourceComponent, WhiteSourceComponent>) projectReferences.stream().collect(Collectors.toMap(Function.identity(), Function.identity()));
        if (Objects.isNull(referencesMap.get(whiteSourceComponent))) {
            libraryPolicyReference.getProjectReferences().add(whiteSourceComponent);
        }
        if (Objects.nonNull(changed)){
            libraryPolicyReference.setChangeClass(changed.getChangeClass());
            libraryPolicyReference.setOperator(changed.getOperator());
            libraryPolicyReference.setUserEmail(changed.getUserEmail());
        }

    }

    private  boolean checkForIgnoredLibrary(WhiteSourceChangeRequest changed, LibraryPolicyReference libraryPolicyReference, String orgName){
        if (Objects.nonNull(changed) && changed.getChangeClass().equalsIgnoreCase(whiteSourceSettings.getIgnoredChangeClass())){
            libraryPolicyReference.getProjectReferences().clear();
            libraryPolicyReference.setLastUpdated(System.currentTimeMillis());
            libraryPolicyReference.setOperator(changed.getOperator());
            libraryPolicyReference.setUserEmail(changed.getUserEmail());
            libraryPolicyReference.setOrgName(orgName);
            return true;
        }
        return false;
    }

    /**
     * Check if any changes to a project (usually a create, update, delete)
     * return true if project change.
     */

    private  boolean checkForProjectChange(Set<WhiteSourceChangeRequest> changes){
        if (CollectionUtils.isEmpty(changes)) {
            return false;
        }
        return changes.stream().anyMatch(wcr -> !StringUtils.isEmpty(wcr.getProjectName()));
    }


    private static String getComponentName(String component){
        if(component.contains("#")) {
            return component.substring(0, component.indexOf('#'));
        }
        return null;
    }
    private LibraryPolicyResult getLibraryPolicyResultExisting(ObjectId collectorItemId, long evaluationTimestamp) {
        return libraryPolicyResultsRepository.findByCollectorItemIdAndEvaluationTimestamp(collectorItemId, evaluationTimestamp);
    }

    private List<WhiteSourceComponent> enabledApplications(WhiteSourceCollector collector) {
//        return whiteSourceComponentRepository.findByCollectorIdIn(Lists.newArrayList(collector.getId()));
        return whiteSourceComponentRepository.findEnabledComponents(collector.getId());
    }

    private void addNewApplications(List<WhiteSourceComponent> applications, Set<WhiteSourceComponent> existingApplications, WhiteSourceCollector collector, Count count) {
        int newCount = 0;
        int updatedCount = 0;
        HashMap<WhiteSourceComponent,WhiteSourceComponent> existingMap = (HashMap<WhiteSourceComponent, WhiteSourceComponent>) existingApplications.stream().collect(Collectors.toMap(Function.identity(),Function.identity()));
        for (WhiteSourceComponent application : applications) {
            WhiteSourceComponent matched = existingMap.get(application);
            if (matched == null) {
                application.setCollectorId(collector.getId());
                application.setCollector(collector);
                application.setEnabled(false);
                application.setDescription(application.getProjectName());
                application.setPushed(false);
                whiteSourceComponentRepository.save(application);
                newCount++;
            } else {
                if (Objects.isNull(matched.getProjectName()) || Objects.isNull(matched.getProductName()) || Objects.isNull(matched.getProductToken())) {
                    matched.setProductToken(application.getProductToken());
                    matched.setProjectName(application.getProjectName());
                    matched.setProductName(application.getProductName());
                    whiteSourceComponentRepository.save(matched);
                    updatedCount++;
                }
            }
        }
        count.addNewCount(newCount);
        count.addUpdatedCount(updatedCount);
    }

    private long getHistoryTimestamp(long collectorLastUpdated) {
        if(collectorLastUpdated > 0) {
            return collectorLastUpdated - whiteSourceSettings.getOffSet();
        }else{
            return System.currentTimeMillis() - whiteSourceSettings.getHistoryTimestamp();
        }
    }

}
