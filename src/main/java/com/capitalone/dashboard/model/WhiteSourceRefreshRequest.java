package com.capitalone.dashboard.model;

import io.swagger.annotations.ApiModelProperty;

public class WhiteSourceRefreshRequest {

    @ApiModelProperty(notes = "WhiteSource Organization name",name="OrgName",required=false)
    private String orgName;

    @ApiModelProperty(notes = "WhiteSource Product name",name="ProductName",required=false)
    private String productName;

    @ApiModelProperty(notes = "WhiteSource Project name",name="ProjectName")
    private String projectName;

    @ApiModelProperty(notes = "WhiteSource alternate identifier",name="AltIdentifier",required=false)
    private String altIdentifier;

    @ApiModelProperty(notes = "WhiteSource Project token",name="ProjectToken",required=false)
    private String projectToken;

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }


    public String getOrgName() {return orgName;}

    public void setOrgName(String orgName) {this.orgName = orgName;}

    public String getProductName() {return productName;}

    public void setProductName(String productName) {this.productName = productName;}

    public String getAltIdentifier() {return altIdentifier;}

    public void setAltIdentifier(String altIdentifier) {this.altIdentifier = altIdentifier;}

    public String getProjectToken() { return projectToken; }

    public void setProjectToken(String projectToken) { this.projectToken = projectToken; }
}


