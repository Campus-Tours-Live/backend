package com.CampusToursLive.web.dto;

/** Standard success envelope: { data, meta }. */
public record ApiEnvelope<T>(T data, Meta meta) {
    public static <T> ApiEnvelope<T> of(T data) {
        return new ApiEnvelope<>(data, Meta.now());
    }
}
