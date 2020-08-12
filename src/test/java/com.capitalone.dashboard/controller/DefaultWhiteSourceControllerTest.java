package com.capitalone.dashboard.controller;

import com.capitalone.dashboard.collector.DefaultWhiteSourceClient;
import com.capitalone.dashboard.misc.HygieiaException;
import com.capitalone.dashboard.model.Collector;
import com.capitalone.dashboard.model.WhiteSourceRequest;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;


import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class DefaultWhiteSourceControllerTest {

    @Mock
    private DefaultWhiteSourceClient defaultWhiteSourceClient;

    @InjectMocks
    private DefaultWhiteSourceController defaultWhiteSourceController;

    private WhiteSourceRequest makeWhiteSourceRequest() {
        WhiteSourceRequest data = new WhiteSourceRequest();
        data.setAlerts("ICBbCgogICAgICAgICAgewoKICAgICAgICAgICAgInR5cGUiOiAidGVzdCIsCgogICAgICAgICAgICAibGV2ZWwiOiAiTUFKT1IiLAoKICAgICAgICAgICAgImxpYnJhcnkiOiB7CgogICAgICAgICAgICAgICAgImtleVV1aWQiOiAiODczZTVhY2EtNzMwZGRjLTQ0NGYtYTlkZi1jZGJjYTQxNGZkM2YiLAoKICAgICAgICAgICAgICAgICJrZXlJZCI6IFhYWFhYWFhYWCwKCiAgICAgICAgICAgICAgICAiZmlsZW5hbWUiOiAidGVzdC5qYXIiLAoKICAgICAgICAgICAgICAgICJ0eXBlIjogIkphdmEiLAoKICAgICAgICAgICAgICAgICJkZXNjcmlwdGlvbiI6ICIiLAoKICAgICAgICAgICAgICAgICJzaGExIjogIjAzZDIyZmNlY2M3OWNkNjY4NGIwODNlZWVlY2RhNzVmYWMyZmJiZWM5MmFlIiwKCiAgICAgICAgICAgICAgICAibmFtZSI6ICJ0ZXN0IiwKCiAgICAgICAgICAgICAgICAiYXJ0aWZhY3RJZCI6ICJ0ZXN0IiwKCiAgICAgICAgICAgICAgICAidmVyc2lvbiI6ICIxLjEuMSIsCgogICAgICAgICAgICAgICAgImdyb3VwSWQiOiAiY29tLnRlc3QudXRpbGl0eSIKCiAgICAgICAgICAgIH0sCgogICAgICAgICAgICAicHJvamVjdCI6ICJ0ZXN0LXFhIiwKCiAgICAgICAgICAgICJwcm9qZWN0SWQiOiAyNzQzNDU2MDksCgogICAgICAgICAgICAicHJvamVjdFRva2VuIjogImNlYzVkNDE4MGY0MTRnbmZoZGQ4YTk3NGY3MThmZGFkZjA5YWRhZmI0ZDlhIiwKCiAgICAgICAgICAgICJkaXJlY3REZXBlbmRlbmN5IjogdHJ1ZSwKCiAgICAgICAgICAgICJkZXNjcmlwdGlvbiI6ICJUZXN0IExpY2Vuc2UiLAoKICAgICAgICAgICAgImRhdGUiOiAiMjAyMC0wNy0yOSIsCgogICAgICAgICAgICAidGltZSI6IDE1OTYwNDgyODIwMDAsCgogICAgICAgICAgICAiY3JlYXRpb25fZGF0ZSI6ICIyMDIwLTA3LTI5IiwKCiAgICAgICAgICAgICJhbGVydFV1aWQiOiAiYWFhNmM3OWItZWU2ZS00Z2hmNTAtYWRiOC1hODVmYzkxMjJlZmUiCgogICAgICAgIH0KCiAgXQoKfQ==");
        data.setOrgName("Capital One QA");
        data.setProjectVitals("ewoKICAgICAgICAicHJvZHVjdE5hbWUiOiAidGVzdCIsCgogICAgICAgICJuYW1lIjogInRlc3QiLAoKICAgICAgICAidG9rZW4iOiAiY2VjNWQ0MTgwZjQxNGRkOGE5NzRmNzE4ZmRhZGYwOWFkZGdoZGhhZmI0ZDlhN2MzYTQ2Njg4MjA2OWZiNjg1ZTllMzVmIiwKCiAgICAgICAgImNyZWF0aW9uRGF0ZSI6ICIyMDIwLTA3LTI5IDE4OjQzOjMxIiwKCiAgICAgICAgImxhc3RVcGRhdGVkRGF0ZSI6ICIyMDIwLTA3LTI5IDE4OjQ0OjQ1IgoKICAgIH0=");
        return data;
    }



    @Test
    public void createWithGoodWhiteSourceRequest() throws HygieiaException {
        WhiteSourceRequest request = makeWhiteSourceRequest();
        when(defaultWhiteSourceClient.process(any(WhiteSourceRequest.class))).thenReturn("Successfully");
        ResponseEntity<String> response = defaultWhiteSourceController.setProjectVitalsAndAlerts(request);
        assertEquals( "Successfully" , response.getBody());
    }

}
