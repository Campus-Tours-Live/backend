package com.CampusToursLive.web;

import com.CampusToursLive.domain.booking.BookingService;
import com.CampusToursLive.domain.user.UserRole;
import com.CampusToursLive.security.CurrentUser;
import com.CampusToursLive.web.doc.ApiExamples;
import com.CampusToursLive.web.dto.ApiEnvelope;
import com.CampusToursLive.web.dto.BookingDetailResponse;
import com.CampusToursLive.web.dto.CartItemResponse;
import com.CampusToursLive.web.dto.CreateBookingRequest;
import com.CampusToursLive.web.dto.Problem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Participant booking cart (Core: /cart; BFF path /v1/cart/*). Cart items are DRAFT bookings:
 * validated and priced, but holding no slot until checkout submits them all atomically. All
 * endpoints require the PARTICIPANT role.
 */
@RestController
@RequestMapping("/cart")
@Tag(
        name = "Participant cart",
        description =
                "A participant's booking cart: DRAFT bookings that hold no slot until checkout"
                        + " submits them all atomically. Every operation requires a valid platform"
                        + " JWT and the PARTICIPANT role (authorization reads user_roles).")
public class CartController {

    private final CurrentUser currentUser;
    private final BookingService bookingService;

    public CartController(CurrentUser currentUser, BookingService bookingService) {
        this.currentUser = currentUser;
        this.bookingService = bookingService;
    }

    /** The current cart, oldest item first. */
    @Operation(
            summary = "List cart items",
            description =
                    "Returns the participant's cart — their DRAFT bookings, oldest first. DRAFT"
                            + " items are fully validated and priced but hold no slot until"
                            + " checkout.")
    @ApiResponse(
            responseCode = "200",
            description = "The cart items (empty array when the cart is empty).",
            content =
                    @Content(
                            mediaType = "application/json",
                            examples = {
                                @ExampleObject(name = "items", value = ApiExamples.CART_LIST),
                                @ExampleObject(name = "empty", value = ApiExamples.CART_EMPTY)
                            }))
    @ApiResponse(
            responseCode = "401",
            description = "No valid principal / account not provisioned.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_401)))
    @ApiResponse(
            responseCode = "403",
            description = "Caller does not hold the PARTICIPANT role.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_403)))
    @GetMapping
    public ApiEnvelope<List<CartItemResponse>> getCart() {
        var user = currentUser.requireRole(UserRole.PARTICIPANT);
        return ApiEnvelope.of(bookingService.getCart(user.getId()));
    }

    /** Validate and add one item (same body as a direct booking create). */
    @Operation(
            summary = "Add a cart item",
            description =
                    "Validates a booking request exactly like a direct create (bookable offering,"
                            + " notice/advance windows, conflicts with held bookings and other cart"
                            + " items) and saves it as a DRAFT booking that holds no slot. Carts"
                            + " are capped at 10 items; the price is snapshotted at add time.")
    @ApiResponse(
            responseCode = "200",
            description = "The added cart item (a DRAFT booking).",
            content =
                    @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = ApiExamples.CART_ITEM_DRAFT)))
    @ApiResponse(
            responseCode = "401",
            description = "No valid principal / account not provisioned.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_401)))
    @ApiResponse(
            responseCode = "403",
            description = "Caller does not hold the PARTICIPANT role.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_403)))
    @ApiResponse(
            responseCode = "404",
            description = "Tour not found (unknown offering, or not currently ACTIVE).",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_404)))
    @ApiResponse(
            responseCode = "422",
            description =
                    "Validation failed — bad fields, window violated, cart full, or the time"
                            + " conflicts with a held booking or another cart item.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_422)))
    @PostMapping("/items")
    public ApiEnvelope<CartItemResponse> addItem(@RequestBody CreateBookingRequest req) {
        var user = currentUser.requireRole(UserRole.PARTICIPANT);
        return ApiEnvelope.of(bookingService.addCartItem(user, req));
    }

    /** Remove one item; returns the remaining cart. */
    @Operation(
            summary = "Remove a cart item",
            description =
                    "Deletes one DRAFT booking from the participant's cart and returns the"
                            + " remaining items. A DRAFT was never a commitment, so removal is a"
                            + " hard delete.")
    @ApiResponse(
            responseCode = "200",
            description = "The remaining cart items.",
            content =
                    @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = ApiExamples.CART_EMPTY)))
    @ApiResponse(
            responseCode = "401",
            description = "No valid principal / account not provisioned.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_401)))
    @ApiResponse(
            responseCode = "403",
            description = "Caller does not hold the PARTICIPANT role.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_403)))
    @ApiResponse(
            responseCode = "404",
            description = "Cart item not found (unknown, not DRAFT, or not owned by the caller).",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_404)))
    @DeleteMapping("/items/{id}")
    public ApiEnvelope<List<CartItemResponse>> removeItem(@PathVariable UUID id) {
        var user = currentUser.requireRole(UserRole.PARTICIPANT);
        return ApiEnvelope.of(bookingService.removeCartItem(user, id));
    }

    /** Submit every cart item atomically — all become PENDING_GUIDE_ACCEPTANCE, or none do. */
    @Operation(
            summary = "Checkout the cart",
            description =
                    "Re-validates every cart item (offerings may have paused, items may have"
                            + " drifted inside the 24h notice window) and flips all DRAFTs to"
                            + " PENDING_GUIDE_ACCEPTANCE in one transaction — all or nothing. If"
                            + " any slot was taken concurrently, the whole checkout rolls back and"
                            + " the cart is left intact.")
    @ApiResponse(
            responseCode = "200",
            description = "All submitted bookings, now waiting for their guides.",
            content =
                    @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = ApiExamples.CART_CHECKOUT_RESULT)))
    @ApiResponse(
            responseCode = "401",
            description = "No valid principal / account not provisioned.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_401)))
    @ApiResponse(
            responseCode = "403",
            description = "Caller does not hold the PARTICIPANT role.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_403)))
    @ApiResponse(
            responseCode = "422",
            description =
                    "Checkout rejected — empty cart, a stale/no-longer-bookable item, items that"
                            + " overlap each other, or a slot taken concurrently. Nothing was"
                            + " submitted.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_422)))
    @PostMapping("/checkout")
    public ApiEnvelope<List<BookingDetailResponse>> checkout() {
        var user = currentUser.requireRole(UserRole.PARTICIPANT);
        return ApiEnvelope.of(bookingService.checkout(user));
    }
}
