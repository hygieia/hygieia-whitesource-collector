package com.capitalone.dashboard.collector;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Bean to hold settings specific to the WhiteSource collector.
 */
@Component
@ConfigurationProperties(prefix = "whitesource")
public class WhiteSourceSettings {


    private String cron;
    private List<String> servers;
    private String username;
    private boolean selectStricterLicense;
    @Value("${whitesource.sleepTime:250}") // 250 ms as default
    private long sleepTime;
    private long waitTime;
    private int requestRateLimit;
    private long requestRateLimitTimeWindow;
    @Value("${whitesource.errorThreshold:2}")
    private int errorThreshold;
    @Value("${whitesource.errorResetWindow:3600000}")
    private int errorResetWindow;
    private List<String> genericItemCaptureRegEx;
    @Value("${whitesource.connectTimeout:15000}")
    private int connectTimeout;
    @Value("${whitesource.readTimeout:120000}")
    private int readTimeout;
    private List<String> orgTokens;
    private String userKey;
    private List<String> highLicensePolicyTypes = new ArrayList<>();
    private List<String> mediumLicensePolicyTypes = new ArrayList<>();
    private List<String> lowLicensePolicyTypes = new ArrayList<>();

    public String getUserKey() {
        return userKey;
    }

    public void setUserKey(String userKey) {
        this.userKey = userKey;
    }

    public List<String> getHighLicensePolicyTypes() {
        return highLicensePolicyTypes;
    }

    public void setHighLicensePolicyTypes(List<String> highLicensePolicyTypes) {
        this.highLicensePolicyTypes = highLicensePolicyTypes;
    }

    public List<String> getMediumLicensePolicyTypes() {
        return mediumLicensePolicyTypes;
    }

    public void setMediumLicensePolicyTypes(List<String> mediumLicensePolicyTypes) {
        this.mediumLicensePolicyTypes = mediumLicensePolicyTypes;
    }

    public List<String> getLowLicensePolicyTypes() {
        return lowLicensePolicyTypes;
    }

    public void setLowLicensePolicyTypes(List<String> lowLicensePolicyTypes) {
        this.lowLicensePolicyTypes = lowLicensePolicyTypes;
    }

    public List<String> getOrgTokens() {
        return orgTokens;
    }

    public void setOrgTokens(List<String> orgTokens) {
        this.orgTokens = orgTokens;
    }

    private List<String> dispositionMapping;


    private Map<String, Long> licenseThreatScore;


    public String getCron() {
        return cron;
    }

    public void setCron(String cron) {
        this.cron = cron;
    }

    public List<String> getServers() {
        return servers;
    }

    public void setServers(List<String> servers) {
        this.servers = servers;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Map<String, Long> getLicenseThreatScore() {
        return licenseThreatScore;
    }

    public void setLicenseThreatScore(Map<String, Long> licenseThreatScore) {
        this.licenseThreatScore = licenseThreatScore;
    }

    public boolean isSelectStricterLicense() {
        return selectStricterLicense;
    }

    public void setSelectStricterLicense(boolean selectStricterLicense) {
        this.selectStricterLicense = selectStricterLicense;
    }

    public long getSleepTime() {
        return sleepTime;
    }

    public void setSleepTime(long sleepTime) {
        this.sleepTime = sleepTime;
    }

    public long getWaitTime() {
        return waitTime;
    }

    public void setWaitTime(long waitTime) {
        this.waitTime = waitTime;
    }

    public int getRequestRateLimit() {
        return requestRateLimit;
    }

    public void setRequestRateLimit(int requestRateLimit) {
        this.requestRateLimit = requestRateLimit;
    }

    public long getRequestRateLimitTimeWindow() {
        return requestRateLimitTimeWindow;
    }

    public void setRequestRateLimitTimeWindow(long requestRateLimitTimeWindow) {
        this.requestRateLimitTimeWindow = requestRateLimitTimeWindow;
    }

    public int getErrorResetWindow() {
        return errorResetWindow;
    }

    public void setErrorResetWindow(int errorResetWindow) {
        this.errorResetWindow = errorResetWindow;
    }

    public int getErrorThreshold() {
        return errorThreshold;
    }

    public void setErrorThreshold(int errorThreshold) {
        this.errorThreshold = errorThreshold;
    }

    public List<String> getGenericItemCaptureRegEx() {
        return genericItemCaptureRegEx;
    }

    public void setGenericItemCaptureRegEx(List<String> genericItemCaptureRegEx) {
        this.genericItemCaptureRegEx = genericItemCaptureRegEx;
    }

    public List<String> getDispositionMapping() {
        return dispositionMapping;
    }

    public void setDispositionMapping(List<String> dispositionMapping) {
        this.dispositionMapping = dispositionMapping;
    }

    public int getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(int readTimeout) {
        this.readTimeout = readTimeout;
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }
}
