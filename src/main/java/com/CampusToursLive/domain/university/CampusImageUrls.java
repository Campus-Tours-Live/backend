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

    /** {@code base + urlEncode(name) + ".png"}; null when name is blank. Spaces→%20, &→%26. */
    public String forName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String enc = URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
        return base + enc + ".png";
    }
}
