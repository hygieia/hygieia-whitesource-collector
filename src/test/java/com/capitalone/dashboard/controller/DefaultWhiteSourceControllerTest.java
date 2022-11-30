package com.capitalone.dashboard.controller;

import com.capitalone.dashboard.collector.DefaultWhiteSourceClient;
import com.capitalone.dashboard.config.TestConfig;
import com.capitalone.dashboard.config.WebMVCConfig;
import com.capitalone.dashboard.model.WhiteSourceRequest;
import com.google.gson.Gson;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@WebAppConfiguration
@ExtendWith(SpringExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ContextConfiguration(classes = {TestConfig.class, WebMVCConfig.class})
public class DefaultWhiteSourceControllerTest {

    private MockMvc mockMvc;
    @Autowired
    private WebApplicationContext wac;


    private WhiteSourceRequest makeWhiteSourceRequest() {
        WhiteSourceRequest data = new WhiteSourceRequest();
        data.setAlerts("dGVzdA==");
        data.setOrgName("test");
        data.setProjectVitals("dGVzdA==");
        return data;
    }

    @BeforeEach
    public void before() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
    }

    @Test
    public void getProjectVitals() throws Exception {
        mockMvc.perform(post("/project-alerts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(new Gson().toJson(makeWhiteSourceRequest())))
                .andExpect(status().isCreated());
    }

}
