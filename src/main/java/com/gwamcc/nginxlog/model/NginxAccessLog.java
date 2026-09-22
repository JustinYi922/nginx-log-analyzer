package com.gwamcc.nginxlog.model;

import java.math.BigDecimal;
import java.util.Date;

public class NginxAccessLog {

    private String remoteAddr;
    private String remoteUser;
    private Date accessTime;
    private String method;
    private String path;
    private String protocol;
    private Integer status;
    private Long bodyBytes;
    private String referer;
    private String userAgent;
    private String xForwardedFor;
    private String upstreamAddr;
    private BigDecimal upstreamResponseTime;

    public String getRemoteAddr() {
        return remoteAddr;
    }

    public void setRemoteAddr(String remoteAddr) {
        this.remoteAddr = remoteAddr;
    }

    public String getRemoteUser() {
        return remoteUser;
    }

    public void setRemoteUser(String remoteUser) {
        this.remoteUser = remoteUser;
    }

    public Date getAccessTime() {
        return accessTime;
    }

    public void setAccessTime(Date accessTime) {
        this.accessTime = accessTime;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Long getBodyBytes() {
        return bodyBytes;
    }

    public void setBodyBytes(Long bodyBytes) {
        this.bodyBytes = bodyBytes;
    }

    public String getReferer() {
        return referer;
    }

    public void setReferer(String referer) {
        this.referer = referer;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getXForwardedFor() {
        return xForwardedFor;
    }

    public void setXForwardedFor(String xForwardedFor) {
        this.xForwardedFor = xForwardedFor;
    }

    public String getUpstreamAddr() {
        return upstreamAddr;
    }

    public void setUpstreamAddr(String upstreamAddr) {
        this.upstreamAddr = upstreamAddr;
    }

    public BigDecimal getUpstreamResponseTime() {
        return upstreamResponseTime;
    }

    public void setUpstreamResponseTime(BigDecimal upstreamResponseTime) {
        this.upstreamResponseTime = upstreamResponseTime;
    }
}
