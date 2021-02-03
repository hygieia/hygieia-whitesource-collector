package com.capitalone.dashboard.model;

import java.util.Objects;

public class WhitesourceOrg {
    String name;
    String token;

    public WhitesourceOrg(String name, String token) {
        this.name = name;
        this.token = token;
    }

    public String getName() {
        return name;
    }

    public String getToken() {
        return token;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WhitesourceOrg)) return false;
        WhitesourceOrg that = (WhitesourceOrg) o;
        return name.equals(that.name) &&
                token.equals(that.token);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, token);
    }
}
