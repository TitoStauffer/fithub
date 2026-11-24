package com.fithub.nutrition.interfaces.rest;

import com.fithub.nutrition.application.ViewOwnFoodRestrictions;
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

/** O paciente lê as próprias restrições alimentares, só leitura (DEV-0080, FR-40). */
@RestController
@PreAuthorize("hasRole('CLIENT')")
@Tag(name = "Restrições alimentares", description = "Alergias, intolerâncias e preferências do paciente")
class MyFoodRestrictionController {

    private final ViewOwnFoodRestrictions viewOwn;

    MyFoodRestrictionController(ViewOwnFoodRestrictions viewOwn) {
        this.viewOwn = viewOwn;
    }

    @GetMapping(path = "/me/food-restrictions", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "listMyFoodRestrictions",
            summary = "As restrições alimentares do paciente autenticado",
            responses = {
                @ApiResponse(responseCode = "200", description = "Restrições (a lista pode ser vazia)."),
                @ApiResponse(
                        responseCode = "403",
                        description = "A conta não é de cliente.",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                        schema = @Schema(implementation = ClientController.ApiProblem.class)))
            })
    List<FoodRestrictionController.RestrictionResponse> mine(@AuthenticationPrincipal Jwt jwt) {
        return viewOwn.mine(jwt.getSubject()).stream()
                .map(FoodRestrictionController.RestrictionResponse::of)
                .toList();
    }
}
