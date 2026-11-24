package com.fithub.client.interfaces.rest;

import com.fithub.client.application.ManageAssessments;
import com.fithub.client.application.ManagePhotos;
import com.fithub.client.domain.assessment.InvalidAssessmentException;
import com.fithub.client.domain.assessment.Photo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Fotos de evolução das avaliações (DEV-0048). O arquivo vai direto ao bucket privado por URL pré-assinada; a API só
 * entrega e confere URLs de validade curta. As URLs e as chaves dos objetos nunca vão para log.
 */
@RestController
@Tag(name = "Fotos", description = "Fotos de evolução das avaliações")
class PhotoController {

    private static final String PROBLEM = MediaType.APPLICATION_PROBLEM_JSON_VALUE;
    private static final String BASE = "/clients/me/assessments/{assessmentId}/photos";

    private final ManagePhotos managePhotos;

    PhotoController(ManagePhotos managePhotos) {
        this.managePhotos = managePhotos;
    }

    @Schema(name = "PhotoUploadRequest")
    record UploadRequest(
            @Schema(description = "image/jpeg ou image/png.")
            String contentType) {}

    @Schema(name = "PhotoUpload")
    record UploadResponse(
            @Schema(description = "PUT do arquivo, com o mesmo Content-Type.")
            URI uploadUrl,

            @Schema(description = "Repetido na confirmação.")
            String uploadId,

            Instant expiresAt) {}

    @Schema(name = "PhotoConfirmRequest")
    record ConfirmRequest(String uploadId) {}

    @Schema(name = "Photo")
    record PhotoResponse(Photo.Pose pose, URI url, Instant expiresAt, Instant uploadedAt) {

        static PhotoResponse of(ManagePhotos.Viewable photo) {
            return new PhotoResponse(photo.pose(), photo.url(), photo.expiresAt(), photo.uploadedAt());
        }
    }

    @PostMapping(path = BASE + "/{pose}/upload-url", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(
            summary = "URL pré-assinada para enviar a foto da pose (5 minutos)",
            responses = {
                @ApiResponse(responseCode = "200", description = "URL e id do envio."),
                @ApiResponse(
                        responseCode = "400",
                        description = "Tipo de arquivo não aceito.",
                        content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ApiProblem.class))),
                @ApiResponse(
                        responseCode = "401",
                        description = "Sem token válido.",
                        content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ApiProblem.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "Avaliação não encontrada (ou de outra pessoa).",
                        content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ApiProblem.class)))
            })
    UploadResponse uploadUrl(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID assessmentId,
            @PathVariable Photo.Pose pose,
            @RequestBody UploadRequest request) {
        var upload = managePhotos.requestUpload(jwt.getSubject(), assessmentId, pose, request.contentType());
        return new UploadResponse(upload.uploadUrl(), upload.uploadId(), upload.expiresAt());
    }

    @PostMapping(path = BASE + "/{pose}/confirm", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(
            summary = "Confirma o envio da foto da pose (substitui a anterior)",
            responses = {
                @ApiResponse(responseCode = "200", description = "Foto confirmada, com a URL temporária de leitura."),
                @ApiResponse(
                        responseCode = "400",
                        description = "Arquivo ausente, de outro tipo ou acima de 10 MB.",
                        content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ApiProblem.class))),
                @ApiResponse(
                        responseCode = "401",
                        description = "Sem token válido.",
                        content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ApiProblem.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "Avaliação não encontrada (ou de outra pessoa).",
                        content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ApiProblem.class)))
            })
    PhotoResponse confirm(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID assessmentId,
            @PathVariable Photo.Pose pose,
            @RequestBody ConfirmRequest request) {
        return PhotoResponse.of(managePhotos.confirm(jwt.getSubject(), assessmentId, pose, request.uploadId()));
    }

    @GetMapping(path = BASE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(
            summary = "Fotos de uma avaliação do cliente autenticado",
            responses = {
                @ApiResponse(responseCode = "200", description = "Fotos com URLs temporárias de leitura."),
                @ApiResponse(
                        responseCode = "401",
                        description = "Sem token válido.",
                        content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ApiProblem.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "Avaliação não encontrada (ou de outra pessoa).",
                        content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ApiProblem.class)))
            })
    List<PhotoResponse> mine(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID assessmentId) {
        return managePhotos.listMine(jwt.getSubject(), assessmentId).stream()
                .map(PhotoResponse::of)
                .toList();
    }

    @DeleteMapping(BASE + "/{pose}")
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(
            summary = "Exclui a foto da pose; o arquivo sai do bucket",
            responses = {
                @ApiResponse(responseCode = "204", description = "Foto excluída (ou não havia).", content = @Content()),
                @ApiResponse(
                        responseCode = "401",
                        description = "Sem token válido.",
                        content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ApiProblem.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "Avaliação não encontrada (ou de outra pessoa).",
                        content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ApiProblem.class)))
            })
    ResponseEntity<Void> delete(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID assessmentId, @PathVariable Photo.Pose pose) {
        managePhotos.delete(jwt.getSubject(), assessmentId, pose);
        return ResponseEntity.noContent().build();
    }

    @GetMapping(
            path = "/professionals/me/clients/{clientId}/assessments/{assessmentId}/photos",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('PERSONAL', 'NUTRITIONIST')")
    @Operation(
            summary = "Fotos de uma avaliação de cliente com vínculo ativo",
            responses = {
                @ApiResponse(responseCode = "200", description = "Fotos com URLs temporárias de leitura."),
                @ApiResponse(
                        responseCode = "401",
                        description = "Sem token válido.",
                        content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ApiProblem.class))),
                @ApiResponse(
                        responseCode = "403",
                        description = "A conta não é profissional.",
                        content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ApiProblem.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "Sem vínculo ativo, ou avaliação de outro cliente.",
                        content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ApiProblem.class)))
            })
    List<PhotoResponse> forClient(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID clientId, @PathVariable UUID assessmentId) {
        return managePhotos.listForClient(jwt.getSubject(), clientId, assessmentId).stream()
                .map(PhotoResponse::of)
                .toList();
    }

    @ExceptionHandler(InvalidAssessmentException.class)
    ProblemDetail invalid(InvalidAssessmentException exception) {
        ProblemDetail problem = InvitationController.problem(
                HttpStatus.BAD_REQUEST, "Dados inválidos", "Um ou mais campos são inválidos.");
        problem.setProperty("errors", List.of(Map.of("field", exception.field(), "message", exception.getMessage())));
        return problem;
    }

    @ExceptionHandler(ManageAssessments.NotFoundException.class)
    ProblemDetail notFound(ManageAssessments.NotFoundException exception) {
        return InvitationController.problem(HttpStatus.NOT_FOUND, "Não encontrado", exception.getMessage());
    }
}
