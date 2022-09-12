package com.capitalone.dashboard.collector;

import com.capitalone.dashboard.model.DataRefresh;
import com.capitalone.dashboard.model.LibraryPolicyReference;
import com.capitalone.dashboard.model.LibraryPolicyResult;
import com.capitalone.dashboard.model.WhiteSourceComponent;
import com.capitalone.dashboard.model.WhiteSourceProduct;
import com.capitalone.dashboard.model.WhiteSourceProjectVital;
import com.capitalone.dashboard.model.WhitesourceOrg;
import com.capitalone.dashboard.repository.LibraryPolicyResultsRepository;
import com.capitalone.dashboard.repository.LibraryReferenceRepository;
import com.capitalone.dashboard.repository.WhiteSourceComponentRepository;
import com.capitalone.dashboard.settings.WhiteSourceServerSettings;
import com.capitalone.dashboard.settings.WhiteSourceSettings;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
public class AsyncService {
    private static final Log LOG = LogFactory.getLog(AsyncService.class);
    private final WhiteSourceComponentRepository whiteSourceComponentRepository;
    private final LibraryPolicyResultsRepository libraryPolicyResultsRepository;
    private final WhiteSourceClient whiteSourceClient;

    @Autowired
    public AsyncService(WhiteSourceComponentRepository whiteSourceComponentRepository,
                        LibraryPolicyResultsRepository libraryPolicyResultsRepository,
                        WhiteSourceClient whiteSourceClient) {
        this.whiteSourceComponentRepository = whiteSourceComponentRepository;
        this.libraryPolicyResultsRepository = libraryPolicyResultsRepository;
        this.whiteSourceClient = whiteSourceClient;
    }

    /**
     * Async method to get projects for a product
     *
     * @param whitesourceOrg            whitesource org
     * @param products                  products
     * @param whiteSourceServerSettings whitesource server settings
     * @return CompletableFuture of list of  WhitesourceComponents
     */
    @Async("WSCollectorExecutor")
    public CompletableFuture<List<WhiteSourceComponent>> getProjectsForProductsAsync(WhitesourceOrg whitesourceOrg, List<WhiteSourceProduct> products, WhiteSourceServerSettings whiteSourceServerSettings) {
        List<WhiteSourceComponent> projects = new ArrayList<>();
        products.stream().map(product -> whiteSourceClient.getAllProjectsForProduct(whitesourceOrg, product,
                whiteSourceServerSettings)).forEach(projects::addAll);
        return CompletableFuture.completedFuture(projects);
    }

    /**
     * Async method to get product alerts and update library policy results
     *
     * @param productTokensToCollect products to collect
     * @param enabledProjects        set of enabled projects
     * @param projectVitalMap        project vital map
     * @param serverSettings         whitesource server setting
     * @return CompletableFuture of DataRefresh
     */
    @Async("WSCollectorExecutor")
    public CompletableFuture<DataRefresh> getAndUpdateDataByProductAsync(List<String> productTokensToCollect, Set<WhiteSourceComponent> enabledProjects, Map<String, WhiteSourceProjectVital> projectVitalMap, WhiteSourceServerSettings serverSettings) {
        AtomicInteger counter = new AtomicInteger();
        Map<String, LibraryPolicyResult> libraryPolicyResultMap = new HashMap<>();
        Map<String, LibraryPolicyReference> libraryLookUp = new HashMap<>();
        int totalCount = productTokensToCollect.size();
        long startTime = System.currentTimeMillis();
        productTokensToCollect
                .stream()
                .map(ept -> {
                    LOG.info("Collecting alerts for Product Token " + ept + ": " + counter.incrementAndGet() + "  of " + totalCount);
                    return whiteSourceClient.getProductAlerts(ept, enabledProjects, projectVitalMap, serverSettings);
                })
                .forEach(libraryPolicyResultMap::putAll);

        //only save enabled projects
        Set<WhiteSourceComponent> collected = enabledProjects.stream().filter(e -> libraryPolicyResultMap.containsKey(e.getProjectToken())).collect(Collectors.toSet());
        DataRefresh dataRefresh = new DataRefresh(collected, libraryLookUp);
        collected.forEach(project -> {
            saveScanData(project, libraryPolicyResultMap);
//            Map<String, LibraryPolicyReference> referenceMap = buildLibraryReference(project, libraryPolicyResultMap);
//            dataRefresh.addLibraryReference(referenceMap);
        });
        long endTime = System.currentTimeMillis();
        LOG.info(String.format("getAndUpdateByProductAsync :: Duration %d", startTime-endTime));
        return CompletableFuture.completedFuture(dataRefresh);
    }

