package com.capitalone.dashboard.model;


public class CollectorMetric {
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
        this.added = added + current;
    }

    public void addUpdatedCount(int current){
        this.updated = updated + current;
    }

    public void addInstanceCount(int current) {
        this.instanceCount = instanceCount + current;
    }

    public void addFetched(int current){
        this.fetched = fetched + current;
    }
}
