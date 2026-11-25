package com.fithub.nutrition.infrastructure.persistence;

import com.fithub.nutrition.domain.progress.ProgressRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Consultas de evolução da dieta no Postgres do nutrition-service (DEV-0092), sem tabela derivada. */
@Repository
class JdbcProgressRepository implements ProgressRepository {

    /** Filtro dos planos da nutricionista; com o parâmetro vazio, vale todo plano do paciente. */
    private static final String AUTHOR_FILTER =
            " and (cast(:nutritionistId as uuid) is null or p.nutritionist_id = cast(:nutritionistId as uuid))";

    private final JdbcClient jdbc;

    JdbcProgressRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<PublishedPlan> publishedPlans(UUID clientId, UUID nutritionistId) {
        record Row(UUID planId, Instant replacedAt, Instant publishedAt, int meals) {}
        List<Row> rows = jdbc.sql("""
                        select p.id, p.status, p.updated_at, v.published_at,
                            (select count(*) from meal m where m.version_id = v.id) meals
                        from meal_plan p join meal_plan_version v on v.plan_id = p.id
                        where p.client_id = :clientId and p.status <> 'DRAFT' and v.published_at is not null
                        """ + AUTHOR_FILTER + " order by p.id, v.number")
                .param("clientId", clientId)
                .param("nutritionistId", nutritionistId)
                .query((rs, index) -> new Row(
                        rs.getObject("id", UUID.class),
                        "REPLACED".equals(rs.getString("status"))
                                ? rs.getTimestamp("updated_at").toInstant()
                                : null,
                        rs.getTimestamp("published_at").toInstant(),
                        rs.getInt("meals")))
                .list();
        Map<UUID, List<PublishedVersion>> versions = new LinkedHashMap<>();
        Map<UUID, Instant> replaced = new HashMap<>();
        for (Row row : rows) {
            versions.computeIfAbsent(row.planId(), id -> new ArrayList<>())
                    .add(new PublishedVersion(row.publishedAt(), row.meals()));
            replaced.put(row.planId(), row.replacedAt());
        }
        return versions.entrySet().stream()
                .map(entry -> new PublishedPlan(entry.getKey(), replaced.get(entry.getKey()), entry.getValue()))
                .toList();
    }

    @Override
    public List<LoggedMeal> logs(UUID clientId, UUID nutritionistId, LocalDate from, LocalDate to) {
        return jdbc.sql("""
                        select l.logged_on, l.plan_id, l.status
                        from meal_log l join meal_plan p on p.id = l.plan_id
                        where l.client_id = :clientId and l.logged_on between :from and :to
                        """ + AUTHOR_FILTER)
                .param("clientId", clientId)
                .param("nutritionistId", nutritionistId)
                .param("from", from)
                .param("to", to)
                .query((rs, index) -> new LoggedMeal(
                        rs.getObject("logged_on", LocalDate.class),
                        rs.getObject("plan_id", UUID.class),
                        rs.getString("status")))
                .list();
    }

    @Override
    public List<AlternativeUse> topAlternatives(
            UUID clientId, UUID nutritionistId, LocalDate from, LocalDate to, int limit) {
        return jdbc.sql("""
                        select f.id, f.name, count(*) times
                        from meal_log_substitution s
                            join meal_log l on l.id = s.log_id
                            join meal_plan p on p.id = l.plan_id
                            join meal_item_alternative a on a.version_id = l.version_id
                                and a.meal_position = l.meal_position and a.item_position = s.item_position
                                and a.position = s.alternative
                            join food f on f.id = a.food_id
                        where l.client_id = :clientId and l.logged_on between :from and :to
                        """ + AUTHOR_FILTER + """

                        group by f.id, f.name
                        order by times desc, f.name
                        limit :limit
                        """)
                .param("clientId", clientId)
                .param("nutritionistId", nutritionistId)
                .param("from", from)
                .param("to", to)
                .param("limit", limit)
                .query((rs, index) ->
                        new AlternativeUse(rs.getObject("id", UUID.class), rs.getString("name"), rs.getLong("times")))
                .list();
    }

    @Override
    public List<Deviation> deviations(UUID clientId, UUID nutritionistId, LocalDate from, LocalDate to) {
        return jdbc.sql("""
                        select l.id, l.logged_on, l.meal_position, m.name meal_name, l.deviation, l.photo_key
                        from meal_log l
                            join meal_plan p on p.id = l.plan_id
                            join meal m on m.version_id = l.version_id and m.position = l.meal_position
                        where l.client_id = :clientId and l.logged_on between :from and :to
                            and (l.deviation is not null or l.photo_key is not null)
                        """ + AUTHOR_FILTER + " order by l.logged_on desc, l.meal_position")
                .param("clientId", clientId)
                .param("nutritionistId", nutritionistId)
                .param("from", from)
                .param("to", to)
                .query((rs, index) -> new Deviation(
                        rs.getObject("id", UUID.class),
                        rs.getObject("logged_on", LocalDate.class),
                        rs.getInt("meal_position"),
                        rs.getString("meal_name"),
                        rs.getString("deviation"),
                        rs.getString("photo_key")))
                .list();
    }

    @Override
    public Map<UUID, Instant> lastLogged(UUID nutritionistId, Collection<UUID> clientIds) {
        Map<UUID, Instant> last = new HashMap<>();
        if (clientIds.isEmpty()) {
            return last;
        }
        jdbc.sql("""
                        select l.client_id, max(l.created_at) last_logged
                        from meal_log l join meal_plan p on p.id = l.plan_id
                        where p.nutritionist_id = :nutritionistId and l.client_id in (:clientIds)
                        group by l.client_id
                        """)
                .param("nutritionistId", nutritionistId)
                .param("clientIds", clientIds)
                .query(rs -> {
                    last.put(
                            rs.getObject("client_id", UUID.class),
                            rs.getTimestamp("last_logged").toInstant());
                });
        return last;
    }
}
