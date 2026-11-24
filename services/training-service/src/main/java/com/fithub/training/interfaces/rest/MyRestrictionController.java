package com.fithub.training.interfaces.rest;

import com.fithub.training.application.ViewOwnRestrictions;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** O aluno lê as próprias restrições de mobilidade (DEV-0058, FR-26). */
@RestController
@PreAuthorize("hasRole('CLIENT')")
@Tag(name = "Restrições de mobilidade", description = "Restrições de mobilidade do aluno")
class MyRestrictionController {

    private final ViewOwnRestrictions viewOwnRestrictions;

    MyRestrictionController(ViewOwnRestrictions viewOwnRestrictions) {
        this.viewOwnRestrictions = viewOwnRestrictions;
    }

    @GetMapping(path = "/me/restrictions", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "As restrições de mobilidade do próprio aluno",
            responses = {
                @ApiResponse(responseCode = "200", description = "Restrições, da mais antiga para a mais nova."),
                @ApiResponse(
                        responseCode = "403",
                        description = "A conta não é de cliente.",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class)))
            })
    List<RestrictionController.RestrictionResponse> mine(@AuthenticationPrincipal Jwt jwt) {
        return viewOwnRestrictions.list(jwt.getSubject()).stream()
                .map(RestrictionController.RestrictionResponse::of)
                .toList();
    }
}
