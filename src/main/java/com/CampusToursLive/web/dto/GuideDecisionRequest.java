package com.CampusToursLive.web.dto;

/** Body for POST /admin/guides/{userId}/decision — APPROVED or REJECTED. */
public record GuideDecisionRequest(String decision) {}
