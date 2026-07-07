package com.CampusToursLive.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Body for POST /session/active-role — the role to make active. */
@Schema(
        name = "ActiveRoleRequest",
        description = "Body for switching the caller's active (UX-context) role.")
public record ActiveRoleRequest(
        @Schema(
                        description =
                                "The role to make active. Must be a role the caller already holds"
                                        + " and that is switchable (PARTICIPANT or GUIDE); staff"
                                        + " roles never become active.",
                        example = "GUIDE",
                        allowableValues = {"PARTICIPANT", "GUIDE"},
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String role) {}
