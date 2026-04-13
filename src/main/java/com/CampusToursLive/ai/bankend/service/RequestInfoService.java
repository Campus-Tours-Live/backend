package com.CampusToursLive.ai.bankend.service;

import com.CampusToursLive.ai.bankend.dto.RequestInfo;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class RequestInfoService {

    public RequestInfo extract(HttpServletRequest request) {
        RequestInfo info = new RequestInfo();

        String userAgent = header(request, "User-Agent");
        String secChUa = header(request, "Sec-CH-UA");
        String secChUaPlatform = header(request, "Sec-CH-UA-Platform");
        String secChUaMobile = header(request, "Sec-CH-UA-Mobile");

        info.setClientIp(resolveClientIp(request));
        info.setUserAgent(userAgent);
        info.setSecChUa(secChUa);
        info.setSecChUaPlatform(secChUaPlatform);
        info.setSecChUaMobile(secChUaMobile);
        info.setReferer(header(request, "Referer"));
        info.setOrigin(header(request, "Origin"));
        info.setAccept(header(request, "Accept"));
        info.setAcceptLanguage(header(request, "Accept-Language"));
        info.setHost(header(request, "Host"));
        info.setMethod(request.getMethod());
        info.setRequestUri(request.getRequestURI());
        info.setLikelyBrowser(isLikelyBrowser(request, userAgent));
        info.setOsGuess(detectOs(userAgent, secChUaPlatform));
        info.setBrowserGuess(detectBrowser(userAgent, secChUa));
        info.setHeaders(getAllHeaders(request));

        return info;
    }

    public String resolveClientIp(HttpServletRequest request) {
        String[] headerNames = {
                "X-Forwarded-For",
                "Forwarded",
                "X-Real-IP",
                "CF-Connecting-IP",
                "True-Client-IP"
        };

        for (String headerName : headerNames) {
            String value = request.getHeader(headerName);
            if (value != null && !value.isBlank()) {
                if ("X-Forwarded-For".equalsIgnoreCase(headerName)) {
                    return value.split(",")[0].trim();
                }
                if ("Forwarded".equalsIgnoreCase(headerName)) {
                    // Example: for=203.0.113.60;proto=https;by=203.0.113.43
                    for (String part : value.split(";")) {
                        String trimmed = part.trim();
                        if (trimmed.toLowerCase().startsWith("for=")) {
                            return trimmed.substring(4).replace("\"", "");
                        }
                    }
                }
                return value.trim();
            }
        }

        return request.getRemoteAddr();
    }

    private boolean isLikelyBrowser(HttpServletRequest request, String userAgent) {
        String accept = header(request, "Accept");
        boolean hasBrowserAccept = accept != null && accept.contains("text/html");
        boolean hasUa = userAgent != null && !userAgent.isBlank();
        boolean secFetchSite = request.getHeader("Sec-Fetch-Site") != null;
        return hasUa && (hasBrowserAccept || secFetchSite);
    }

    private String detectOs(String userAgent, String secChUaPlatform) {
        if (secChUaPlatform != null && !secChUaPlatform.isBlank()) {
            return secChUaPlatform.replace("\"", "");
        }

        if (userAgent == null) return "Unknown";
        String ua = userAgent.toLowerCase();

        if (ua.contains("mac os x") || ua.contains("macintosh")) return "macOS";
        if (ua.contains("windows")) return "Windows";
        if (ua.contains("android")) return "Android";
        if (ua.contains("iphone") || ua.contains("ipad") || ua.contains("ios")) return "iOS";
        if (ua.contains("linux")) return "Linux";

        return "Unknown";
    }

    private String detectBrowser(String userAgent, String secChUa) {
        if (secChUa != null && !secChUa.isBlank()) {
            String lower = secChUa.toLowerCase();
            if (lower.contains("google chrome")) return "Chrome";
            if (lower.contains("microsoft edge")) return "Edge";
            if (lower.contains("chromium")) return "Chromium-based";
        }

        if (userAgent == null) return "Unknown";
        String ua = userAgent.toLowerCase();

        if (ua.contains("edg/")) return "Edge";
        if (ua.contains("chrome/")) return "Chrome";
        if (ua.contains("safari/") && !ua.contains("chrome/")) return "Safari";
        if (ua.contains("firefox/")) return "Firefox";

        return "Unknown";
    }

    private Map<String, String> getAllHeaders(HttpServletRequest request) {
        Map<String, String> headers = new LinkedHashMap<>();
        Enumeration<String> names = request.getHeaderNames();

        if (names == null) {
            return Collections.emptyMap();
        }

        while (names.hasMoreElements()) {
            String name = names.nextElement();
            headers.put(name, request.getHeader(name));
        }

        return headers;
    }

    private String header(HttpServletRequest request, String name) {
        return request.getHeader(name);
    }
}