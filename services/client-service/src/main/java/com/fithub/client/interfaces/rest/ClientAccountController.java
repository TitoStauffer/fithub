package com.fithub.client.interfaces.rest;

import com.fithub.client.application.DeleteClientAccount;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exclusão da conta do cliente (DEV-0105, NFR-01, ADR-012). A conta e o e-mail vêm sempre do token; a confirmação
 * explícita fica na tela do app (DEV-0164).
 */
@RestController
@Tag(name = "Conta do cliente", description = "Exclusão da conta do cliente autenticado")
class ClientAccountController {

    private static final String PROBLEM = MediaType.APPLICATION_PROBLEM_JSON_VALUE;

    private final DeleteClientAccount deleteClientAccount;

    ClientAccountController(DeleteClientAccount deleteClientAccount) {
        this.deleteClientAccount = deleteClientAccount;
    }

    @DeleteMapping("/clients/me")
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(
            summary = "Exclui a conta do cliente e todos os dados dele, sem volta",
            description = "Apaga perfil, saúde, avaliações, fotos, vínculos e consentimentos e publica o"
                    + " ContaExcluida para os outros serviços apagarem o que têm. As prescrições ficam com o"
                    + " profissional, sem identificar o cliente (ADR-012). Repetir responde 204.",
            responses = {
                @ApiResponse(responseCode = "204", description = "Conta excluída.", content = @Content()),
                @ApiResponse(
                        responseCode = "401",
                        description = "Sem token válido.",
                        content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ApiProblem.class))),
                @ApiResponse(
                        responseCode = "403",
                        description = "A conta não tem o papel de cliente.",
                        content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ApiProblem.class)))
            })
    ResponseEntity<Void> deleteAccount(@AuthenticationPrincipal Jwt jwt) {
        deleteClientAccount.delete(jwt.getSubject(), jwt.getClaimAsString("email"));
        return ResponseEntity.noContent().build();
    }
}
