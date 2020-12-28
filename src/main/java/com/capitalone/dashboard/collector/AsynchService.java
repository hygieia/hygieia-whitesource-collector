package com.capitalone.dashboard.collector;

import com.capitalone.dashboard.model.DataRefresh;
import com.capitalone.dashboard.model.LibraryPolicyReference;
import com.capitalone.dashboard.model.LibraryPolicyResult;
import com.capitalone.dashboard.model.WhiteSourceChangeRequest;
import com.capitalone.dashboard.model.WhiteSourceComponent;
import com.capitalone.dashboard.model.WhiteSourceProduct;
import com.capitalone.dashboard.model.WhiteSourceProjectVital;
import com.capitalone.dashboard.model.WhiteSourceServerSettings;
import com.capitalone.dashboard.model.WhitesourceOrg;
import com.capitalone.dashboard.repository.LibraryPolicyResultsRepository;
import com.capitalone.dashboard.repository.LibraryReferenceRepository;
import com.capitalone.dashboard.repository.WhiteSourceComponentRepository;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AsynchService {
    private static final Log LOG = LogFactory.getLog(AsynchService.class);
    private final WhiteSourceComponentRepository whiteSourceComponentRepository;
    private final LibraryPolicyResultsRepository libraryPolicyResultsRepository;
    private final WhiteSourceClient whiteSourceClient;
    private final WhiteSourceSettings whiteSourceSettings;
    private final LibraryReferenceRepository libraryReferenceRepository;

    @Autowired
    public AsynchService(WhiteSourceComponentRepository whiteSourceComponentRepository,
                         LibraryPolicyResultsRepository libraryPolicyResultsRepository,
                         WhiteSourceClient whiteSourceClient, WhiteSourceSettings whiteSourceSettings,
                         LibraryReferenceRepository libraryReferenceRepository) {
        this.whiteSourceComponentRepository = whiteSourceComponentRepository;
        this.libraryPolicyResultsRepository = libraryPolicyResultsRepository;
        this.whiteSourceClient = whiteSourceClient;
        this.whiteSourceSettings = whiteSourceSettings;
        this.libraryReferenceRepository = libraryReferenceRepository;
    }

    @Async("WSCollectorExecutor")
    public CompletableFuture<List<WhiteSourceComponent>> getProjectsForProductsAsync(WhitesourceOrg whitesourceOrg, List<WhiteSourceProduct> products, WhiteSourceServerSettings whiteSourceServerSettings) {
        List<WhiteSourceComponent> projects = new ArrayList<>();
        products.stream().map(product -> whiteSourceClient.getAllProjectsForProduct(whitesourceOrg, product,
                whiteSourceServerSettings)).forEach(projects::addAll);
        return CompletableFuture.completedFuture(projects);
    }

    @Async("WSCollectorExecutor")
    public CompletableFuture<DataRefresh> getAndUpdateDataAsynch(WhitesourceOrg whitesourceOrg, List<String> productTokensToCollect, Set<WhiteSourceComponent> enabledProjects,
                                                                 Map<String, WhiteSourceProjectVital> projectVitalMap,
                                                                 Map<WhiteSourceChangeRequest, WhiteSourceChangeRequest> changeRequestMap,
                                                                 WhiteSourceServerSettings serverSettings) {
        AtomicInteger counter = new AtomicInteger();
        Map<String, LibraryPolicyResult> libraryPolicyResultMap = new HashMap<>();
        Map<String, LibraryPolicyReference> libraryLookUp = new HashMap<>();
        int totalCount = productTokensToCollect.size();
        productTokensToCollect
                .stream()
                .map(ept -> {
                    counter.getAndIncrement();
                    LOG.info("Collecting alerts for Product Token " + ept + ": " + counter.get() + "  of " + totalCount);
                    return whiteSourceClient.getProductAlerts(whitesourceOrg, ept, projectVitalMap, serverSettings);
                })
                .forEach(libraryPolicyResultMap::putAll);

        //only save enabled projects
        Set<WhiteSourceComponent> collected = enabledProjects.stream().filter(e -> libraryPolicyResultMap.containsKey(e.getProjectToken())).collect(Collectors.toSet());
        collected.forEach(project -> {
            saveScanData(whitesourceOrg, project, libraryPolicyResultMap, libraryLookUp, changeRequestMap);
            counter.getAndIncrement();
        });
        return CompletableFuture.completedFuture(new DataRefresh(collected, libraryLookUp));
    }

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
        libs.forEach(lib -> lib.stream().map(LibraryPolicyResult.Threat::getComponents).forEach(components -> {
            // Add or update lprs
            components.stream().map(AsynchService::getComponentName).forEach(name -> {
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

    private static String getComponentName(String component) {
        return component.contains("#") ? component.substring(0, component.indexOf('#')) : "";
    }
}
