package com.CampusToursLive.ai.bankend.dto;

import java.util.Map;

public class RequestInfo {
    private String clientIp;
    private String userAgent;
    private String secChUa;
    private String secChUaPlatform;
    private String secChUaMobile;
    private String referer;
    private String origin;
    private String accept;
    private String acceptLanguage;
    private String host;
    private String method;
    private String requestUri;
    private boolean likelyBrowser;
    private String osGuess;
    private String browserGuess;
    private Map<String, String> headers;

    public String getClientIp() { return clientIp; }
    public void setClientIp(String clientIp) { this.clientIp = clientIp; }

    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }

    public String getSecChUa() { return secChUa; }
    public void setSecChUa(String secChUa) { this.secChUa = secChUa; }

    public String getSecChUaPlatform() { return secChUaPlatform; }
    public void setSecChUaPlatform(String secChUaPlatform) { this.secChUaPlatform = secChUaPlatform; }

    public String getSecChUaMobile() { return secChUaMobile; }
    public void setSecChUaMobile(String secChUaMobile) { this.secChUaMobile = secChUaMobile; }

    public String getReferer() { return referer; }
    public void setReferer(String referer) { this.referer = referer; }

    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }

    public String getAccept() { return accept; }
    public void setAccept(String accept) { this.accept = accept; }

    public String getAcceptLanguage() { return acceptLanguage; }
    public void setAcceptLanguage(String acceptLanguage) { this.acceptLanguage = acceptLanguage; }

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }

    public String getRequestUri() { return requestUri; }
    public void setRequestUri(String requestUri) { this.requestUri = requestUri; }

    public boolean isLikelyBrowser() { return likelyBrowser; }
    public void setLikelyBrowser(boolean likelyBrowser) { this.likelyBrowser = likelyBrowser; }

    public String getOsGuess() { return osGuess; }
    public void setOsGuess(String osGuess) { this.osGuess = osGuess; }

    public String getBrowserGuess() { return browserGuess; }
    public void setBrowserGuess(String browserGuess) { this.browserGuess = browserGuess; }

    public Map<String, String> getHeaders() { return headers; }
    public void setHeaders(Map<String, String> headers) { this.headers = headers; }
}