package com.CampusToursLive.domain.university;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Builds the Cloudflare R2 URL for a university's campus photo from its (Scorecard) name. */
@Component
public class CampusImageUrls {
    private final String base;

    public CampusImageUrls(@Value("${campus.image.base-url:}") String base) {
        // Guarantee exactly one trailing slash so `base + key` is well-formed.
        this.base = base == null || base.isBlank() ? "" : base.replaceAll("/+$", "") + "/";
    }

    /**
     * {@code base + urlEncode(name) + ".png"}; {@code null} when name is blank OR the base is
     * unset. Returning a relative "Name.png" on a blank base would be handed to the frontend's
     * next/image, which THROWS at render on a src without a scheme/leading slash — crashing the
     * whole card grid instead of falling back. Null lets the frontend degrade to its fallback
     * image. Spaces→%20, &→%26.
     */
    public String forName(String name) {
        if (name == null || name.isBlank() || base.isEmpty()) {
            return null;
        }
        String enc = URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
        return base + enc + ".png";
    }
}
