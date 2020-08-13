package com.capitalone.dashboard.model;

import io.swagger.annotations.ApiModelProperty;

public class WhiteSourceRequest {



    @ApiModelProperty(notes = "WhiteSource Organization name",name="OrgName",required=true)
    private OrgName orgName;

    @ApiModelProperty(notes = "Base64 encoded response produced from getProjectAlerts of WhiteSource API v1.3",name="alerts",required=true)
    private String alerts;

    @ApiModelProperty(notes = "Base64 encoded response produced from getProjectVitals of WhiteSource API v1.3",name="projectVitals",required=true)
    private String projectVitals;

    private String buildUrl;

//    public String getOrgName() {
//        return orgName.toString();
//    }
//
    public void setOrgName(String orgName) {
        switch (orgName) {
            case "Capital One QA":
                this.orgName = OrgName.Capital_One_QA;
                break;
            case "Capital One Prod":
                this.orgName = OrgName.Capital_One_Prod;
                break;
        }
    }


    public OrgName getOrgName() {
        return orgName;
    }

//    public void setOrgName(OrgName orgName) {
//        this.orgName = orgName;
//    }

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
}


