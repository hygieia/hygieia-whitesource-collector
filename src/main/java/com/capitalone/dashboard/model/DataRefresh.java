package com.capitalone.dashboard.model;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class DataRefresh {
    private Set<WhiteSourceComponent> collectedProjects = new HashSet<>();
    private Map<String, LibraryPolicyReference> libraryLookUp = new HashMap<>();

    public DataRefresh(Set<WhiteSourceComponent> collectedProjects, Map<String, LibraryPolicyReference> libraryLookUp) {
        this.collectedProjects = collectedProjects;
        this.libraryLookUp = libraryLookUp;
    }

    public DataRefresh() {
    }

    public Set<WhiteSourceComponent> getCollectedProjects() {
        return collectedProjects;
    }

    public void addCollectedProjects(Set<WhiteSourceComponent> collectedProjects) {
        this.collectedProjects.addAll(collectedProjects);
    }

    public Map<String, LibraryPolicyReference> getLibraryLookUp() {
        return libraryLookUp;
    }

    public void addLibraryLookUp(Map<String, LibraryPolicyReference> libraryLookUp) {
        libraryLookUp.forEach((key, value) -> {
            if (!this.libraryLookUp.containsKey(key)) {
                this.libraryLookUp.put(key, value);
            } else {
                this.libraryLookUp.get(key).getProjectReferences().addAll(value.getProjectReferences());
            }
        });
    }

    public void combine(DataRefresh dataRefresh) {
        if (dataRefresh != null) {
            this.addCollectedProjects(dataRefresh.collectedProjects);
            this.addLibraryLookUp(dataRefresh.libraryLookUp);
        }
    }
}
