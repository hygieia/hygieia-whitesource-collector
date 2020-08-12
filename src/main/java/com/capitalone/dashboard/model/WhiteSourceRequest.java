package com.capitalone.dashboard.model;

import io.swagger.annotations.ApiModelProperty;

public class WhiteSourceRequest {

    @ApiModelProperty(notes = "WhiteSource Organization name",name="OrgName",required=true)
    private OrgName orgName;

    @ApiModelProperty(notes = "Base64 encoded response produced from getProjectAlerts of WhiteSource API v1.3",name="alerts",required=true)
    private String alerts;

    @ApiModelProperty(notes = "Base64 encoded response produced from getProjectVitals of WhiteSource API v1.3",name="projectVitals",required=true)
    private String projectVitals;

    public String getOrgName() {
        return orgName.toString();
    }

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
}

 enum OrgName {
     Capital_One_QA ("Capital One QA"),
     Capital_One_Prod ("Capital One Prod")
     ;

    private String name;

    /**
     * @param name
     */
    OrgName( String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }

}
