package com.fithub.training.infrastructure.persistence;

import com.fithub.training.domain.exercise.Attribution;
import com.fithub.training.domain.exercise.Equipment;
import com.fithub.training.domain.exercise.Exercise;
import com.fithub.training.domain.exercise.ExerciseRepository;
import com.fithub.training.domain.exercise.MuscleGroup;
import java.sql.Timestamp;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Exercícios no Postgres do training-service (migration V3). */
@Repository
class JdbcExerciseRepository implements ExerciseRepository {

    private final JdbcClient jdbc;

    JdbcExerciseRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Grava o exercício e troca os grupos musculares numa transação só. */
    @Override
    @Transactional
    public void save(Exercise exercise) {
        Attribution attribution = exercise.attribution();
        jdbc.sql("""
                        insert into exercise (id, kind, owner_id, name, equipment, instructions, media_url,
                            external_source, external_id, license, license_author, created_at, updated_at)
                        values (:id, :kind, :ownerId, :name, :equipment, :instructions, :mediaUrl,
                            :externalSource, :externalId, :license, :licenseAuthor, :createdAt, :updatedAt)
                        on conflict (id) do update set
                            name = excluded.name,
                            equipment = excluded.equipment,
                            instructions = excluded.instructions,
                            media_url = excluded.media_url,
                            external_source = excluded.external_source,
                            external_id = excluded.external_id,
                            license = excluded.license,
                            license_author = excluded.license_author,
                            updated_at = excluded.updated_at
                        """)
                .param("id", exercise.id())
                .param("kind", exercise.kind().name())
                .param("ownerId", exercise.ownerId())
                .param("name", exercise.name())
                .param("equipment", exercise.equipment().name())
                .param("instructions", exercise.instructions())
                .param("mediaUrl", exercise.mediaUrl())
                .param("externalSource", attribution == null ? null : attribution.source())
                .param("externalId", attribution == null ? null : attribution.externalId())
                .param("license", attribution == null ? null : attribution.license())
                .param("licenseAuthor", attribution == null ? null : attribution.author())
                .param("createdAt", Timestamp.from(exercise.createdAt()))
                .param("updatedAt", Timestamp.from(exercise.updatedAt()))
                .update();
        jdbc.sql("delete from exercise_muscle_group where exercise_id = :id")
                .param("id", exercise.id())
                .update();
        for (MuscleGroup group : exercise.muscleGroups()) {
            jdbc.sql("insert into exercise_muscle_group (exercise_id, muscle_group) values (:id, :group)")
                    .param("id", exercise.id())
                    .param("group", group.name())
                    .update();
        }
    }

    @Override
    public Optional<Exercise> findById(UUID id) {
        Set<MuscleGroup> groups = EnumSet.noneOf(MuscleGroup.class);
        jdbc.sql("select muscle_group from exercise_muscle_group where exercise_id = :id")
                .param("id", id)
                .query(String.class)
                .list()
                .forEach(group -> groups.add(MuscleGroup.valueOf(group)));
        return jdbc.sql("""
                        select id, kind, owner_id, name, equipment, instructions, media_url, external_source,
                            external_id, license, license_author, created_at, updated_at
                        from exercise where id = :id
                        """)
                .param("id", id)
                .query((row, index) -> {
                    String source = row.getString("external_source");
                    return new Exercise(
                            row.getObject("id", UUID.class),
                            Exercise.Kind.valueOf(row.getString("kind")),
                            row.getObject("owner_id", UUID.class),
                            row.getString("name"),
                            groups,
                            Equipment.valueOf(row.getString("equipment")),
                            row.getString("instructions"),
                            row.getString("media_url"),
                            source == null
                                    ? null
                                    : new Attribution(
                                            source,
                                            row.getString("external_id"),
                                            row.getString("license"),
                                            row.getString("license_author")),
                            row.getTimestamp("created_at").toInstant(),
                            row.getTimestamp("updated_at").toInstant());
                })
                .optional();
    }
}