    /**
     * Build Library Policy Reference
     *
     * @param project                whitesource project
     * @param libraryPolicyResultMap library policy results
     * @return library reference map
     */
    private static Map<String, LibraryPolicyReference> buildLibraryReference(WhiteSourceComponent project, Map<String, LibraryPolicyResult> libraryPolicyResultMap) {
        LibraryPolicyResult libraryPolicyResult = libraryPolicyResultMap.get(project.getProjectToken());
        Collection<Set<LibraryPolicyResult.Threat>> libs = libraryPolicyResult.getThreats().values();
        Map<String, LibraryPolicyReference> referenceMap = new HashMap<>();

        libs.forEach(lib -> lib.stream()
                .map(LibraryPolicyResult.Threat::getComponents)
                .forEach(components -> components.stream()
                        .map(AsyncService::getComponentName)
                        .forEach(name -> {
                            LibraryPolicyReference lpr = referenceMap.get(name);
                            if (lpr == null) {
                                lpr = new LibraryPolicyReference();
                                lpr.setLibraryName(name);
                            }
                            List<WhiteSourceComponent> projects = lpr.getProjectReferences();
                            projects.add(project);
                            lpr.setProjectReferences(projects);
                            lpr.setLastUpdated(System.currentTimeMillis());
                            referenceMap.put(name, lpr);
                        })
                ));
        return referenceMap;
    }

    /**
     * Async method to get project alerts and save library policy result
     *
     * @param projects        set of projects to collect
     * @param projectVitalMap project vital map
     * @param serverSettings  server settings
     * @return CompletableFuture of DataRefresh
     */
    @Async("WSCollectorExecutor")
    public CompletableFuture<DataRefresh> getAndUpdateDataByProjectAsync(List<WhiteSourceComponent> projects, Map<String, WhiteSourceProjectVital> projectVitalMap, WhiteSourceServerSettings serverSettings) {
        AtomicInteger counter = new AtomicInteger();
        Map<String, LibraryPolicyResult> libraryPolicyResultMap = new HashMap<>();
        Map<String, LibraryPolicyReference> libraryLookUp = new HashMap<>();
        int totalCount = projects.size();
        projects
                .stream()
                .map(project -> {
                    LOG.info("Collecting alerts for Project Token " + project.getProjectToken() + ": " + counter.incrementAndGet() + "  of " + totalCount);
                    LibraryPolicyResult libraryPolicyResult = whiteSourceClient.getProjectAlerts(project, projectVitalMap.get(project.getProjectToken()), serverSettings, null);
                    Map<String, LibraryPolicyResult> lpr = new HashMap<>();
                    lpr.put(project.getProjectToken(), libraryPolicyResult);
                    return lpr;
                })
                .forEach(libraryPolicyResultMap::putAll);

        DataRefresh dataRefresh = new DataRefresh(new HashSet<>(projects), libraryLookUp);
        projects.forEach(project -> {
            saveScanData(project, libraryPolicyResultMap);
//            Map<String, LibraryPolicyReference> referenceMap = buildLibraryReference(project, libraryPolicyResultMap);
//            dataRefresh.addLibraryReference(referenceMap);
        });
        return CompletableFuture.completedFuture(dataRefresh);
    }

    /**
     * Save scan data
     *
     * @param project                whitesource project
     * @param libraryPolicyResultMap library policy results map
     */
    private void saveScanData(WhiteSourceComponent project, Map<String, LibraryPolicyResult> libraryPolicyResultMap) {
        LibraryPolicyResult libraryPolicyResult = libraryPolicyResultMap.get(project.getProjectToken());

        if (Objects.isNull(libraryPolicyResult)) return;
        LibraryPolicyResult libraryPolicyResultExisting = getLibraryPolicyResultExisting(project.getId(), libraryPolicyResult.getEvaluationTimestamp());
        if (Objects.nonNull(libraryPolicyResultExisting)) {
            libraryPolicyResult.setId(libraryPolicyResultExisting.getId());
        }
        libraryPolicyResult.setCollectorItemId(project.getId());
        libraryPolicyResultsRepository.save(libraryPolicyResult);
        project.setLastUpdated(System.currentTimeMillis());
        whiteSourceComponentRepository.save(project);
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


    private static String getComponentName(String component) {
        return component.contains("#") ? component.substring(0, component.indexOf('#')) : "";
    }
}
