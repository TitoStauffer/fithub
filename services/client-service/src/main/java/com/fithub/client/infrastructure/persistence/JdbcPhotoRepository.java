package com.fithub.client.infrastructure.persistence;

import com.fithub.client.domain.assessment.Photo;
import com.fithub.client.domain.assessment.PhotoRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Fotos confirmadas no Postgres do client-service (migration V12). O arquivo fica no bucket. */
@Repository
class JdbcPhotoRepository implements PhotoRepository {

    private static final String COLUMNS = "assessment_id, client_id, pose, object_key, uploaded_at";

    private final JdbcClient jdbc;

    JdbcPhotoRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Photo> find(UUID assessmentId, Photo.Pose pose) {
        return jdbc.sql("select " + COLUMNS + " from assessment_photo where assessment_id = :id and pose = :pose")
                .param("id", assessmentId)
                .param("pose", pose.name())
                .query(JdbcPhotoRepository::map)
                .optional();
    }

    @Override
    public List<Photo> listByAssessment(UUID assessmentId) {
        return jdbc.sql("select " + COLUMNS + " from assessment_photo where assessment_id = :id order by pose")
                .param("id", assessmentId)
                .query(JdbcPhotoRepository::map)
                .list();
    }

    @Override
    public void save(Photo photo) {
        jdbc.sql("""
                        insert into assessment_photo (assessment_id, client_id, pose, object_key, uploaded_at)
                        values (:assessmentId, :clientId, :pose, :objectKey, :uploadedAt)
                        on conflict (assessment_id, pose) do update set
                            object_key = excluded.object_key,
                            uploaded_at = excluded.uploaded_at
                        """)
                .param("assessmentId", photo.assessmentId())
                .param("clientId", photo.clientId())
                .param("pose", photo.pose().name())
                .param("objectKey", photo.objectKey())
                .param("uploadedAt", Timestamp.from(photo.uploadedAt()))
                .update();
    }

    @Override
    public void delete(UUID assessmentId, Photo.Pose pose) {
        jdbc.sql("delete from assessment_photo where assessment_id = :id and pose = :pose")
                .param("id", assessmentId)
                .param("pose", pose.name())
                .update();
    }

    private static Photo map(ResultSet row, int index) throws SQLException {
        return new Photo(
                row.getObject("assessment_id", UUID.class),
                row.getObject("client_id", UUID.class),
                Photo.Pose.valueOf(row.getString("pose")),
                row.getString("object_key"),
                row.getTimestamp("uploaded_at").toInstant());
    }
}
