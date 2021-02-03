package com.capitalone.dashboard.model;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class DataRefresh {
    private Set<WhiteSourceComponent> collectedProjects = new HashSet<>();
    private Map<String, LibraryPolicyReference> libraryReferenceMap = new HashMap<>();

    public DataRefresh(Set<WhiteSourceComponent> collectedProjects, Map<String, LibraryPolicyReference> libraryReferenceMap) {
        this.collectedProjects = collectedProjects;
        this.libraryReferenceMap = libraryReferenceMap;
    }

    public DataRefresh() {
    }

    public Set<WhiteSourceComponent> getCollectedProjects() {
        return collectedProjects;
    }

    public void addProject(WhiteSourceComponent project) {
        this.collectedProjects.add(project);
    }

    public void addCollectedProjects(Set<WhiteSourceComponent> collectedProjects) {
        this.collectedProjects.addAll(collectedProjects);
    }

    public Map<String, LibraryPolicyReference> getLibraryReferenceMap() {
        return libraryReferenceMap;
    }

    public void addLibraryReference(Map<String, LibraryPolicyReference> libraryReference) {
        libraryReference.forEach((key, value) -> {
            if (!this.libraryReferenceMap.containsKey(key)) {
                this.libraryReferenceMap.put(key, value);
            } else {
                this.libraryReferenceMap.get(key).getProjectReferences().addAll(value.getProjectReferences());
            }
        });
    }

    public void combine(DataRefresh dataRefresh) {
        if (dataRefresh != null) {
            this.addCollectedProjects(dataRefresh.collectedProjects);
            this.addLibraryReference(dataRefresh.libraryReferenceMap);
        }
    }
}
