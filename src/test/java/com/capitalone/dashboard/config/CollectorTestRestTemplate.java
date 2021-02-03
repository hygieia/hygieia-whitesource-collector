package com.capitalone.dashboard.config;

import com.capitalone.dashboard.testutil.TestResponse;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

public class CollectorTestRestTemplate extends RestTemplate {

    private Map<TestRestKey, TestResponse> response;

    public CollectorTestRestTemplate(Map<TestRestKey, TestResponse> response) {
        this.response = response;
    }

    public Map<TestRestKey, TestResponse> getResponse() {
        return this.response;
    }

    public void addResponse(TestRestKey key, TestResponse testResponse) {
        if (this.response == null) {
            this.response = new HashMap<>();
        }

        this.response.put(key, testResponse);
    }

    public void addResponse(TestRestKey key, String body, HttpStatus httpStatus) {
        if (this.response == null) {
            this.response = new HashMap<>();
        }

        this.response.put(key, new TestResponse(body, httpStatus));
    }

    public void clearResponse() {
        if (this.response != null) {
            this.response.clear();
        }

    }

    @Override
    public ResponseEntity exchange(String var1, HttpMethod var2, HttpEntity var3, Class var4, Object... var5) throws RestClientException {
        TestRestKey key = new TestRestKey();
        key.setUrl(var1);
        key.setHttpEntity(var3);
        for (TestRestKey restKey : response.keySet()) {
            TestResponse testResponse = response.get(restKey);
            if (restKey.equals(key)) {
                return new ResponseEntity(testResponse.getBody(), testResponse.getStatus());
            }
        }
        return new ResponseEntity(HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
