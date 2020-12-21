package com.capitalone.dashboard.collector;

import com.capitalone.dashboard.misc.HygieiaException;
import com.capitalone.dashboard.model.LibraryPolicyResult;
import com.capitalone.dashboard.model.WhiteSourceChangeRequest;
import com.capitalone.dashboard.model.WhiteSourceComponent;
import com.capitalone.dashboard.model.WhiteSourceProduct;
import com.capitalone.dashboard.model.WhiteSourceProjectVital;
import com.capitalone.dashboard.model.WhiteSourceServerSettings;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface WhiteSourceClient {
    List<WhiteSourceProduct> getProducts(String instanceUrl,String orgToken,String orgName,WhiteSourceServerSettings serverSettings) throws HygieiaException;
    List<WhiteSourceComponent> getAllProjectsForProduct(String instanceUrl,WhiteSourceProduct product,String orgToken,String orgName,WhiteSourceServerSettings serverSettings);
    LibraryPolicyResult getProjectInventory(String instanceUrl, WhiteSourceComponent whiteSourceComponent,WhiteSourceServerSettings serverSettings);
    Map<String, LibraryPolicyResult> getProductAlerts(String instanceUrl, String orgName, String productToken, Map<String, WhiteSourceProjectVital> projectVitalMap, WhiteSourceServerSettings serverSettings);
    LibraryPolicyResult getProjectAlerts(String instanceUrl, WhiteSourceComponent whiteSourceComponent, WhiteSourceProjectVital projectVital, WhiteSourceServerSettings serverSettings);
    String getOrgDetails(String instanceUrl, WhiteSourceServerSettings serverSettings) throws HygieiaException;
    List<WhiteSourceChangeRequest> getChangeRequestLog(String instanceUrl, String orgToken, String orgName,long collectorLastUpdatedTime,WhiteSourceServerSettings serverSettings);
    void getEvaluationTimeStamp(LibraryPolicyResult libraryPolicyResult, JSONObject projectVitalsObject, WhiteSourceServerSettings serverSettings);
    void transform(LibraryPolicyResult libraryPolicyResult, JSONArray alerts);
    Map<String, WhiteSourceProjectVital> getOrgProjectVitals(String instanceUrl, String orgToken, String orgName, WhiteSourceServerSettings whiteSourceServerSettings);
    Set<String> getAffectedProjectsForOrganization(String instanceUrl, String orgName, String orgToken, long historyTimestamp, WhiteSourceServerSettings serverSettings);
}
