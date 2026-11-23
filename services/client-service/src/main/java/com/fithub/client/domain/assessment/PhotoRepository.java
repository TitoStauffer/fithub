package com.fithub.client.domain.assessment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Registro das fotos confirmadas (porta do domínio, DEV-0048). */
public interface PhotoRepository {

    Optional<Photo> find(UUID assessmentId, Photo.Pose pose);

    List<Photo> listByAssessment(UUID assessmentId);

    /** Grava a foto da pose, substituindo a anterior. */
    void save(Photo photo);

    void delete(UUID assessmentId, Photo.Pose pose);
}
