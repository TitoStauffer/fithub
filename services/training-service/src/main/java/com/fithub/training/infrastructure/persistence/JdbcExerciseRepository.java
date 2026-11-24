package com.fithub.training.infrastructure.persistence;

import com.fithub.training.domain.exercise.Attribution;
import com.fithub.training.domain.exercise.Equipment;
import com.fithub.training.domain.exercise.Exercise;
import com.fithub.training.domain.exercise.ExerciseRepository;
import com.fithub.training.domain.exercise.ExerciseSearch;
import com.fithub.training.domain.exercise.MuscleGroup;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.EnumSet;
import java.util.List;
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
                            external_source, external_id, license, license_author, media_license, media_author,
                            created_at, updated_at, archived_at)
                        values (:id, :kind, :ownerId, :name, :equipment, :instructions, :mediaUrl,
                            :externalSource, :externalId, :license, :licenseAuthor, :mediaLicense, :mediaAuthor,
                            :createdAt, :updatedAt, :archivedAt)
                        on conflict (id) do update set
                            name = excluded.name,
                            equipment = excluded.equipment,
                            instructions = excluded.instructions,
                            media_url = excluded.media_url,
                            external_source = excluded.external_source,
                            external_id = excluded.external_id,
                            license = excluded.license,
                            license_author = excluded.license_author,
                            media_license = excluded.media_license,
                            media_author = excluded.media_author,
                            updated_at = excluded.updated_at,
                            archived_at = excluded.archived_at
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
                .param("mediaLicense", attribution == null ? null : attribution.mediaLicense())
                .param("mediaAuthor", attribution == null ? null : attribution.mediaAuthor())
                .param("createdAt", Timestamp.from(exercise.createdAt()))
                .param("updatedAt", Timestamp.from(exercise.updatedAt()))
                .param("archivedAt", exercise.archivedAt() == null ? null : Timestamp.from(exercise.archivedAt()))
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

    // Uma consulta só: o exercício e os grupos vêm da mesma fotografia do banco, mesmo com um save concorrente
    // apagando e regravando os grupos.
    private static final String SELECT = """
            select e.id, e.kind, e.owner_id, e.name, e.equipment, e.instructions, e.media_url,
                e.external_source, e.external_id, e.license, e.license_author, e.media_license, e.media_author,
                e.created_at, e.updated_at, e.archived_at, array_remove(array_agg(g.muscle_group), null) as muscle_groups
            from exercise e
            left join exercise_muscle_group g on g.exercise_id = e.id
            """;

    @Override
    public Optional<Exercise> findById(UUID id) {
        return jdbc.sql(SELECT + "where e.id = :id group by e.id")
                .param("id", id)
                .query(JdbcExerciseRepository::toExercise)
                .optional();
    }

    @Override
    public Optional<Exercise> findByExternalId(String source, String externalId) {
        return jdbc.sql(SELECT + "where e.external_source = :source and e.external_id = :externalId group by e.id")
                .param("source", source)
                .param("externalId", externalId)
                .query(JdbcExerciseRepository::toExercise)
                .optional();
    }

    @Override
    public ExerciseSearch.Page search(ExerciseSearch search, UUID personalId) {
        // Visível para quem busca: o catálogo e os próprios dele (BR-13). Filtros ausentes não restringem.
        String matching = """
                from exercise e
                where (e.kind = 'CATALOG' or e.owner_id = :personalId)
                    and e.archived_at is null
                    and (cast(:name as text) is null
                        or e.search_name like '%' || fold_search(cast(:name as text)) || '%' escape '\\')
                    and (cast(:equipment as text) is null or e.equipment = cast(:equipment as text))
                    and (cast(:muscleGroup as text) is null or exists (
                        select 1 from exercise_muscle_group m
                        where m.exercise_id = e.id and m.muscle_group = cast(:muscleGroup as text)))
                """;
        String name = search.name() == null ? null : escapeLike(search.name());
        String equipment =
                search.equipment() == null ? null : search.equipment().name();
        String muscleGroup =
                search.muscleGroup() == null ? null : search.muscleGroup().name();
        long total = jdbc.sql("select count(*) " + matching)
                .param("personalId", personalId)
                .param("name", name)
                .param("equipment", equipment)
                .param("muscleGroup", muscleGroup)
                .query(Long.class)
                .single();
        List<Exercise> items = jdbc.sql("with page as (select e.id " + matching
                        + " order by e.search_name, e.name, e.id limit :size offset :offset) "
                        + SELECT
                        + "where e.id in (select id from page) group by e.id order by e.search_name, e.name, e.id")
                .param("personalId", personalId)
                .param("name", name)
                .param("equipment", equipment)
                .param("muscleGroup", muscleGroup)
                .param("size", search.size())
                .param("offset", (long) search.page() * search.size())
                .query(JdbcExerciseRepository::toExercise)
                .list();
        return new ExerciseSearch.Page(items, total);
    }

    /** O texto buscado é literal: `%` e `_` não viram curinga. */
    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static Exercise toExercise(ResultSet row, int index) throws SQLException {
        Set<MuscleGroup> groups = EnumSet.noneOf(MuscleGroup.class);
        for (String group : (String[]) row.getArray("muscle_groups").getArray()) {
            groups.add(MuscleGroup.valueOf(group));
        }
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
                                row.getString("license_author"),
                                row.getString("media_license"),
                                row.getString("media_author")),
                row.getTimestamp("created_at").toInstant(),
                row.getTimestamp("updated_at").toInstant(),
                row.getTimestamp("archived_at") == null
                        ? null
                        : row.getTimestamp("archived_at").toInstant());
    }
}
