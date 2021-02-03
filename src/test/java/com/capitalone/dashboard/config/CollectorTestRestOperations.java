package com.capitalone.dashboard.config;

import com.capitalone.dashboard.client.RestOperationsSupplier;
import com.capitalone.dashboard.testutil.TestResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestOperations;

import java.util.HashMap;
import java.util.Map;

public class CollectorTestRestOperations implements RestOperationsSupplier {


    CollectorTestRestTemplate template;
    private Map<TestRestKey, TestResponse> response;

    public CollectorTestRestOperations(Map<TestRestKey, TestResponse> response) {
        this.response = response;
    }

    public Map<TestRestKey, TestResponse> getResponse() {
        return this.response;
    }

    public void addResponse(TestRestKey key, TestResponse testResponse) {
        if (this.response == null) {
            this.response = new HashMap();
        }

        this.response.put(key, testResponse);
    }

    public void addResponse(TestRestKey key, String body, HttpStatus httpStatus) {
        if (this.response == null) {
            this.response = new HashMap();
        }

        this.response.put(key, new TestResponse(body, httpStatus));
        if (this.template != null) {
            this.template.addResponse(key, new TestResponse(body, httpStatus));
        }

    }

    public CollectorTestRestTemplate getTemplate() {
        return this.template;
    }

    public void setTemplate(CollectorTestRestTemplate template) {
        this.template = template;
    }

    public RestOperations get() {
        if (this.template == null) {
            this.template = new CollectorTestRestTemplate(this.response);
        }

        return this.template;
    }
}