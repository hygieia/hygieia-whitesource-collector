package com.capitalone.dashboard.model;

import io.swagger.annotations.ApiModelProperty;

public class WhiteSourceRequest {



    @ApiModelProperty(notes = "WhiteSource Organization name",name="OrgName",required=true)
    private String orgName;

    @ApiModelProperty(notes = "Base64 encoded response produced from getProjectAlerts of WhiteSource API v1.3",name="alerts",required=true)
    private String alerts;

    @ApiModelProperty(notes = "Base64 encoded response produced from getProjectVitals of WhiteSource API v1.3",name="projectVitals",required=true)
    private String projectVitals;

    private String buildUrl;

    private String clientReference;


    public String getAlerts() {
        return alerts;
    }

    public void setAlerts(String alerts) {
        this.alerts = alerts;
    }

    public String getProjectVitals() {
        return projectVitals;
    }

    public void setProjectVitals(String projectVitals) {
        this.projectVitals = projectVitals;
    }

    public String getBuildUrl() {
        return buildUrl;
    }

    public void setBuildUrl(String buildUrl) {
        this.buildUrl = buildUrl;
    }

    public String getOrgName() {
        return orgName;
    }

    public void setOrgName(String orgName) {
        this.orgName = orgName;
    }

    public String getClientReference() {
        return clientReference;
    }

    public void setClientReference(String clientReference) {
        this.clientReference = clientReference;
    }
}


