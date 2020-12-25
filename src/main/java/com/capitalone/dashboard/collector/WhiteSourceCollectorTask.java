package com.capitalone.dashboard.collector;


import com.capitalone.dashboard.misc.HygieiaException;
import com.capitalone.dashboard.model.Count;
import com.capitalone.dashboard.model.LibraryPolicyReference;
import com.capitalone.dashboard.model.LibraryPolicyResult;
import com.capitalone.dashboard.model.WhiteSourceChangeRequest;
import com.capitalone.dashboard.model.WhiteSourceCollector;
import com.capitalone.dashboard.model.WhiteSourceComponent;
import com.capitalone.dashboard.model.WhiteSourceProduct;
import com.capitalone.dashboard.model.WhiteSourceProjectVital;
import com.capitalone.dashboard.model.WhiteSourceServerSettings;
import com.capitalone.dashboard.model.WhitesourceOrg;
import com.capitalone.dashboard.repository.BaseCollectorRepository;
import com.capitalone.dashboard.repository.LibraryPolicyResultsRepository;
import com.capitalone.dashboard.repository.LibraryReferenceRepository;
import com.capitalone.dashboard.repository.WhiteSourceCollectorRepository;
import com.capitalone.dashboard.repository.WhiteSourceComponentRepository;
import com.capitalone.dashboard.repository.WhiteSourceCustomComponentRepository;
import com.capitalone.dashboard.utils.Constants;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.SetUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import sun.text.resources.cldr.zh.FormatData_zh_Hans_SG;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
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

        // Get existing projects from collector item repository
        List<WhiteSourceComponent> existingProjects = whiteSourceComponentRepository.findByCollectorIdIn(Stream.of(collector.getId()).collect(Collectors.toList()));
        Set<WhiteSourceComponent> existingProjectsSet = new HashSet<>(existingProjects);

        collector.getWhiteSourceServers().forEach(instanceUrl -> {
            log(instanceUrl);
            whiteSourceSettings.getWhiteSourceServerSettings().forEach(whiteSourceServerSettings -> {
                String logMessage = "WhiteSourceCollector :";
                Map<String, LibraryPolicyReference> libraryLookUp = new HashMap<>();
                try {
                    // Get org details
                    WhitesourceOrg whitesourceOrg = whiteSourceClient.getOrgDetails(whiteSourceServerSettings);

                    // Get the change request log
                    Set<WhiteSourceChangeRequest> changeSet = getChangeRequests(collector, whitesourceOrg, whiteSourceServerSettings);

                    // Add new projects
//                    List<WhiteSourceComponent> projects = addProjects(collector, whitesourceOrg, changeSet, existingProjectsSet, count, whiteSourceServerSettings);
                    List<WhiteSourceComponent> projects = new ArrayList<>();
                    // Get project vitals in a map
                    Map<String, WhiteSourceProjectVital> projectVitalMap = whiteSourceClient.getOrgProjectVitals(whitesourceOrg, whiteSourceServerSettings);

                    //Refresh scan data
                    int refreshCount = refreshData(collector, libraryLookUp, whitesourceOrg, changeSet, projectVitalMap, whiteSourceServerSettings);
                    count.addInstanceCount(refreshCount);

                    //Save Library Reference Data
                    libraryReferenceRepository.save(libraryLookUp.values());

                    logMessage = "SUCCESS, orgName=" + whitesourceOrg.getName() + ", fetched projects=" + projects.size() + ", New projects=" + count.getAdded() + ", updated-projects=" + count.getUpdated() + ", updated instance-data=" + count.getInstanceCount();
                } catch (HygieiaException he) {
                    logMessage = "EXCEPTION occurred, " + he.getClass().getCanonicalName();
                    LOG.error("Unexpected error occurred while collecting data for url=" + instanceUrl, he);
                } finally {
                    LOG.info(String.format("status=%s", logMessage));
                }
            });
        });
        long end = System.currentTimeMillis();
        long elapsedTime = (end - start) / 1000;

        LOG.info(String.format("WhitesourceCollectorTask:collector stop, totalProcessSeconds=%d,  totalFetchedProjects=%d, totalNewProjects=%d, totalUpdatedProjects=%d, totalUpdatedInstanceData=%d ",
                elapsedTime, count.getFetched(), count.getAdded(), count.getUpdated(), count.getInstanceCount()));
    }


    /**
     * Gets the org level change requests log
     *
     * @param collector                 Whitesource collector
     * @param whitesourceOrg            Whitesource Org
     * @param whiteSourceServerSettings Whitesource Server Setting
     * @return Set of Change Requests
     */
    private Set<WhiteSourceChangeRequest> getChangeRequests(WhiteSourceCollector collector, WhitesourceOrg whitesourceOrg, WhiteSourceServerSettings whiteSourceServerSettings) throws HygieiaException {
        // Get the change request log
        long historyTimestamp = getHistoryTimestamp(collector);
        LOG.info("Look back time for processing changeRequestLog=" + historyTimestamp + ", collector lastExecutedTime=" + collector.getLastExecuted());
        List<WhiteSourceChangeRequest> changeRequests = whiteSourceClient.getChangeRequestLog(whitesourceOrg, historyTimestamp, whiteSourceServerSettings);
        return new HashSet<>(changeRequests);
    }

    /**
     * Fetches all projects for an org and adds new collector items
     *
     * @param collector                 collector
     * @param whitesourceOrg            white source org
     * @param changeSet                 organization change set
     * @param existingProjectsSet       existing projects
     * @param count                     count
     * @param whiteSourceServerSettings whitesource server settings
     * @return List of Whitesource Projects
     */
    private List<WhiteSourceComponent> addProjects(WhiteSourceCollector collector, WhitesourceOrg whitesourceOrg,
                                                   Set<WhiteSourceChangeRequest> changeSet,
                                                   Set<WhiteSourceComponent> existingProjectsSet, Count count,
                                                   WhiteSourceServerSettings whiteSourceServerSettings) {

        long timeGetProjects = System.currentTimeMillis();
        List<WhiteSourceComponent> projects = new ArrayList<>();
        try {
            if (collector.getLastExecuted() == 0 || checkForProjectChange(changeSet)) {
                List<WhiteSourceProduct> products = whiteSourceClient.getProducts(whitesourceOrg,
                        whiteSourceServerSettings);
                products.stream().map(product -> whiteSourceClient.getAllProjectsForProduct(whitesourceOrg, product,
                        whiteSourceServerSettings)).forEach(projects::addAll);
                timeGetProjects = System.currentTimeMillis() - timeGetProjects;
                LOG.info("WhitesourceCollectorTask: Time to get all projects: " + timeGetProjects);
                count.addFetched(projects.size());
                upsertProjects(projects, existingProjectsSet, collector, count);
            } else {
                LOG.info("WhitesourceCollectorTask: No need to fetch all projects: ");
            }
        } catch (HygieiaException he) {
            LOG.error("Unexpected error occurred while collecting data for url=" + whiteSourceServerSettings.getInstanceUrl(), he);
        }
        return projects;
    }


    /**
     * Adds or updates new whitesource projects to collector items
     *
     * @param projects         all whitesource projects
     * @param existingProjects existing whitesource projects
     * @param collector        whitesource collector
     * @param count            Count
     */
    private void upsertProjects(List<WhiteSourceComponent> projects, Set<WhiteSourceComponent> existingProjects, WhiteSourceCollector collector, Count count) {
        int newCount = 0;
        int updatedCount = 0;
        HashMap<WhiteSourceComponent, WhiteSourceComponent> existingMap = (HashMap<WhiteSourceComponent, WhiteSourceComponent>) existingProjects.stream().collect(Collectors.toMap(Function.identity(), Function.identity()));
        for (WhiteSourceComponent project : projects) {
            WhiteSourceComponent matched = existingMap.get(project);
            if (matched == null) {
                project.setCollectorId(collector.getId());
                project.setCollector(collector);
                project.setEnabled(false);
                project.setDescription(project.getProjectName());
                project.setPushed(false);
                whiteSourceComponentRepository.save(project);
                newCount++;
            } else {
                if (Objects.isNull(matched.getProjectName()) || Objects.isNull(matched.getProductName()) || Objects.isNull(matched.getProductToken())) {
                    matched.setProductToken(project.getProductToken());
                    matched.setProjectName(project.getProjectName());
                    matched.setProductName(project.getProductName());
                    whiteSourceComponentRepository.save(matched);
                    updatedCount++;
                }
            }
        }
        count.addNewCount(newCount);
        count.addUpdatedCount(updatedCount);
    }


    /**
     * Refresh Scan data
     * Refresh logic -
     * (1) First collect the project alerts for projects that have recent scans - find those from project vitals with
     * last updated time greater than history time
     * (2) Next collect the project alerts for projects that are impacted by change requests - such as license change.
     * Identify the libraries from change requests with "scope" = "LIBRARY". Name of library  = "scopeName".
     * For these libraries, look up Library Reference collection and identify the projects impacted and collect
     * (3) Next collect the project alters for projects that are in org level alerts list for policy violations
     * (4) Last collect rest of the projects in the enabled project list. Do not double collect the above
     * <p>
     * - For the first collector run, collect all project alerts (step 4)
     * - For all project alert collection, collect alerts at the product level to reduce number of http calls
     *
     * @param collector       Whitesource collector
     * @param libraryLookUp   Library Lookup Map
     * @param whitesourceOrg  Whitesource Org
     * @param changeRequests  Change Requests for the org
     * @param projectVitalMap Project Vital Map
     * @param serverSettings  Whitesource Server Setting
     */
    private int refreshData(WhiteSourceCollector collector, Map<String, LibraryPolicyReference> libraryLookUp,
                            WhitesourceOrg whitesourceOrg, Set<WhiteSourceChangeRequest> changeRequests,
                            Map<String, WhiteSourceProjectVital> projectVitalMap, WhiteSourceServerSettings serverSettings) {


        long startTime = System.currentTimeMillis();
        int count = 0;
        Set<WhiteSourceComponent> collectedProjects = new HashSet<>();

        Map<WhiteSourceChangeRequest, WhiteSourceChangeRequest> changeRequestMap = changeRequests.stream().collect(Collectors.toMap(Function.identity(), Function.identity()));

        Set<WhiteSourceComponent> enabledProjects = getEnabledProjects(collector);

        Set<String> enabledProductTokens = enabledProjects.stream().map(WhiteSourceComponent::getProductToken).collect(Collectors.toSet());

        LOG.info("WhitesourceCollectorTask: Refresh Data - Total enabled products : " + (CollectionUtils.isEmpty(enabledProductTokens) ? 0 : enabledProductTokens.size()));
        LOG.info("WhitesourceCollectorTask: Refresh Data - Total enabled projects : " + (CollectionUtils.isEmpty(enabledProjects) ? 0 : enabledProjects.size()));


        if (collector.getLastExecuted() > 0) {
            // (1) Collect for recently scanned projects first

            //Note: This list contains only enabled projects
            Set<WhiteSourceComponent> recentScannedProjects = getRecentScannedProjects(projectVitalMap, enabledProjects);
            Set<WhiteSourceComponent> collected = getAndUpdateData(whitesourceOrg, enabledProjects, recentScannedProjects, projectVitalMap, changeRequestMap, libraryLookUp, serverSettings);
            count = collected.size();
            collectedProjects.addAll(collected);


            // (2)
            Set<String> newAlertProjectTokens = whiteSourceClient.getAffectedProjectsForOrganization(whitesourceOrg, getHistoryTimestamp(collector), serverSettings);
            Set<WhiteSourceComponent> newAlertProjects = enabledProjects.stream()
                    .filter(e -> newAlertProjectTokens.contains(e.getProjectToken()))
                    .filter(e -> !collectedProjects.contains(e)) //not collected yet
                    .collect(Collectors.toSet());

            collected = getAndUpdateData(whitesourceOrg, enabledProjects, newAlertProjects, projectVitalMap, changeRequestMap, libraryLookUp, serverSettings);
            count = count + collected.size();
            collectedProjects.addAll(collected);

            // (3)
            Set<String> changeRequestProjectTokens = getAffectedProjectsFromChanges(changeRequests, whitesourceOrg.getName());
            Set<WhiteSourceComponent> changeRequestProjects = enabledProjects.stream()
                    .filter(e -> changeRequestProjectTokens.contains(e.getProjectToken()))
                    .filter(e -> !collectedProjects.contains(e)) //not collected yet
                    .collect(Collectors.toSet());

            collected = getAndUpdateData(whitesourceOrg, enabledProjects, changeRequestProjects, projectVitalMap, changeRequestMap, libraryLookUp, serverSettings);
            count = count + collected.size();
            collectedProjects.addAll(collected);

            LOG.info("WhitesourceCollectorTask: Refresh Data - High Priority Changes - Total projects : " +
                    (CollectionUtils.isEmpty(collectedProjects) ? 0 : collectedProjects.size()) +
                    ". Time taken =" + (System.currentTimeMillis() - startTime));
        }
        startTime = System.currentTimeMillis();

        // (4)
        Set<WhiteSourceComponent> remainingProjects = enabledProjects.stream().filter(e -> !collectedProjects.contains(e)).collect(Collectors.toSet());

        //Start multi threading now
        Iterable<List<WhiteSourceComponent>> partitions = Iterables.partition(remainingProjects, remainingProjects.size() / 4);
        Set<WhiteSourceComponent> collected = new HashSet<>();
        CompletableFuture<Set<WhiteSourceComponent>>[] threads = new CompletableFuture[5];
        int i = 0;
        for (List<WhiteSourceComponent> partition : partitions) {
            threads[i] = getAndUpdateDataThread(whitesourceOrg, enabledProjects, new HashSet<>(partition), projectVitalMap, changeRequestMap, libraryLookUp, serverSettings);
            i++;
        }
        CompletableFuture.allOf(threads).join();

//        Set<WhiteSourceComponent> collected = getAndUpdateData(whitesourceOrg, enabledProjects, remainingProjects, projectVitalMap, changeRequestMap, libraryLookUp, serverSettings);

        for (int x = 0; x < threads.length; x++) {
            if (threads[x] != null) {
                try {
                    count = count + threads[x].get().size();
                    collected.addAll(threads[x].get());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (ExecutionException e) {
                    e.printStackTrace();
                }
            }
//            count = count + threads[x].
            collectedProjects.addAll(collected);
        }

        LOG.info("WhitesourceCollectorTask: Refresh Data - Normal changes - Total projects : " +
                (CollectionUtils.isEmpty(remainingProjects) ? 0 : remainingProjects.size()) +
                ". Time taken =" + (System.currentTimeMillis() - startTime));
        return count;
    }

    private Set<WhiteSourceComponent> getAndUpdateData(WhitesourceOrg whitesourceOrg, Set<WhiteSourceComponent> enabledProjects, Set<WhiteSourceComponent> projectsToCollect,
                                                       Map<String, WhiteSourceProjectVital> projectVitalMap,
                                                       Map<WhiteSourceChangeRequest, WhiteSourceChangeRequest> changeRequestMap,
                                                       Map<String, LibraryPolicyReference> libraryLookUp,
                                                       WhiteSourceServerSettings serverSettings) {
        AtomicInteger counter = new AtomicInteger();
        Map<String, LibraryPolicyResult> libraryPolicyResultMap = new HashMap<>();

        Set<String> productTokensToCollect = projectsToCollect.stream().map(WhiteSourceComponent::getProductToken).collect(Collectors.toSet());

        productTokensToCollect
                .stream()
                .map(ept -> whiteSourceClient.getProductAlerts(whitesourceOrg, ept, projectVitalMap, serverSettings))
                .forEach(libraryPolicyResultMap::putAll);

        //only save enabled projects
        Set<WhiteSourceComponent> collected = enabledProjects.stream().filter(e -> libraryPolicyResultMap.containsKey(e.getProjectToken())).collect(Collectors.toSet());
        collected.forEach(project -> {
            saveScanData(whitesourceOrg, project, libraryPolicyResultMap, libraryLookUp, changeRequestMap);
            counter.getAndIncrement();
        });
        return collected;
    }


    private CompletableFuture<Set<WhiteSourceComponent>> getAndUpdateDataThread(WhitesourceOrg whitesourceOrg, Set<WhiteSourceComponent> enabledProjects, Set<WhiteSourceComponent> projectsToCollect,
                                                       Map<String, WhiteSourceProjectVital> projectVitalMap,
                                                       Map<WhiteSourceChangeRequest, WhiteSourceChangeRequest> changeRequestMap,
                                                       Map<String, LibraryPolicyReference> libraryLookUp,
                                                       WhiteSourceServerSettings serverSettings) {
        AtomicInteger counter = new AtomicInteger();
        Map<String, LibraryPolicyResult> libraryPolicyResultMap = new HashMap<>();

        Set<String> productTokensToCollect = projectsToCollect.stream().map(WhiteSourceComponent::getProductToken).collect(Collectors.toSet());

        productTokensToCollect
                .stream()
                .map(ept -> whiteSourceClient.getProductAlerts(whitesourceOrg, ept, projectVitalMap, serverSettings))
                .forEach(libraryPolicyResultMap::putAll);

        //only save enabled projects
        Set<WhiteSourceComponent> collected = enabledProjects.stream().filter(e -> libraryPolicyResultMap.containsKey(e.getProjectToken())).collect(Collectors.toSet());
        collected.forEach(project -> {
            saveScanData(whitesourceOrg, project, libraryPolicyResultMap, libraryLookUp, changeRequestMap);
            counter.getAndIncrement();
        });
        return  CompletableFuture.completedFuture(collected);
    }

    /**
     * Based on last update times in project vitals map, return a list of projects that are updated since enabled
     * component was last updated mius the offset
     *
     * @param projectVitalMap project vital map
     * @param enabledProjects enabled project
     * @return set of project tokens
     */
    private Set<WhiteSourceComponent> getRecentScannedProjects(Map<String, WhiteSourceProjectVital> projectVitalMap, Set<WhiteSourceComponent> enabledProjects) {
        return enabledProjects
                .stream()
                .filter(e -> Objects.nonNull(projectVitalMap.get(e.getProjectToken())) &&
                        (e.getLastUpdated() - whiteSourceSettings.getOffSet() < projectVitalMap.get(e.getProjectToken()).getLastUpdateDate()))
                .collect(Collectors.toSet());
    }

    /**
     * Saves scan data to library policy collection. If existing, it overwrites. If new, it inserts. Also updates
     * lookup reference collection
     *
     * @param whitesourceOrg         whitesource org
     * @param project                whitesource project
     * @param libraryPolicyResultMap library policy results map
     * @param libraryLookUp          reference lookup map
     * @param changeRequestMap       change request map
     */
    private void saveScanData(WhitesourceOrg whitesourceOrg, WhiteSourceComponent project, Map<String,
            LibraryPolicyResult> libraryPolicyResultMap, Map<String, LibraryPolicyReference> libraryLookUp,
                              Map<WhiteSourceChangeRequest, WhiteSourceChangeRequest> changeRequestMap) {
        LibraryPolicyResult libraryPolicyResult = libraryPolicyResultMap.get(project.getProjectToken());

        if (Objects.isNull(libraryPolicyResult)) return;
        LibraryPolicyResult libraryPolicyResultExisting = getLibraryPolicyResultExisting(project.getId(), libraryPolicyResult.getEvaluationTimestamp());
        // add to lookup
        processLibraryLookUp(libraryPolicyResult, libraryLookUp, whitesourceOrg, project, changeRequestMap);
        libraryPolicyResult.setCollectorItemId(project.getId());
        if (Objects.nonNull(libraryPolicyResultExisting)) {
            libraryPolicyResult.setId(libraryPolicyResultExisting.getId());
        }
        libraryPolicyResultsRepository.save(libraryPolicyResult);
        project.setLastUpdated(System.currentTimeMillis());
        whiteSourceComponentRepository.save(project);

    }

    /**
     * Get all enabled project for a collector
     *
     * @param collector Whitesource collector
     * @return List of enabled whitesource project
     */
    private Set<WhiteSourceComponent> getEnabledProjects(WhiteSourceCollector collector) {
        return new HashSet<>(whiteSourceComponentRepository.findEnabledComponents(collector.getId()));
    }


    /**
     * Get affected Projects from the org change requests
     *
     * @param changes org level chance requests
     * @param orgName Org name
     * @return List of project tokens
     */
    private Set<String> getAffectedProjectsFromChanges(Set<WhiteSourceChangeRequest> changes, String orgName) {
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

    /**
     * Process Library Lookup
     *
     * @param libraryPolicyResult  Library Policy Result
     * @param libraryLookUp        Lookup map
     * @param whitesourceOrg       Whitesource org
     * @param whiteSourceComponent Whitesource component
     * @param changeRequestMap     Org Change Request Map
     */
    private void processLibraryLookUp(LibraryPolicyResult libraryPolicyResult, Map<String, LibraryPolicyReference> libraryLookUp,
                                      WhitesourceOrg whitesourceOrg, WhiteSourceComponent whiteSourceComponent, Map<WhiteSourceChangeRequest, WhiteSourceChangeRequest> changeRequestMap) {
        Collection<Set<LibraryPolicyResult.Threat>> libs = libraryPolicyResult.getThreats().values();
        libs.forEach(lib -> {
            lib.stream().map(LibraryPolicyResult.Threat::getComponents).forEach(components -> {
                // Add or update lprs
                components.stream().map(WhiteSourceCollectorTask::getComponentName).filter(Objects::nonNull).forEach(name -> {
                    LibraryPolicyReference lpr = libraryLookUp.get(name);
                    if (Objects.isNull(lpr)) {
                        lpr = libraryReferenceRepository.findByLibraryNameAndOrgName(name, whitesourceOrg.getName());
                        if (Objects.nonNull(lpr)) {
                            libraryLookUp.put(name, lpr);
                        }
                    }
                    WhiteSourceChangeRequest whiteSourceChangeRequest = new WhiteSourceChangeRequest();
                    whiteSourceChangeRequest.setScopeName(name);
                    WhiteSourceChangeRequest changed = changeRequestMap.get(whiteSourceChangeRequest);
                    if (Objects.nonNull(lpr)) {
                        lpr.setLibraryName(name);
                        addOrUpdateLibraryPolicyReference(whitesourceOrg.getName(), whiteSourceComponent, lpr, changed);
                    } else {
                        lpr = new LibraryPolicyReference();
                        lpr.setLibraryName(name);
                        addOrUpdateLibraryPolicyReference(whitesourceOrg.getName(), whiteSourceComponent, lpr, changed);
                    }
                    libraryLookUp.put(name, lpr);
                });
            });
        });
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
        if (Objects.nonNull(changed)) {
            libraryPolicyReference.setChangeClass(changed.getChangeClass());
            libraryPolicyReference.setOperator(changed.getOperator());
            libraryPolicyReference.setUserEmail(changed.getUserEmail());
        }

    }

    private boolean checkForIgnoredLibrary(WhiteSourceChangeRequest changed, LibraryPolicyReference libraryPolicyReference, String orgName) {
        if (Objects.nonNull(changed) && changed.getChangeClass().equalsIgnoreCase(whiteSourceSettings.getIgnoredChangeClass())) {
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

    private static boolean checkForProjectChange(Set<WhiteSourceChangeRequest> changes) {
        if (CollectionUtils.isEmpty(changes)) {
            return false;
        }
        return changes.stream().anyMatch(wcr -> !StringUtils.isEmpty(wcr.getProjectName()));
    }


    private static String getComponentName(String component) {
        if (component.contains("#")) {
            return component.substring(0, component.indexOf('#'));
        }
        return null;
    }

    /**
     * Gets existing Library Policy Result
     *
     * @param collectorItemId     Collector Item Id
     * @param evaluationTimestamp Evaluation Timestamp
     * @return Library Policy Result
     */
    private LibraryPolicyResult getLibraryPolicyResultExisting(ObjectId collectorItemId, long evaluationTimestamp) {
        return libraryPolicyResultsRepository.findByCollectorItemIdAndEvaluationTimestamp(collectorItemId, evaluationTimestamp);
    }


    /**
     * Get History Time Stamp
     *
     * @param collector White source Collector
     * @return long history timestamp milliseconds
     */
    private long getHistoryTimestamp(WhiteSourceCollector collector) {
        return collector.getLastExecuted() > 0
                ? collector.getLastExecuted() - whiteSourceSettings.getOffSet()
                : System.currentTimeMillis() - whiteSourceSettings.getHistoryTimestamp();
    }

}
