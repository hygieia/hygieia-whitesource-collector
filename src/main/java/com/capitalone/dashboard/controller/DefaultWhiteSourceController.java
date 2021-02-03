package com.capitalone.dashboard.controller;


import com.capitalone.dashboard.collector.DefaultWhiteSourceClient;
import com.capitalone.dashboard.collector.WhiteSourceSettings;
import com.capitalone.dashboard.misc.HygieiaException;
import com.capitalone.dashboard.model.CollectorItem;
import com.capitalone.dashboard.model.LibraryPolicyResult;
import com.capitalone.dashboard.model.WhiteSourceComponent;
import com.capitalone.dashboard.model.WhiteSourceRefreshRequest;
import com.capitalone.dashboard.model.WhiteSourceRequest;
import com.capitalone.dashboard.model.WhiteSourceServerSettings;
import com.capitalone.dashboard.repository.CollectorItemRepository;
import com.capitalone.dashboard.repository.LibraryPolicyResultsRepository;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import javax.validation.Valid;
import java.util.List;
import java.util.Objects;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.web.bind.annotation.RequestMethod.POST;


@RestController
public class DefaultWhiteSourceController {

    private final DefaultWhiteSourceClient defaultWhiteSourceClient;
    private final WhiteSourceSettings whiteSourceSettings;
    private final LibraryPolicyResultsRepository libraryPolicyResultsRepository;

    @Autowired
    public DefaultWhiteSourceController(DefaultWhiteSourceClient defaultWhiteSourceClient,
                                        WhiteSourceSettings whiteSourceSettings,
                                        LibraryPolicyResultsRepository libraryPolicyResultsRepository) {
        this.defaultWhiteSourceClient = defaultWhiteSourceClient;
        this.whiteSourceSettings = whiteSourceSettings;
        this.libraryPolicyResultsRepository = libraryPolicyResultsRepository;
    }


    @ApiResponses(value = {
            @ApiResponse(code = 201, message = "Created"),
            @ApiResponse(code = 401, message = "BAD REQUEST"),
            @ApiResponse(code = 403, message = "forbidden( Unauthorized)"),
            @ApiResponse(code = 500, message = "System Internal Error") })
    @RequestMapping(value = "/project-alerts", method = POST,
            consumes = "application/json", produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<String> setProjectVitalsAndAlerts(@Valid @RequestBody WhiteSourceRequest request) throws HygieiaException {
        String response = defaultWhiteSourceClient.process(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @RequestMapping(value = "/refresh", method = POST,
            consumes = "application/json", produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<String> refresh(@Valid @RequestBody WhiteSourceRefreshRequest request) throws HygieiaException {
        if (Objects.isNull(request) ||Objects.isNull(request.getOrgName()) ||Objects.isNull(request.getProductName()) ||Objects.isNull(request.getProjectName()) ){
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body("Required fields are null");
        }
        List<WhiteSourceComponent> components = defaultWhiteSourceClient.getWhiteSourceComponents(request.getOrgName(),request.getProductName(),request.getProjectName());
        for (WhiteSourceComponent component : components) {
           LibraryPolicyResult libraryPolicyResult = defaultWhiteSourceClient.getProjectAlerts(whiteSourceSettings.getServers().get(0),component,whiteSourceSettings.getWhiteSourceServerSettings().get(0));
           if (Objects.nonNull(libraryPolicyResult)){
               libraryPolicyResult.setCollectorItemId(component.getId());
               LibraryPolicyResult libraryPolicyResultExisting = defaultWhiteSourceClient.getQualityData(component,libraryPolicyResult);
               if (Objects.nonNull(libraryPolicyResultExisting)){
                   libraryPolicyResult.setId(libraryPolicyResultExisting.getId());
               }
                libraryPolicyResultsRepository.save(libraryPolicyResult);
           }
        }
        return ResponseEntity
                .status(HttpStatus.OK)
                .body("Updated Whitesource component:: OrgName="+request.getOrgName()+", productName="+request.getProductName()+", projectName="+request.getProjectName());
    }

}
