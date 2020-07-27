package com.capitalone.dashboard.collector;


import com.capitalone.dashboard.model.CollectionError;
import com.capitalone.dashboard.model.LibraryPolicyResult;
import com.capitalone.dashboard.model.WhiteSourceCollector;
import com.capitalone.dashboard.model.WhiteSourceComponent;
import com.capitalone.dashboard.model.WhiteSourceProduct;
import com.capitalone.dashboard.repository.BaseCollectorRepository;
import com.capitalone.dashboard.repository.LibraryPolicyResultsRepository;
import com.capitalone.dashboard.repository.WhiteSourceCollectorRepository;
import com.capitalone.dashboard.repository.WhiteSourceComponentRepository;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
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

    @Autowired
    public WhiteSourceCollectorTask(TaskScheduler taskScheduler,
                                    WhiteSourceCollectorRepository whiteSourceCollectorRepository,
                                    WhiteSourceComponentRepository whiteSourceComponentRepository,
                                    LibraryPolicyResultsRepository libraryPolicyResultsRepository,
                                    WhiteSourceClient whiteSourceClient,
                                    WhiteSourceSettings whiteSourceSettings) {
        super(taskScheduler, "WhiteSource");
        this.whiteSourceCollectorRepository = whiteSourceCollectorRepository;
        this.whiteSourceComponentRepository = whiteSourceComponentRepository;
        this.libraryPolicyResultsRepository = libraryPolicyResultsRepository;
        this.whiteSourceClient = whiteSourceClient;
        this.whiteSourceSettings = whiteSourceSettings;
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
        collector.getWhiteSourceServers().forEach(instanceUrl -> {
            logBanner(instanceUrl);
            whiteSourceSettings.getOrgTokens().forEach(orgToken -> {
                String orgName = whiteSourceClient.getOrgDetails(instanceUrl, orgToken);
                List<WhiteSourceProduct> products = whiteSourceClient.getProducts(instanceUrl, orgToken);
                List<WhiteSourceComponent> projects = new ArrayList<>();
                for (WhiteSourceProduct product : products) {
                    projects.addAll(whiteSourceClient.getAllProjectsForProduct(instanceUrl, product, orgToken, orgName));
                }
                log("Fetched projects for Organization : " + orgName + ":  total projects :" + projects.size(), start);
                List<WhiteSourceComponent> existingComponents = whiteSourceComponentRepository.findByCollectorIdIn(Stream.of(collector.getId()).collect(Collectors.toList()));
                addNewApplications(projects, existingComponents, collector);
                refreshData(enabledApplications(collector), getRequestRateLimit(),
                        getRequestRateLimitTimeWindow(), getWaitTime(), instanceUrl);
            });
            log("Finished", start);
        });
    }

    private void refreshData(List<WhiteSourceComponent> enabledProjects, int requestRateLimit, long requestRateLimitTimeWindow, long waitTime, String instanceUrl) {
        long start = System.currentTimeMillis();
        int rateCount = 0;
        long startTime = System.currentTimeMillis();
        List<WhiteSourceComponent> exception429TooManyRequestsComponentsList = new ArrayList<>();
        AtomicInteger count = new AtomicInteger();
        for (WhiteSourceComponent component : enabledProjects) {
            if (component.isEnabled()) {
                if (component.checkErrorOrReset(whiteSourceSettings.getErrorResetWindow(), whiteSourceSettings.getErrorThreshold())) {
                    try {
                        LibraryPolicyResult libraryPolicyResult = whiteSourceClient.getProjectAlerts(instanceUrl, component);
                        LibraryPolicyResult libraryPolicyResultExisting = getLibraryPolicyResultExisting(component.getId());
                        if(Objects.nonNull(libraryPolicyResult)){
                            libraryPolicyResult.setCollectorItemId(component.getId());
                            count.getAndIncrement();
                            if (Objects.nonNull(libraryPolicyResultExisting)) {
                                libraryPolicyResult.setId(libraryPolicyResultExisting.getId());
                            }
                            libraryPolicyResultsRepository.save(libraryPolicyResult);
                        }
                   } catch (HttpStatusCodeException hc) {
                        exception429TooManyRequestsComponentsList.add(component);
                        LOG.error("Error fetching details for:" + component.getDescription(), hc);
                        CollectionError error = new CollectionError(hc.getStatusCode().toString(), hc.getMessage());
                        component.getErrors().add(error);
                    } catch (RestClientException re) {
                        LOG.error("Error fetching details for:" + component.getDescription(), re);
                        CollectionError error = new CollectionError(CollectionError.UNKNOWN_HOST, component.getInstanceUrl());
                        component.getErrors().add(error);
                    }
                    component.setLastUpdated(System.currentTimeMillis());
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
        log("Updated", start, count.get());

    }

    private LibraryPolicyResult getLibraryPolicyResultExisting(ObjectId collectorItemId) {
        return libraryPolicyResultsRepository.findByCollectorItemId(collectorItemId);
    }

    private List<WhiteSourceComponent> enabledApplications(WhiteSourceCollector collector) {
        return whiteSourceComponentRepository.findEnabledComponents(collector.getId());
    }

    private void addNewApplications(List<WhiteSourceComponent> applications, List<WhiteSourceComponent> existingApplications, WhiteSourceCollector collector) {
        long start = System.currentTimeMillis();
        int newCount = 0;
        int updatedCount = 0;
        for (WhiteSourceComponent application : applications) {
            int matchIndex = existingApplications.indexOf(application);
            WhiteSourceComponent matching = matchIndex >= 0 ? existingApplications.get(matchIndex) : null;
            if (matching == null) {
                application.setCollectorId(collector.getId());
                application.setCollector(collector);
                application.setEnabled(false);
                application.setDescription(application.getProjectName());
                application.setPushed(false);
                whiteSourceComponentRepository.save(application);
                newCount++;
            } else {
                if (Objects.isNull(matching.getProductToken())) {
                    matching.setProductToken(application.getProductToken());
                }
                if (Objects.isNull(matching.getProjectToken())) {
                    matching.setProjectToken(application.getProjectToken());
                }
                whiteSourceComponentRepository.save(matching);
                updatedCount++;
            }
        }
        log("New projects", start, newCount);
        log("Updated projects", start, updatedCount);
    }
}
