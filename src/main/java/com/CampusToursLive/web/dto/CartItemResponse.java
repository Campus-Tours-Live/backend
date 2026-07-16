package com.CampusToursLive.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A DRAFT cart item, flattened for display (CTL-83). A superset of {@link BookingDetailResponse}'s
 * fields — so existing consumers keep reading the same keys — plus cart-specific display fields:
 * {@code cartStatus} (a fresh, non-atomic hint) and {@code currentPriceCents} (the offering's live
 * price, versus {@code priceCents} which is the snapshot taken at add-to-cart time).
 *
 * <p>The cart never reserves the guide's slot, so {@code cartStatus == AVAILABLE} does not
 * guarantee the time is still bookable — checkout, when it ships, re-validates atomically.
 */
@Schema(
        name = "CartItemResponse",
        description =
                "A DRAFT cart item flattened for display, with a non-atomic availability hint.")
public record CartItemResponse(
        @Schema(
                        description = "Cart item id (the underlying DRAFT booking's UUID).",
                        example = "b1a2c3d4-0000-4000-8000-000000000001",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String id,
        @Schema(
                        description = "Frontend-facing status; always \"DRAFT\" for a cart item.",
                        example = "DRAFT",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String status,
        @Schema(
                        description = "ISO-8601 UTC start time the participant chose.",
                        example = "2026-08-01T15:00:00Z",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String scheduledAt,
        @Schema(
                        description = "Id of the tour offering this cart item is for.",
                        example = "o1a2c3d4-0000-4000-8000-000000000002",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String offeringId,
        @Schema(
                        description = "Display title of the offering.",
                        example = "North Campus highlights",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String offeringTitle,
        @Schema(
                        description = "Display name of the guide.",
                        example = "Maya Chen",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String guideName,
        @Schema(
                        description = "Name of the university the tour is at.",
                        example = "North Coast University",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String universityName,
        @Schema(
                        description = "Tour duration in minutes.",
                        example = "60",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                int durationMin,
        @Schema(
                        description =
                                "Price in cents captured when the item was added (the snapshot).",
                        example = "4200",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                long priceCents,
        @Schema(
                        description =
                                "The offering's current price in cents; differs from priceCents"
                                        + " when the guide changed it after add-to-cart.",
                        example = "4200",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                long currentPriceCents,
        @Schema(
                        description = "ISO-4217 currency code.",
                        example = "USD",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String currency,
        @Schema(
                        description =
                                "Display-only availability hint, recomputed on every read — NOT an"
                                        + " atomic reservation.",
                        example = "AVAILABLE",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String cartStatus) {}
