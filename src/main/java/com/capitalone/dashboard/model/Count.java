package com.capitalone.dashboard.model;


public class Count {
    private int added = 0;
    private int updated = 0;
    private int instanceCount = 0;
    private int fetched = 0;

    public int getFetched() {
        return fetched;
    }

    public void setFetched(int fetched) {
        this.fetched = fetched;
    }

    public int getInstanceCount() {
        return instanceCount;
    }

    public void setInstanceCount(int instanceCount) {
        this.instanceCount = instanceCount;
    }

    public int getAdded() {
        return added;
    }

    public void setAdded(int added) {
        this.added = added;
    }

    public int getUpdated() {
        return updated;
    }

    public void setUpdated(int updated) {
        this.updated = updated;
    }

    public void addNewCount(int current){
        this.added = getAdded() + current;
    }

    public void addUpdatedCount(int current){
        this.updated = getUpdated() + current;
    }

    public void addInstanceCount(int current) {
        this.instanceCount = getInstanceCount() + current;
    }

    public void addFetched(int current){
        this.fetched = getFetched() + current;
    }
}
