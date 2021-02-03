package com.capitalone.dashboard.controller;

import com.capitalone.dashboard.collector.DefaultWhiteSourceClient;
import com.capitalone.dashboard.controller.DefaultWhiteSourceController;
import com.capitalone.dashboard.misc.HygieiaException;
import com.capitalone.dashboard.model.WhiteSourceRequest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
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
        data.setAlerts("dGVzdA==");
        data.setOrgName("test");
        data.setProjectVitals("dGVzdA==");
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
