package com.capitalone.dashboard.collector;

import com.capitalone.dashboard.client.RestClient;
import com.capitalone.dashboard.client.RestOperationsSupplier;
import com.capitalone.dashboard.config.CollectorTestConfig;
import com.capitalone.dashboard.config.TestConstants;
import com.capitalone.dashboard.config.TestMongoServerConfig;
import com.capitalone.dashboard.config.TestRestConfig;
import com.capitalone.dashboard.model.LibraryPolicyResult;
import com.capitalone.dashboard.model.WhiteSourceCollector;
import com.capitalone.dashboard.model.WhiteSourceComponent;
import com.capitalone.dashboard.repository.CollectorItemRepository;
import com.capitalone.dashboard.repository.ComponentRepository;
import com.capitalone.dashboard.repository.LibraryPolicyResultsRepository;
import com.capitalone.dashboard.repository.LibraryReferenceRepository;
import com.capitalone.dashboard.repository.WhiteSourceCollectorRepository;
import com.capitalone.dashboard.repository.WhiteSourceComponentRepository;
import com.capitalone.dashboard.settings.WhiteSourceSettings;
import com.capitalone.dashboard.testutil.GsonUtil;
import com.capitalone.dashboard.utils.TestUtils;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;


@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(SpringJUnit4ClassRunner.class)
@PowerMockIgnore("jdk.internal.reflect.*")
@ContextConfiguration(classes = {TestRestConfig.class, TestMongoServerConfig.class, CollectorTestConfig.class})
@ComponentScan("com.capitalone.dashboard.client")
@PrepareForTest(fullyQualifiedNames = "com.capitalone.dashboard.*")
public class WhiteSourceCollectorTaskTest {
	
	/*
	 * Power mock is not compatible with JUnit5
	 * https://aggarwal-rohan17.medium.com/simplifying-junit-mockito-and-powermock-d1392059ce87
	 * */

    @Autowired
    private WhiteSourceCollectorRepository whiteSourceCollectorRepository;

    @Autowired
    private WhiteSourceComponentRepository whiteSourceComponentRepository;

    @Autowired
    private CollectorItemRepository collectorItemRepository;

    @Autowired
    private ComponentRepository componentRepository;

    @Autowired
    private DefaultWhiteSourceClient whiteSourceClient;

    @Autowired
    private RestClient restClient;

    @Autowired
    private WhiteSourceSettings whiteSourceSettings;

    @Autowired
    private LibraryReferenceRepository libraryReferenceRepository;

    @Autowired
    private AsyncService asyncService;

    @Autowired
    private LibraryPolicyResultsRepository libraryPolicyResultsRepository;

    @Autowired
    RestOperationsSupplier restOperationsSupplier;

    @Autowired
    private WhiteSourceCollectorTask whiteSourceCollectorTask;


    @Before
    public void populateRequestResponse() {
        PowerMockito.spy(System.class);
        PowerMockito.when(System.currentTimeMillis()).thenReturn(TestConstants.FIX_TIME_MILLIS);
        populateDatabase();
        populateOrgDetailsResponse();
        populateOrgChangeReportResponse();
        populateOrgAlertsByPolicyResponse();
        populateOrgAlertsBySecurityResponse();
        populateAllProductsResponse();
        populateAllProjectsResponse();
        populateProjectVitalsResponse();
        populateProductAlertsResponse();
        populateProjectAlertsResponse();
        populateProjectVitalsTokenRespose();

        long t = System.currentTimeMillis();
    }

    private void populateProjectVitalsResponse() {
        String response = getJsonResponse("project-vitals.json");
        TestUtils.addResponse(restOperationsSupplier, TestConstants.PROJECT_VITALS_FOR_ORG_REQUEST, response,HttpStatus.ACCEPTED);
    }

    private void populateAllProjectsResponse() {
        String response = getJsonResponse("all-projects-" + TestConstants.PRODUCT_TOKEN_Test1Product + ".json");
        TestUtils.addResponse(restOperationsSupplier, String.format(TestConstants.ALL_PROJECTS_REQUEST, TestConstants.PRODUCT_TOKEN_Test1Product), response,HttpStatus.ACCEPTED);

        response = getJsonResponse("all-projects-" + TestConstants.PRODUCT_TOKEN_Test2Product + ".json");
        TestUtils.addResponse(restOperationsSupplier, String.format(TestConstants.ALL_PROJECTS_REQUEST, TestConstants.PRODUCT_TOKEN_Test2Product), response,HttpStatus.ACCEPTED);

        response = getJsonResponse("all-projects-" + TestConstants.PRODUCT_TOKEN_Test3Product + ".json");
        TestUtils.addResponse(restOperationsSupplier, String.format(TestConstants.ALL_PROJECTS_REQUEST, TestConstants.PRODUCT_TOKEN_Test3Product), response,HttpStatus.ACCEPTED);

        response = getJsonResponse("all-projects-" + TestConstants.PRODUCT_TOKEN_Test4Product + ".json");
        TestUtils.addResponse(restOperationsSupplier, String.format(TestConstants.ALL_PROJECTS_REQUEST, TestConstants.PRODUCT_TOKEN_Test4Product), response,HttpStatus.ACCEPTED);
    }

