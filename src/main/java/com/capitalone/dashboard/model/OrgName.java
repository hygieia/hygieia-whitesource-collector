package com.capitalone.dashboard.model;

public enum OrgName {

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
