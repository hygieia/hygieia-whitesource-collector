package com.capitalone.dashboard.collector;

import com.capitalone.dashboard.misc.HygieiaException;
import com.capitalone.dashboard.model.LibraryPolicyResult;
import com.capitalone.dashboard.model.WhiteSourceChangeRequest;
import com.capitalone.dashboard.model.WhiteSourceComponent;
import com.capitalone.dashboard.model.WhiteSourceProduct;
import com.capitalone.dashboard.model.WhiteSourceProjectVital;
import com.capitalone.dashboard.model.WhiteSourceServerSettings;
import com.capitalone.dashboard.model.WhitesourceOrg;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

public interface WhiteSourceClient {
    List<WhiteSourceProduct> getProducts(WhitesourceOrg whitesourceOrg,WhiteSourceServerSettings serverSettings) throws HygieiaException;
    Map<String, LibraryPolicyResult> getProductAlerts(WhitesourceOrg whitesourceOrg, String productToken, Map<String, WhiteSourceProjectVital> projectVitalMap, WhiteSourceServerSettings serverSettings);
    LibraryPolicyResult getProjectAlerts(WhiteSourceComponent whiteSourceComponent, WhiteSourceProjectVital projectVital, WhiteSourceServerSettings serverSettings);
    WhitesourceOrg getOrgDetails(WhiteSourceServerSettings serverSettings) throws HygieiaException;
    List<WhiteSourceChangeRequest> getChangeRequestLog(WhitesourceOrg whitesourceOrg, long collectorLastUpdatedTime, WhiteSourceServerSettings serverSettings) throws HygieiaException;
    Map<String, WhiteSourceProjectVital> getOrgProjectVitals(WhitesourceOrg whitesourceOrg, WhiteSourceServerSettings whiteSourceServerSettings) throws HygieiaException;
    Set<String> getAffectedProjectsForOrganization(WhitesourceOrg whitesourceOrg, long historyTimestamp, WhiteSourceServerSettings serverSettings) throws ExecutionException, InterruptedException;
    List<WhiteSourceComponent> getAllProjectsForProduct(WhitesourceOrg whitesourceOrg, WhiteSourceProduct product, WhiteSourceServerSettings serverSettings);
}
