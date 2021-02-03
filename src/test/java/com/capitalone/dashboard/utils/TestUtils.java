package com.capitalone.dashboard.utils;

import com.capitalone.dashboard.client.RestOperationsSupplier;
import com.capitalone.dashboard.config.CollectorTestRestOperations;
import com.capitalone.dashboard.config.TestConstants;
import com.capitalone.dashboard.config.TestRestKey;
import com.capitalone.dashboard.mapper.CustomObjectMapper;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.Charset;

import static org.springframework.util.MimeTypeUtils.APPLICATION_JSON;

public class TestUtils {
    public static final MediaType APPLICATION_JSON_UTF8 = new MediaType(APPLICATION_JSON.getType(), APPLICATION_JSON.getSubtype(), Charset.forName("utf8"));

    public static byte[] convertObjectToJsonBytes(Object object) throws IOException {
        ObjectMapper mapper = new CustomObjectMapper();
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        return mapper.writeValueAsBytes(object);
    }


    private static HttpEntity getHttpEntity (String request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity(getJSONObject(request), headers);
    }

    public static void addResponse(RestOperationsSupplier restOperationsSupplier, String request, String response, HttpStatus httpStatus) {
        CollectorTestRestOperations testRestOperations = ((CollectorTestRestOperations) restOperationsSupplier);
        TestRestKey key = new TestRestKey();
        key.setUrl(TestConstants.API_URL);

        key.setHttpEntity(getHttpEntity(request));
        testRestOperations.getTemplate().addResponse(key, response, httpStatus);
    }

    private static JSONObject getJSONObject(String json) {
        JSONParser parser = new JSONParser();
        try {
            return (JSONObject) parser.parse(json);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return new JSONObject();
    }


}
