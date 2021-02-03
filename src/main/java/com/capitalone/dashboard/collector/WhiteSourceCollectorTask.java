package com.capitalone.dashboard.collector;


import com.capitalone.dashboard.misc.HygieiaException;
import com.capitalone.dashboard.model.CollectorMetric;
import com.capitalone.dashboard.model.DataRefresh;
import com.capitalone.dashboard.model.LibraryPolicyReference;
import com.capitalone.dashboard.model.WhiteSourceChangeRequest;
import com.capitalone.dashboard.model.WhiteSourceCollector;
import com.capitalone.dashboard.model.WhiteSourceComponent;
import com.capitalone.dashboard.model.WhiteSourceProduct;
import com.capitalone.dashboard.model.WhiteSourceProjectVital;
import com.capitalone.dashboard.model.WhitesourceOrg;
import com.capitalone.dashboard.repository.BaseCollectorRepository;
import com.capitalone.dashboard.repository.LibraryReferenceRepository;
import com.capitalone.dashboard.repository.WhiteSourceCollectorRepository;
import com.capitalone.dashboard.repository.WhiteSourceComponentRepository;
import com.capitalone.dashboard.settings.WhiteSourceServerSettings;
import com.capitalone.dashboard.settings.WhiteSourceSettings;
import com.capitalone.dashboard.utils.Constants;
import com.google.common.collect.Iterables;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;


@Component
public class WhiteSourceCollectorTask extends CollectorTask<WhiteSourceCollector> {
    private static final Log LOG = LogFactory.getLog(WhiteSourceCollectorTask.class);
    private final WhiteSourceCollectorRepository whiteSourceCollectorRepository;
    private final WhiteSourceComponentRepository whiteSourceComponentRepository;
    private final WhiteSourceClient whiteSourceClient;
    private final WhiteSourceSettings whiteSourceSettings;
    private final LibraryReferenceRepository libraryReferenceRepository;
    private final AsyncService dataRefreshService;


