package com.capitalone.dashboard.config;

import org.springframework.http.HttpEntity;

import java.util.Objects;

public class TestRestKey {
    private String url;
    private HttpEntity httpEntity;

    public TestRestKey() {
    }

    public TestRestKey(String url, HttpEntity httpEntity) {
        this.url = url;
        this.httpEntity = httpEntity;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public HttpEntity getHttpEntity() {
        return httpEntity;
    }

    public void setHttpEntity(HttpEntity httpEntity) {
        this.httpEntity = httpEntity;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TestRestKey)) return false;
        TestRestKey that = (TestRestKey) o;
        return url.equals(that.url) &&
                httpEntity.equals(that.httpEntity);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url, httpEntity);
    }
}
