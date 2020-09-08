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
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
        Count count = new Count();
        List<WhiteSourceComponent> existingComponents = whiteSourceComponentRepository.findByCollectorIdIn(Stream.of(collector.getId()).collect(Collectors.toList()));
        collector.getWhiteSourceServers().forEach(instanceUrl -> {
            log(instanceUrl);
            whiteSourceSettings.getWhiteSourceServerSettings().forEach(whiteSourceServerSettings -> {
                String logMessage = "WhiteSourceCollector :";
                Map<String, LibraryPolicyReference> libraryLookUp = new HashMap<>();
                try {
                    String orgToken = whiteSourceServerSettings.getOrgToken();
                    String orgName = whiteSourceClient.getOrgDetails(instanceUrl, whiteSourceServerSettings);
                    List<WhiteSourceProduct> products = whiteSourceClient.getProducts(instanceUrl, orgToken,orgName,whiteSourceServerSettings);
                    List<WhiteSourceComponent> projects = new ArrayList<>();
                    for (WhiteSourceProduct product : products) {
                        projects.addAll(whiteSourceClient.getAllProjectsForProduct(instanceUrl, product, orgToken, orgName,whiteSourceServerSettings));
                    }
                    count.addFetched(projects.size());
                    addNewApplications(projects, existingComponents, collector, count);
                    long historyTimestamp = getHistoryTimestamp(collector.getLastExecuted());
                    LOG.info("Look back time for processing changeRequestLog="+historyTimestamp+", collector lastExecutedTime="+collector.getLastExecuted());
                    // find change request log
                    List<WhiteSourceChangeRequest> changeRequests = whiteSourceClient.getChangeRequestLog(instanceUrl,orgToken,orgName,historyTimestamp,whiteSourceServerSettings);
                    Set<WhiteSourceChangeRequest> changeSet = changeRequests.stream().collect(Collectors.toSet());
                    // get collectorItems from changeRequests
                    List<WhiteSourceComponent> changedCollectorItems = new ArrayList<>();
                    // find al collectorItems with scope PROJECT
                    for (WhiteSourceChangeRequest changeRequest: changeRequests) {
                        if(changeRequest.getScope().equalsIgnoreCase(Constants.PROJECT)){
                            changedCollectorItems.addAll(whiteSourceCustomComponentRepository.findCollectorItemsByUniqueOptions(collector.getId(),getOptions(changeRequest),getOptions(changeRequest),collector.getUniqueFields()));
                        }
                        if(changeRequest.getScope().equalsIgnoreCase(Constants.LIBRARY_SCOPE)){
                            LibraryPolicyReference lpr = libraryReferenceRepository.findByLibraryNameAndOrgName(changeRequest.getScopeName(),orgName);
                            if(Objects.nonNull(lpr)){
                                changedCollectorItems.addAll(lpr.getProjectReferences());
                            }
                        }
                    }
                    refreshData(changedCollectorItems,enabledApplications(collector), getRequestRateLimit(),
                            getRequestRateLimitTimeWindow(), getWaitTime(), instanceUrl,count,libraryLookUp,orgName,changeSet,whiteSourceServerSettings);
                    libraryReferenceRepository.save(libraryLookUp.values());
                    logMessage="SUCCESS, orgName="+orgName+", fetched projects="+projects.size()+", New projects="+count.getAdded()+", updated-projects="+count.getUpdated()+", updated instance-data="+count.getInstanceCount();
                }catch (HygieiaException he) {
                    logMessage= "EXCEPTION, "+he.getClass().getCanonicalName();
                    LOG.error("Unexpected error occurred while collecting data for url=" + instanceUrl, he);
                } finally {
                    LOG.info(String.format("status=%s",logMessage));
                }
            });
        });
        long end = System.currentTimeMillis();
        long elapsedTime = (end-start) / 1000;
        LOG.info(String.format("WhitesourceCollectorTask:collect stop, totalProcessSeconds=%d,  totalFetchedProjects=%d, totalNewProjects=%d, totalUpdatedProjects=%d, totalUpdatedInstanceData=%d ",
                elapsedTime, count.getFetched(), count.getAdded(), count.getUpdated(), count.getInstanceCount()));
    }


    private Map<String, Object> getOptions(WhiteSourceChangeRequest changeRequest){
            Map<String, Object> options = new HashMap<>();
            options.put(Constants.ORG_NAME,changeRequest.getOrgName());
            options.put(Constants.PRODUCT_NAME,changeRequest.getProductName());
            options.put(Constants.PROJECT_NAME,changeRequest.getProjectName());
            return options;
    }

    private void refreshData(List<WhiteSourceComponent> changedProjects, List<WhiteSourceComponent> enabledProjects, int requestRateLimit, long requestRateLimitTimeWindow, long waitTime, String instanceUrl, Count count, Map<String, LibraryPolicyReference> libraryLookUp,
                             String orgName, Set<WhiteSourceChangeRequest> changeRequests, WhiteSourceServerSettings serverSettings) {
        int rateCount = 0;
        long startTime = System.currentTimeMillis();
        List<WhiteSourceComponent> exception429TooManyRequestsComponentsList = new ArrayList<>();
        int counter = 0;
        HashMap<WhiteSourceComponent,WhiteSourceComponent> changedMap = (HashMap<WhiteSourceComponent, WhiteSourceComponent>) changedProjects.stream().collect(Collectors.toMap(Function.identity(),Function.identity()));
        HashMap<WhiteSourceChangeRequest,WhiteSourceChangeRequest> changeRequestMap = (HashMap<WhiteSourceChangeRequest, WhiteSourceChangeRequest>) changeRequests.stream().collect(Collectors.toMap(Function.identity(),Function.identity()));
        for(WhiteSourceComponent component : enabledProjects) {
            boolean firstRun = component.getLastUpdated()== 0;
             if(isEligible(component,firstRun,changedMap)){
                if (component.checkErrorOrReset(whiteSourceSettings.getErrorResetWindow(), whiteSourceSettings.getErrorThreshold())) {
                    try {
                        LibraryPolicyResult libraryPolicyResult = whiteSourceClient.getProjectAlerts(instanceUrl, component,serverSettings);
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
                        }
                        component.setLastUpdated(System.currentTimeMillis());
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
                    whiteSourceComponentRepository.save(component);

                    if (requestRateLimit > 0 && throttleRequests(startTime, rateCount, waitTime, requestRateLimit, requestRateLimitTimeWindow)) {
                        startTime = System.currentTimeMillis();
                        rateCount = 0;
                    }
                } else {
                    LOG.info(component.getProjectName() + ":: errorThreshold exceeded");
                }
            }
        }
        count.addInstanceCount(counter);
    }

    private void processLibraryLookUp(LibraryPolicyResult libraryPolicyResult, Map<String,LibraryPolicyReference> libraryLookUp,
                                      String orgName , WhiteSourceComponent whiteSourceComponent, HashMap<WhiteSourceChangeRequest,WhiteSourceChangeRequest> changeRequestMap){
        Collection<Set<LibraryPolicyResult.Threat>> libs = libraryPolicyResult.getThreats().values();
        libs.forEach(lib->{
            lib.stream().forEach(threat->{
                List<String> components = threat.getComponents();
                components.forEach(component -> {
                    String name = getComponentName(component);
                    if(Objects.nonNull(name)){
                        LibraryPolicyReference lpr = libraryLookUp.get(name);
                        if(Objects.isNull(lpr)){
                            lpr = libraryReferenceRepository.findByLibraryNameAndOrgName(name,orgName);
                            if(Objects.nonNull(lpr)){
                                libraryLookUp.put(name,lpr);
                            }
                        }
                        LibraryPolicyReference libraryPolicyReference = libraryLookUp.get(name);
                        WhiteSourceChangeRequest whiteSourceChangeRequest = new WhiteSourceChangeRequest();
                        whiteSourceChangeRequest.setScopeName(name);
                        WhiteSourceChangeRequest changed = changeRequestMap.get(whiteSourceChangeRequest);
                            // Add or update lprs
                            if (Objects.nonNull(libraryPolicyReference)) {
                                addOrUpdateLibraryPolicyReference(orgName, whiteSourceComponent, libraryPolicyReference, changed);
                            } else {
                                libraryPolicyReference = new LibraryPolicyReference();
                                addOrUpdateLibraryPolicyReference(orgName, whiteSourceComponent, libraryPolicyReference, changed);
                            }
                        libraryLookUp.put(name, libraryPolicyReference);
                    }
                });
            });
        });
    }

    private void addOrUpdateLibraryPolicyReference(String orgName, WhiteSourceComponent whiteSourceComponent, LibraryPolicyReference libraryPolicyReference, WhiteSourceChangeRequest changed) {
        if (!checkForIgnoredLibrary(changed, libraryPolicyReference, orgName)) {
            setLibraryPolicyReference(whiteSourceComponent, libraryPolicyReference, changed);
        }
    }

    private void setLibraryPolicyReference(WhiteSourceComponent whiteSourceComponent, LibraryPolicyReference libraryPolicyReference, WhiteSourceChangeRequest changed) {
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

    private String getComponentName(String component){
        if(component.contains("#")) {
            return component.substring(0, component.indexOf("#"));
        }
        return null;
    }
    private LibraryPolicyResult getLibraryPolicyResultExisting(ObjectId collectorItemId, long evaluationTimestamp) {
        return libraryPolicyResultsRepository.findByCollectorItemIdAndEvaluationTimestamp(collectorItemId, evaluationTimestamp);
    }

    private List<WhiteSourceComponent> enabledApplications(WhiteSourceCollector collector) {
        return whiteSourceComponentRepository.findEnabledComponents(collector.getId());
    }

    private void addNewApplications(List<WhiteSourceComponent> applications, List<WhiteSourceComponent> existingApplications, WhiteSourceCollector collector, Count count) {
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
                if (Objects.isNull(matched.getProductToken()) && Objects.isNull(matched.getProjectToken())) {
                    matched.setProductToken(application.getProductToken());
                    matched.setProjectToken(application.getProjectToken());
                    whiteSourceComponentRepository.save(matched);
                    updatedCount++;
                }
            }
        }
        count.addNewCount(newCount);
        count.addUpdatedCount(updatedCount);
    }

    private boolean isEligible(WhiteSourceComponent enabledComponent, boolean firstRun, HashMap<WhiteSourceComponent,WhiteSourceComponent> changedMap){
        if(enabledComponent.isEnabled() && firstRun) return true;
        if(enabledComponent.isEnabled() && changedMap.get(enabledComponent)!=null) return true;
        return false;
    }

    private long getHistoryTimestamp(long collectorLastUpdated) {
        if(collectorLastUpdated > 0) {
            return collectorLastUpdated - whiteSourceSettings.getOffSet();
        }else{
            return System.currentTimeMillis() - whiteSourceSettings.getHistoryTimestamp();
        }
    }

}
