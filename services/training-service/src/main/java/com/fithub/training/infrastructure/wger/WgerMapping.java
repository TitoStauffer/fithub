package com.fithub.training.infrastructure.wger;

import com.fithub.training.domain.exercise.Equipment;
import com.fithub.training.domain.exercise.Exercise;
import com.fithub.training.domain.exercise.MuscleGroup;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Tradução dos ids do wger para os grupos musculares e equipamentos do FitHub (DEV-0054), versionada no código. Os
 * ids são os da API pública ({@code /muscle/}, {@code /equipment/}, {@code /exercisecategory/}, {@code /language/})
 * em 24/11/2026.
 *
 * <p>Decisões do Agent na ausência do Tito, para revisão: o grupo vem dos músculos principais; sem nenhum mapeável,
 * da categoria (braços e pernas são amplos demais e não valem); sem equipamento, é peso do corpo; com vários, fica
 * o mais específico.
 */
final class WgerMapping {

    private WgerMapping() {}

    static final long PORTUGUESE = 7;
    static final long ENGLISH = 2;

    static final Map<Long, MuscleGroup> MUSCLES = Map.ofEntries(
            Map.entry(1L, MuscleGroup.BICEPS), // Biceps brachii
            Map.entry(2L, MuscleGroup.SHOULDERS), // Anterior deltoid
            Map.entry(3L, MuscleGroup.CHEST), // Serratus anterior
            Map.entry(4L, MuscleGroup.CHEST), // Pectoralis major
            Map.entry(5L, MuscleGroup.TRICEPS), // Triceps brachii
            Map.entry(6L, MuscleGroup.ABS), // Rectus abdominis
            Map.entry(7L, MuscleGroup.CALVES), // Gastrocnemius
            Map.entry(8L, MuscleGroup.GLUTES), // Gluteus maximus
            Map.entry(9L, MuscleGroup.BACK), // Trapezius
            Map.entry(10L, MuscleGroup.QUADRICEPS), // Quadriceps femoris
            Map.entry(11L, MuscleGroup.HAMSTRINGS), // Biceps femoris
            Map.entry(12L, MuscleGroup.BACK), // Latissimus dorsi
            Map.entry(13L, MuscleGroup.BICEPS), // Brachialis
            Map.entry(14L, MuscleGroup.ABS), // Obliquus externus abdominis
            Map.entry(15L, MuscleGroup.CALVES)); // Soleus

    /** Categoria usada só quando nenhum músculo principal é mapeável. */
    static final Map<Long, MuscleGroup> CATEGORIES = Map.of(
            10L, MuscleGroup.ABS, // Abs
            11L, MuscleGroup.CHEST, // Chest
            12L, MuscleGroup.BACK, // Back
            13L, MuscleGroup.SHOULDERS, // Shoulders
            14L, MuscleGroup.CALVES, // Calves
            15L, MuscleGroup.FULL_BODY); // Cardio

    static final Map<Long, Equipment> EQUIPMENT = Map.ofEntries(
            Map.entry(1L, Equipment.BARBELL), // Barbell
            Map.entry(2L, Equipment.BARBELL), // SZ-Bar
            Map.entry(3L, Equipment.DUMBBELL), // Dumbbell
            Map.entry(4L, Equipment.BODYWEIGHT), // Gym mat
            Map.entry(5L, Equipment.OTHER), // Swiss Ball
            Map.entry(6L, Equipment.BODYWEIGHT), // Pull-up bar
            Map.entry(7L, Equipment.BODYWEIGHT), // none (bodyweight exercise)
            Map.entry(8L, Equipment.OTHER), // Bench
            Map.entry(9L, Equipment.OTHER), // Incline bench
            Map.entry(10L, Equipment.KETTLEBELL), // Kettlebell
            Map.entry(11L, Equipment.BAND), // Resistance band
            Map.entry(12L, Equipment.CABLE)); // Cable machine

    /** Do mais específico para o mais genérico: com barra e banco, o exercício é de barra. */
    private static final List<Equipment> EQUIPMENT_PRIORITY = List.of(
            Equipment.BARBELL,
            Equipment.DUMBBELL,
            Equipment.KETTLEBELL,
            Equipment.CABLE,
            Equipment.MACHINE,
            Equipment.BAND,
            Equipment.OTHER,
            Equipment.BODYWEIGHT);

    private static final Pattern TAG = Pattern.compile("<[^>]*>");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    static Set<MuscleGroup> muscleGroups(List<Long> muscleIds, Long categoryId) {
        Set<MuscleGroup> groups = EnumSet.noneOf(MuscleGroup.class);
        if (muscleIds != null) {
            muscleIds.stream().map(MUSCLES::get).filter(Objects::nonNull).forEach(groups::add);
        }
        if (groups.isEmpty() && categoryId != null && CATEGORIES.containsKey(categoryId)) {
            groups.add(CATEGORIES.get(categoryId));
        }
        return groups;
    }

    static Equipment equipment(List<Long> equipmentIds) {
        if (equipmentIds == null || equipmentIds.isEmpty()) {
            return Equipment.BODYWEIGHT;
        }
        Set<Equipment> found = EnumSet.noneOf(Equipment.class);
        equipmentIds.forEach(id -> found.add(EQUIPMENT.getOrDefault(id, Equipment.OTHER)));
        return EQUIPMENT_PRIORITY.stream().filter(found::contains).findFirst().orElse(Equipment.OTHER);
    }

    /**
     * Texto puro da descrição em HTML do wger: sem tags, com as entidades comuns resolvidas e cortado no limite do
     * domínio.
     */
    static String plainText(String html) {
        if (html == null) {
            return null;
        }
        String text = TAG.matcher(html)
                .replaceAll(" ")
                .replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&amp;", "&");
        text = SPACES.matcher(text).replaceAll(" ").strip();
        if (text.length() > Exercise.MAX_INSTRUCTIONS_LENGTH) {
            int cut = Exercise.MAX_INSTRUCTIONS_LENGTH - 1;
            // Não corta um caractere de dois code units (emoji) ao meio.
            if (Character.isHighSurrogate(text.charAt(cut - 1))) {
                cut--;
            }
            text = text.substring(0, cut).strip() + "…";
        }
        return text.isEmpty() ? null : text;
    }
}
