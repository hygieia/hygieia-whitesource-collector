package com.capitalone.dashboard.model;

import io.swagger.annotations.ApiModelProperty;

public class WhiteSourceRefreshRequest {

    @ApiModelProperty(notes = "WhiteSource Organization name",name="OrgName",required=true)
    private String orgName;

    @ApiModelProperty(notes = "WhiteSource Project name",name="ProjectName",required=true)
    private String projectName;

    @ApiModelProperty(notes = "WhiteSource Product name",name="ProductName",required=true)
    private String productName;

    public String getOrgName() {
        return orgName;
    }

    public void setOrgName(String orgName) {
        this.orgName = orgName;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }
}


