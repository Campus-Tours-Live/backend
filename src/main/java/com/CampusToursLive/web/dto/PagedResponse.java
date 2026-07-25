package com.CampusToursLive.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.data.domain.Page;

/**
 * A page of results — the {@code data} payload for any paginated list endpoint. Page-based (not
 * cursor-based) so clients can render numbered pages and jump directly to a page: {@code items} is
 * the current page's rows, and {@code totalPages} / {@code totalElements} let the client size the
 * pager and disable next/previous at the ends.
 */
@Schema(name = "PagedResponse", description = "A page of results with total counts for a pager.")
public record PagedResponse<T>(
        @Schema(
                        description = "The current page's rows.",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                List<T> items,
        @Schema(description = "Zero-based index of this page.", example = "0") int page,
        @Schema(description = "Page size (max rows per page).", example = "20") int size,
        @Schema(description = "Total matching rows across all pages.", example = "128")
                long totalElements,
        @Schema(description = "Total number of pages (0 when there are no results).", example = "7")
                int totalPages) {

    /** Wrap a Spring Data {@link Page} as the API's page shape. */
    public static <T> PagedResponse<T> of(Page<T> page) {
        return new PagedResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