    @Autowired
    public WhiteSourceCollectorTask(TaskScheduler taskScheduler,
                                    WhiteSourceCollectorRepository whiteSourceCollectorRepository,
                                    WhiteSourceComponentRepository whiteSourceComponentRepository,
                                    WhiteSourceClient whiteSourceClient,
                                    WhiteSourceSettings whiteSourceSettings,
                                    LibraryReferenceRepository libraryReferenceRepository,
                                    AsyncService dataRefreshService) {
        super(taskScheduler, Constants.WHITE_SOURCE);
        this.whiteSourceCollectorRepository = whiteSourceCollectorRepository;
        this.whiteSourceComponentRepository = whiteSourceComponentRepository;
        this.whiteSourceClient = whiteSourceClient;
        this.whiteSourceSettings = whiteSourceSettings;
        this.libraryReferenceRepository = libraryReferenceRepository;
        this.dataRefreshService = dataRefreshService;
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
        CollectorMetric collectorMetric = new CollectorMetric();
        // Get existing projects from collector item repository
        List<WhiteSourceComponent> existingProjects = whiteSourceComponentRepository.findByCollectorIdIn(Stream.of(collector.getId()).collect(Collectors.toList()));
        collector.getWhiteSourceServers().forEach(instanceUrl -> {
            log(instanceUrl);
            whiteSourceSettings.getWhiteSourceServerSettings().forEach(whiteSourceServerSettings -> {
                String logMessage = "WhiteSourceCollector :";
                try {
                    // Get org details
                    WhitesourceOrg whitesourceOrg = whiteSourceClient.getOrgDetails(whiteSourceServerSettings);

                    // Get the change request log
                    Set<WhiteSourceChangeRequest> changeSet = getChangeRequests(collector, whitesourceOrg, whiteSourceServerSettings);

                    // Add new projects
                    List<WhiteSourceComponent> projects = addProjects(collector, whitesourceOrg, changeSet, new HashSet<>(existingProjects), collectorMetric, whiteSourceServerSettings);
                    // Get project vitals in a map
                    Map<String, WhiteSourceProjectVital> projectVitalMap = whiteSourceClient.getOrgProjectVitals(whitesourceOrg, whiteSourceServerSettings);

                    //Refresh scan data
                    int refreshCount = refreshData(collector, whitesourceOrg, changeSet, projectVitalMap, whiteSourceServerSettings);
                    collectorMetric.addInstanceCount(refreshCount);

                    logMessage = "SUCCESS, orgName=" + whitesourceOrg.getName() + ", fetched projects=" + projects.size() + ", New projects=" + collectorMetric.getAdded() + ", updated-projects=" + collectorMetric.getUpdated() + ", updated instance-data=" + collectorMetric.getInstanceCount();
                } catch (HygieiaException | ExecutionException he) {
                    logMessage = "EXCEPTION occurred, " + he.getClass().getCanonicalName();
                    LOG.error("Unexpected error occurred while collecting data for url=" + instanceUrl, he);
                } catch (InterruptedException e) {
                    LOG.error("InterruptedException error occurred while collecting data for url=" + instanceUrl, e);
                    Thread.currentThread().interrupt();
                } finally {
                    LOG.info(String.format("status=%s", logMessage));
                }
            });
        });
        long end = System.currentTimeMillis();
        long elapsedTime = (end - start) / 1000;

        LOG.info(String.format("WhitesourceCollectorTask:collector stop, totalProcessSeconds=%d,  totalFetchedProjects=%d, totalNewProjects=%d, totalUpdatedProjects=%d, totalUpdatedInstanceData=%d ",
                elapsedTime, collectorMetric.getFetched(), collectorMetric.getAdded(), collectorMetric.getUpdated(), collectorMetric.getInstanceCount()));
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
                                                   Set<WhiteSourceComponent> existingProjectsSet, CollectorMetric count,
                                                   WhiteSourceServerSettings whiteSourceServerSettings) {

        long timeGetProjects = System.currentTimeMillis();
        List<WhiteSourceComponent> projects = new ArrayList<>();
        try {
            if (collector.getLastExecuted() == 0 || checkForProjectChange(changeSet)) {
                List<WhiteSourceProduct> products = whiteSourceClient.getProducts(whitesourceOrg,
                        whiteSourceServerSettings);

                if (CollectionUtils.isEmpty(products)) {
                    return projects;
                }

                Iterable<List<WhiteSourceProduct>> partitions = Iterables.partition(products, products.size() / whiteSourceSettings.getThreadPoolSettings().getCorePoolSize());

                List<CompletableFuture<List<WhiteSourceComponent>>> threads = new ArrayList<>();
                for (List<WhiteSourceProduct> partition : partitions) {
                    CompletableFuture<List<WhiteSourceComponent>> thread = dataRefreshService.getProjectsForProductsAsync(whitesourceOrg, partition, whiteSourceServerSettings);
                    threads.add(thread);
                }
                CompletableFuture.allOf(Iterables.toArray(threads, CompletableFuture.class)).join();
                for (CompletableFuture<List<WhiteSourceComponent>> thread : threads) {
                    projects.addAll(thread.get());
                }
                timeGetProjects = System.currentTimeMillis() - timeGetProjects;
                LOG.info("WhitesourceCollectorTask: Time to get all projects: " + timeGetProjects);
                count.addFetched(projects.size());
                upsertProjects(projects, existingProjectsSet, collector, count);
            } else {
                LOG.info("WhitesourceCollectorTask: No need to fetch all projects: ");
            }
        } catch (HygieiaException | ExecutionException he) {
            LOG.error("Unexpected error occurred while collecting data for url=" + whiteSourceServerSettings.getInstanceUrl(), he);
        } catch (InterruptedException e) {
            LOG.error("InterruptedException error occurred while collecting data for url=" + whiteSourceServerSettings.getInstanceUrl(), e);
            Thread.currentThread().interrupt();
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
    private void upsertProjects(List<WhiteSourceComponent> projects, Set<WhiteSourceComponent> existingProjects, WhiteSourceCollector collector, CollectorMetric count) {
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
     * @param whitesourceOrg  Whitesource Org
     * @param changeRequests  Change Requests for the org
     * @param projectVitalMap Project Vital Map
     * @param serverSettings  Whitesource Server Setting
     */
    private int refreshData(WhiteSourceCollector collector, WhitesourceOrg whitesourceOrg, Set<WhiteSourceChangeRequest> changeRequests,
                            Map<String, WhiteSourceProjectVital> projectVitalMap, WhiteSourceServerSettings serverSettings) throws ExecutionException, InterruptedException {

        Map<WhiteSourceChangeRequest, WhiteSourceChangeRequest> changeRequestMap = changeRequests.stream().collect(Collectors.toMap(Function.identity(), Function.identity()));

        Set<WhiteSourceComponent> enabledProjects = getEnabledProjects(collector);

        Set<String> enabledProductTokens = enabledProjects.stream().map(WhiteSourceComponent::getProductToken).collect(Collectors.toSet());

        LOG.info("WhitesourceCollectorTask: Refresh Data - Total enabled products : " + (CollectionUtils.isEmpty(enabledProductTokens) ? 0 : enabledProductTokens.size()));
        LOG.info("WhitesourceCollectorTask: Refresh Data - Total enabled projects : " + (CollectionUtils.isEmpty(enabledProjects) ? 0 : enabledProjects.size()));

        long totalTime = 0;
        long startTime = System.currentTimeMillis();
        int count = 0;
        DataRefresh cumulativeDataRefresh = new DataRefresh();
        if (whiteSourceSettings.isOptimizeCollection()) {
            if (collector.getLastExecuted() > 0) {
                // (1) Collect for recently scanned projects first
                Set<WhiteSourceComponent> recentScannedProjects = getRecentScannedEnabledProjects(projectVitalMap, enabledProjects);
                LOG.info("WhitesourceCollectorTask: Refresh Data - Step 1 - Collecting Recently Scanned Projects. To be collected =" + recentScannedProjects.size());
                DataRefresh dataRefresh = updateData(enabledProjects, recentScannedProjects, projectVitalMap, serverSettings);

                count = dataRefresh.getCollectedProjects().size();
                LOG.info("WhitesourceCollectorTask: Refresh Data - Step 1 - Finished Collecting Recently Scanned Projects. Total collected =" + count + ". Time taken =" + (System.currentTimeMillis() - startTime));
                cumulativeDataRefresh.combine(dataRefresh);
                totalTime += (System.currentTimeMillis() - startTime);

                startTime = System.currentTimeMillis();

                // (2) Collect for any project that has a new alert
                Set<String> newAlertProjectTokens = whiteSourceClient.getAffectedProjectsForOrganization(whitesourceOrg, getHistoryTimestamp(collector), serverSettings);
                Set<WhiteSourceComponent> newAlertProjects = filterProjects(enabledProjects, cumulativeDataRefresh.getCollectedProjects(), newAlertProjectTokens);
                LOG.info("WhitesourceCollectorTask: Refresh Data - Step 2 - Collecting Projects with new alerts. To be collected =" + newAlertProjects.size());
                dataRefresh = updateData(enabledProjects, newAlertProjects, projectVitalMap, serverSettings);
                count = count + dataRefresh.getCollectedProjects().size();
                LOG.info("WhitesourceCollectorTask: Refresh Data - Step 2 - Finished Collecting Projects with new alert. Total collected =" + count + ". Time taken =" + (System.currentTimeMillis() - startTime));
                cumulativeDataRefresh.combine(dataRefresh);
                totalTime += (System.currentTimeMillis() - startTime);


                startTime = System.currentTimeMillis();

                // (3) Collect for any project that has related changes
                Set<String> changeRequestProjectTokens = getAffectedProjectsFromChanges(changeRequests, whitesourceOrg.getName());
                Set<WhiteSourceComponent> changeRequestProjects = filterProjects(enabledProjects, cumulativeDataRefresh.getCollectedProjects(), changeRequestProjectTokens);
                LOG.info("WhitesourceCollectorTask: Refresh Data - Step 3 - Collecting Projects in change log. To be collected =" + changeRequestProjects.size());
                dataRefresh = updateData(enabledProjects, changeRequestProjects, projectVitalMap, serverSettings);
                count = count + dataRefresh.getCollectedProjects().size();
                LOG.info("WhitesourceCollectorTask: Refresh Data - Step 3 - Finished Collecting Projects in change log. Total collected =" + count + ". Time taken =" + (System.currentTimeMillis() - startTime));
                cumulativeDataRefresh.combine(dataRefresh);

                LOG.info("WhitesourceCollectorTask: Refresh Data - High Priority Changes - Total projects : " + cumulativeDataRefresh.getCollectedProjects().size() + ". Time taken =" + (System.currentTimeMillis() - startTime));
                totalTime += (System.currentTimeMillis() - startTime);

                startTime = System.currentTimeMillis();
            }

            // (4) Collect everything enabled that is not collected yet in (1) through (3)
            Set<WhiteSourceComponent> remainingProjects = enabledProjects.stream().filter(e -> !cumulativeDataRefresh.getCollectedProjects().contains(e)).collect(Collectors.toSet());
            LOG.info("WhitesourceCollectorTask: Refresh Data - Step 4 - Collecting all remaining projects. To be collected =" + remainingProjects.size());
            DataRefresh dataRefresh = updateData(enabledProjects, remainingProjects, projectVitalMap, serverSettings);
            count = count + dataRefresh.getCollectedProjects().size();
            LOG.info("WhitesourceCollectorTask: Refresh Data - Step 4 - Finished Collecting all remaining Projects. Total collected =" + count + ". Time taken =" + (System.currentTimeMillis() - startTime));
            cumulativeDataRefresh.combine(dataRefresh);
            totalTime += (System.currentTimeMillis() - startTime);
        }
        // (5) Normal collection - Collect everything.
        // In optimized mode, projects left to collect at this point were due to exceptions and most probably due to whitesource api calls timing out.
        startTime = System.currentTimeMillis();
        Set<WhiteSourceComponent> remainingProjects = enabledProjects.stream().filter(e -> !cumulativeDataRefresh.getCollectedProjects().contains(e)).collect(Collectors.toSet());
        if (!CollectionUtils.isEmpty(remainingProjects)) {
            // Need to partition products
            LOG.info("WhitesourceCollectorTask: Refresh Data - Step 5 - Collecting all remaining projects that failed. To be collected =" + remainingProjects.size());
            int partitionCount = remainingProjects.size() >= whiteSourceSettings.getThreadPoolSettings().getCorePoolSize() ? whiteSourceSettings.getThreadPoolSettings().getCorePoolSize() : 1;
            Iterable<List<WhiteSourceComponent>> partitions = Iterables.partition(remainingProjects, remainingProjects.size() / partitionCount);
            List<CompletableFuture<DataRefresh>> threads = new ArrayList<>();
            for (List<WhiteSourceComponent> partition : partitions) {
                CompletableFuture<DataRefresh> thread = dataRefreshService.getAndUpdateDataByProjectAsync(partition, projectVitalMap, serverSettings);
                threads.add(thread);
            }
            CompletableFuture.allOf(Iterables.toArray(threads, CompletableFuture.class)).join();
            for (CompletableFuture<DataRefresh> thread : threads) {
                cumulativeDataRefresh.combine(thread.get());
            }
        }
        totalTime += (System.currentTimeMillis() - startTime);
        LOG.info("WhitesourceCollectorTask: Finished Collected All Steps. Total Projects Collected : " + cumulativeDataRefresh.getCollectedProjects().size() + ". Time taken =" + totalTime);

        startTime = System.currentTimeMillis();
        saveLibraryReferenceData(cumulativeDataRefresh.getLibraryReferenceMap());

        LOG.info("WhitesourceCollectorTask: Saved refernece data. Total Libraries : " + cumulativeDataRefresh.getLibraryReferenceMap().size() + ". Time taken =" + (System.currentTimeMillis() - startTime));
        return count;
    }

    /**
     * Save Library Reference
     * @param referenceMap referenceMap
     */
    private void saveLibraryReferenceData(Map<String, LibraryPolicyReference> referenceMap) {
        Collection<LibraryPolicyReference> referenceList = referenceMap.values();
        referenceList.forEach(r -> {
            LibraryPolicyReference existing = libraryReferenceRepository.findByLibraryNameAndOrgName(r.getLibraryName(),r.getOrgName());
            if (existing == null) {
                libraryReferenceRepository.save(r);
            } else {
                List<WhiteSourceComponent> existingProjects = existing.getProjectReferences();
                List<WhiteSourceComponent> newProjects = r.getProjectReferences();
                List<WhiteSourceComponent> missing = newProjects.stream().filter(n-> !existingProjects.contains(n)).collect(Collectors.toList());
                if (!CollectionUtils.isEmpty(missing)) {
                    existingProjects.addAll(missing);
                    existing.setProjectReferences(existingProjects);
                    existing.setLastUpdated(System.currentTimeMillis());
                    libraryReferenceRepository.save(existing);
                }
            }
        });
    }

    /**
     * Returns a set of white source components/projects from enabled project list that are not all ready collected
     *
     * @param enabledProjects          all enabled projects
     * @param alreadyCollectedProjects projects that are already collected
     * @param projectTokens            project tokens to filter
     * @return Set of whitesource components/projects
     */
    private static Set<WhiteSourceComponent> filterProjects(Set<WhiteSourceComponent> enabledProjects, Set<WhiteSourceComponent> alreadyCollectedProjects, Set<String> projectTokens) {
        return enabledProjects.stream()
                .filter(e -> projectTokens.contains(e.getProjectToken()))
                .filter(e -> !alreadyCollectedProjects.contains(e)) //not collected yet
                .collect(Collectors.toSet());
    }

    /**
     * Updates data via calling DataRefresh Service in multi threads
     *
     * @param enabledProjects   Enabled Projects
     * @param projectsToCollect Projects to collect
     * @param projectVitalMap   Project Vital Map
     * @param serverSettings    Whitesource Server Setting
     * @throws ExecutionException   execution exception
     * @throws InterruptedException interrupted exception
     */
    private DataRefresh updateData(Set<WhiteSourceComponent> enabledProjects, Set<WhiteSourceComponent> projectsToCollect,
                                   Map<String, WhiteSourceProjectVital> projectVitalMap,
                                   WhiteSourceServerSettings serverSettings) throws ExecutionException, InterruptedException {

        DataRefresh dataRefresh = new DataRefresh();
        if (CollectionUtils.isEmpty(projectsToCollect)) {
            return dataRefresh;
        }

        Set<String> productTokensToCollect = projectsToCollect.stream()
                .map(WhiteSourceComponent::getProductToken)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        LOG.info("WhitesourceCollectorTask: updateData: For " + projectsToCollect.size() + " projects, unique product tokens to be collected =" + productTokensToCollect.size());

        //The following shouldn't happen, but still....
        if (CollectionUtils.isEmpty(productTokensToCollect)) {
            return dataRefresh;
        }
        // Need to partition products
        int partitionCount = productTokensToCollect.size() >= whiteSourceSettings.getThreadPoolSettings().getCorePoolSize() ? whiteSourceSettings.getThreadPoolSettings().getCorePoolSize() : 1;

        Iterable<List<String>> partitions = Iterables.partition(productTokensToCollect, productTokensToCollect.size() / partitionCount);
        List<CompletableFuture<DataRefresh>> threads = new ArrayList<>();
        for (List<String> partition : partitions) {
            CompletableFuture<DataRefresh> thread = dataRefreshService.getAndUpdateDataByProductAsync(partition, enabledProjects, projectVitalMap, serverSettings);
            threads.add(thread);
        }
        CompletableFuture.allOf(Iterables.toArray(threads, CompletableFuture.class)).join();

        for (CompletableFuture<DataRefresh> thread : threads) {
            dataRefresh.combine(thread.get());
        }
        return dataRefresh;
    }


    /**
     * Based on last update times in project vitals map, return a list of projects that are updated since enabled
     * component was last updated mius the offset
     *
     * @param projectVitalMap project vital map
     * @param enabledProjects enabled project
     * @return set of project tokens
     */
    private Set<WhiteSourceComponent> getRecentScannedEnabledProjects(Map<String, WhiteSourceProjectVital> projectVitalMap, Set<WhiteSourceComponent> enabledProjects) {
        return enabledProjects
                .stream()
                .filter(e -> Objects.nonNull(projectVitalMap.get(e.getProjectToken())) &&
                        (e.getLastUpdated() - whiteSourceSettings.getOffSet() < projectVitalMap.get(e.getProjectToken()).getLastUpdateDate()))
                .collect(Collectors.toSet());
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
     * Check if any changes to a project (usually a create, update, delete)
     * return true if project change.
     */

    private static boolean checkForProjectChange(Set<WhiteSourceChangeRequest> changes) {
//        return true;  // for testing

        if (CollectionUtils.isEmpty(changes)) {
            return false;
        }
        return changes.stream().anyMatch(wcr -> !StringUtils.isEmpty(wcr.getProjectName()));
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

    boolean processRecord(String value) {

        if(CollectionUtils.isEmpty(whiteSourceSettings.getSearchPatterns())) return true;
        for(String pattern : whiteSourceSettings.getSearchPatterns()) {
            if(value.matches(pattern)) return true;
        }
        return false;
    }

}
