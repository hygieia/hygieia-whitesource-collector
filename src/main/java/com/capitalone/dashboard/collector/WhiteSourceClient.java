package com.capitalone.dashboard.collector;

import com.capitalone.dashboard.misc.HygieiaException;
import com.capitalone.dashboard.model.WhiteSourceChangeRequest;
import com.capitalone.dashboard.model.WhiteSourceComponent;
import com.capitalone.dashboard.model.LibraryPolicyResult;
import com.capitalone.dashboard.model.WhiteSourceProduct;
import com.capitalone.dashboard.model.WhiteSourceServerSettings;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.util.List;

public interface WhiteSourceClient {
    List<WhiteSourceProduct> getProducts(String instanceUrl,String orgToken,String orgName,WhiteSourceServerSettings serverSettings) throws HygieiaException;
    List<WhiteSourceComponent> getAllProjectsForProduct(String instanceUrl,WhiteSourceProduct product,String orgToken,String orgName,WhiteSourceServerSettings serverSettings);
    LibraryPolicyResult getProjectInventory(String instanceUrl, WhiteSourceComponent whiteSourceComponent,WhiteSourceServerSettings serverSettings);
    LibraryPolicyResult getProjectAlerts(String instanceUrl, WhiteSourceComponent whiteSourceComponent, WhiteSourceServerSettings serverSettings);
    String getOrgDetails(String instanceUrl, WhiteSourceServerSettings serverSettings) throws HygieiaException;
    List<WhiteSourceChangeRequest> getChangeRequestLog(String instanceUrl, String orgToken, String orgName,long collectorLastUpdatedTime,WhiteSourceServerSettings serverSettings);
    void getEvaluationTimeStamp(LibraryPolicyResult libraryPolicyResult, JSONObject projectVitalsObject);
    void transform(LibraryPolicyResult libraryPolicyResult, JSONArray alerts);

}
