package com.fithub.client.infrastructure.persistence;

import com.fithub.client.domain.assessment.Assessment;
import com.fithub.client.domain.assessment.AssessmentRepository;
import com.fithub.client.domain.assessment.BodyFat;
import com.fithub.client.domain.assessment.Circumferences;
import com.fithub.client.domain.assessment.Skinfolds;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Avaliações corporais no Postgres do client-service (migration V8). Nada daqui vai para log. */
@Repository
class JdbcAssessmentRepository implements AssessmentRepository {

    private static final String COLUMNS = "id, client_id, assessment_type, measured_on, weight_kg, neck_cm, chest_cm,"
            + " waist_cm, abdomen_cm, hip_cm, arm_cm, forearm_cm, thigh_cm, calf_cm, sex_for_formulas, author_id,"
            + " author_role, created_at, skinfold_chest_mm, skinfold_midaxillary_mm, skinfold_triceps_mm,"
            + " skinfold_subscapular_mm, skinfold_abdomen_mm, skinfold_suprailiac_mm, skinfold_thigh_mm,"
            + " pollock3_percent, pollock7_percent";

    private final JdbcClient jdbc;

    JdbcAssessmentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(Assessment assessment) {
        Circumferences c = assessment.circumferences();
        Skinfolds s = assessment.skinfolds();
        jdbc.sql("insert into body_assessment (" + COLUMNS + ") values (:id, :clientId, :type, :measuredOn, :weight,"
                        + " :neck, :chest, :waist, :abdomen, :hip, :arm, :forearm, :thigh, :calf, :sex, :authorId,"
                        + " :authorRole, :createdAt, :sfChest, :sfMidaxillary, :sfTriceps, :sfSubscapular, :sfAbdomen,"
                        + " :sfSuprailiac, :sfThigh, :pollock3, :pollock7)")
                .param("id", assessment.id())
                .param("clientId", assessment.clientId())
                .param("type", assessment.type().name())
                .param("measuredOn", Date.valueOf(assessment.measuredOn()))
                .param("weight", assessment.weightKg())
                .param("neck", c.neck())
                .param("chest", c.chest())
                .param("waist", c.waist())
                .param("abdomen", c.abdomen())
                .param("hip", c.hip())
                .param("arm", c.arm())
                .param("forearm", c.forearm())
                .param("thigh", c.thigh())
                .param("calf", c.calf())
                .param(
                        "sex",
                        assessment.sexForFormulas() == null
                                ? null
                                : assessment.sexForFormulas().name())
                .param("authorId", assessment.authorId())
                .param("authorRole", assessment.authorRole().name())
                .param("createdAt", Timestamp.from(assessment.createdAt()))
                .param("sfChest", s.chest())
                .param("sfMidaxillary", s.midaxillary())
                .param("sfTriceps", s.triceps())
                .param("sfSubscapular", s.subscapular())
                .param("sfAbdomen", s.abdomen())
                .param("sfSuprailiac", s.suprailiac())
                .param("sfThigh", s.thigh())
                .param("pollock3", assessment.bodyFat().pollock3())
                .param("pollock7", assessment.bodyFat().pollock7())
                .update();
    }

    @Override
    public Optional<Assessment> findById(UUID id) {
        return jdbc.sql("select " + COLUMNS + " from body_assessment where id = :id")
                .param("id", id)
                .query(JdbcAssessmentRepository::map)
                .optional();
    }

    @Override
    public List<Assessment> listByClient(UUID clientId) {
        return jdbc.sql(
                        "select " + COLUMNS
                                + " from body_assessment where client_id = :clientId order by measured_on desc, created_at desc")
                .param("clientId", clientId)
                .query(JdbcAssessmentRepository::map)
                .list();
    }

    @Override
    public void delete(UUID id) {
        jdbc.sql("delete from body_assessment where id = :id").param("id", id).update();
    }

    private static Assessment map(ResultSet row, int index) throws SQLException {
        String sex = row.getString("sex_for_formulas");
        return new Assessment(
                row.getObject("id", UUID.class),
                row.getObject("client_id", UUID.class),
                Assessment.Type.valueOf(row.getString("assessment_type")),
                row.getDate("measured_on").toLocalDate(),
                row.getBigDecimal("weight_kg"),
                new Circumferences(
                        row.getBigDecimal("neck_cm"),
                        row.getBigDecimal("chest_cm"),
                        row.getBigDecimal("waist_cm"),
                        row.getBigDecimal("abdomen_cm"),
                        row.getBigDecimal("hip_cm"),
                        row.getBigDecimal("arm_cm"),
                        row.getBigDecimal("forearm_cm"),
                        row.getBigDecimal("thigh_cm"),
                        row.getBigDecimal("calf_cm")),
                sex == null ? null : Assessment.Sex.valueOf(sex),
                new Skinfolds(
                        row.getBigDecimal("skinfold_chest_mm"),
                        row.getBigDecimal("skinfold_midaxillary_mm"),
                        row.getBigDecimal("skinfold_triceps_mm"),
                        row.getBigDecimal("skinfold_subscapular_mm"),
                        row.getBigDecimal("skinfold_abdomen_mm"),
                        row.getBigDecimal("skinfold_suprailiac_mm"),
                        row.getBigDecimal("skinfold_thigh_mm")),
                new BodyFat(row.getBigDecimal("pollock3_percent"), row.getBigDecimal("pollock7_percent")),
                row.getObject("author_id", UUID.class),
                Assessment.AuthorRole.valueOf(row.getString("author_role")),
                row.getTimestamp("created_at").toInstant());
    }
}