    private void populateAllProductsResponse() {
        String response = getJsonResponse("all-products.json");
        TestUtils.addResponse(restOperationsSupplier, TestConstants.ALL_PRODUCTS_REQUEST, response,HttpStatus.ACCEPTED);
    }

    private void populateOrgChangeReportResponse() {
        String response = getJsonResponse("changelog.json");
        TestUtils.addResponse(restOperationsSupplier, TestConstants.CHANGE_REPORT_REQUEST, response,HttpStatus.ACCEPTED);
    }

    private void populateOrgDetailsResponse() {
        String response = getJsonResponse("org-details.json");
        TestUtils.addResponse(restOperationsSupplier, TestConstants.ORG_DETAILS_REQUEST, response,HttpStatus.ACCEPTED);
    }

    private void populateOrgAlertsByPolicyResponse() {
        String response = getJsonResponse("org-alerts-policy.json");
        TestUtils.addResponse(restOperationsSupplier, TestConstants.ORG_ALERTS_POLICY_REQUEST, response,HttpStatus.ACCEPTED);
    }

    private void populateOrgAlertsBySecurityResponse() {
        String response = getJsonResponse("org-alerts-security.json");
        TestUtils.addResponse(restOperationsSupplier, TestConstants.ORG_ALERTS_SECURITY_REQUEST, response,HttpStatus.ACCEPTED);
    }

    private void populateProductAlertsResponse() {
        if(CollectionUtils.isEmpty(TestConstants.PRODUCT_TOKENS)) return;
        for(String token : TestConstants.PRODUCT_TOKENS) {
            String response = getJsonResponse("product-alert-" + token + ".json");
            TestUtils.addResponse(restOperationsSupplier,TestConstants.PRODUCT_ALERTS_REQUEST_FOR_TOKEN(token), response, HttpStatus.ACCEPTED);
        }
    }

    private void populateProjectAlertsResponse() {
        if(CollectionUtils.isEmpty(TestConstants.PROJECT_TOKENS)) return;
        for(String token : TestConstants.PROJECT_TOKENS) {
            String response = getJsonResponse("project-alert-" + token + ".json");
            TestUtils.addResponse(restOperationsSupplier,TestConstants.PROJECT_ALERTS_REQUEST_FOR_TOKEN(token), response, HttpStatus.ACCEPTED);
        }
    }

    private void populateProjectVitalsTokenRespose() {
        if(CollectionUtils.isEmpty(TestConstants.PROJECT_TOKENS)) return;
        String response = getJsonResponse("project-vitals.json");
        for(String token : TestConstants.PROJECT_TOKENS) {
            TestUtils.addResponse(restOperationsSupplier,TestConstants.PROJECT_VITALS_REQUEST_FOR_TOKEN(token), response, HttpStatus.ACCEPTED);
        }
        for(String token : TestConstants.PROJECT_VITALS_TOKENS) {
            TestUtils.addResponse(restOperationsSupplier,TestConstants.PROJECT_VITALS_REQUEST_FOR_TOKEN(token), response, HttpStatus.ACCEPTED);
        }
    }

    @Test
    public void getCollector() {
        //whiteSourceCollectorTask.run();
        //will fix the below later and add asserts.
        //whiteSourceClient.refresh("GitHub Enterprise","Test3Product","95a82d2395bc4da98083bb9ab84cff349ed92e660ec745439ef37818b8ded1a7");
    }

    private void populateDatabase() {
        populateWhitesourceCollector();
        populateWhitesourceComponents();
        populateLibraryPolicyResults();
    }


    private void populateWhitesourceCollector() {
        String json = getJsonResponse("collector.json");
        Gson gson = GsonUtil.getGson();
        WhiteSourceCollector collector = gson.fromJson(json, WhiteSourceCollector.class);
        whiteSourceCollectorRepository.save(collector);
    }

    private void populateWhitesourceComponents() {
        String json = getJsonResponse("collector_items.json");
        Gson gson = GsonUtil.getGson();
        List<WhiteSourceComponent> components = gson.fromJson(json, new TypeToken<List<WhiteSourceComponent>>(){}.getType());
        whiteSourceComponentRepository.saveAll(components);
    }

    private void populateLibraryPolicyResults() {
        String json = getJsonResponse("library_policy.json");
        Gson gson = GsonUtil.getGson();
        List<LibraryPolicyResult> libraryPolicyResults = gson.fromJson(json, new TypeToken<List<LibraryPolicyResult>>(){}.getType());
        libraryPolicyResultsRepository.saveAll(libraryPolicyResults);
    }

    private String getJsonResponse(String fileName) {
        String json = null;
        try {
            json = IOUtils.toString(WhiteSourceCollectorTaskTest.class.getResourceAsStream(fileName), StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return json;
    }
}
