package com.CampusToursLive.ai.bankend.controller;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@RestController
@RequestMapping("/api/facebook")
public class FacebookLiveController {

    private final RestTemplate restTemplate = new RestTemplate();

    @PostMapping("/create-live")
    public ResponseEntity<Map<String, Object>> createFacebookLive(
            @RequestParam("accessToken") String accessToken,
            @RequestParam("pageId") String pageId
    ) {
        String url = "https://graph.facebook.com/" + pageId + "/live_videos?access_token=" + accessToken;

        // 设置直播请求参数
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("title", "Live from my Spring Boot App");
        requestBody.put("description", "This is a live stream started via API.");
        requestBody.put("status", "LIVE_NOW");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);

            Map<String, Object> result = new HashMap<>();
            Map<String, Object> body = response.getBody();

            if (body != null) {
                result.put("id", body.get("id")); // video ID
                result.put("stream_url", body.get("stream_url"));
                result.put("secure_stream_url", body.get("secure_stream_url")); // for RTMPS
            }

            return ResponseEntity.ok(result);

        } catch (HttpClientErrorException ex) {
            // 错误处理：打印返回内容
            return ResponseEntity
                    .status(ex.getStatusCode())
                    .body(Map.of("error", ex.getResponseBodyAsString()));
        }
    }
}

