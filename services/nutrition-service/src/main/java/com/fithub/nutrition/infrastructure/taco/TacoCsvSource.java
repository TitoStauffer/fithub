package com.fithub.nutrition.infrastructure.taco;

import com.fithub.nutrition.application.FoodCatalogSource;
import com.fithub.nutrition.domain.food.Food;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.springframework.core.io.Resource;

/**
 * A Tabela TACO (4ª edição, NEPA/UNICAMP, 2011) a partir do CSV versionado no serviço (DEV-0075, ADR-011). O CSV traz
 * os valores como a planilha oficial publica, com os códigos da legenda dela, e a etiqueta vegano curada item a item.
 */
public class TacoCsvSource implements FoodCatalogSource {

    public static final String NAME = "TACO 4ª edição (NEPA/UNICAMP, 2011)";

    private static final List<String> HEADER = List.of(
            "taco_number",
            "name",
            "category",
            "base_unit",
            "energy_kcal",
            "protein_g",
            "carbs_g",
            "fat_g",
            "fiber_g",
            "vegan");

    private final Resource csv;

    public TacoCsvSource(Resource csv) {
        this.csv = csv;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void forEach(Consumer<Entry> consumer) {
        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(csv.getInputStream(), StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (header == null || !fields(header).equals(HEADER)) {
                throw new IllegalStateException("O CSV da TACO não tem o cabeçalho esperado.");
            }
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                if (!line.isBlank()) {
                    consumer.accept(entry(fields(line)));
                }
            }
        } catch (IOException failure) {
            throw new UncheckedIOException("Não foi possível ler o CSV da TACO.", failure);
        }
    }

    private static Entry entry(List<String> row) {
        return new Entry(
                row.get(0),
                row.get(1),
                Food.BaseUnit.valueOf(row.get(3)),
                value(row.get(4)),
                value(row.get(5)),
                carbsByDifference(value(row.get(6))),
                value(row.get(7)),
                value(row.get(8)),
                Boolean.parseBoolean(row.get(9)));
    }

    /**
     * Valor pela legenda da planilha oficial: "Tr" (traço, entre 0 e 0,5) e "NA" (não aplicável) valem 0; "*" (análise
     * em reavaliação) e vazio (análise não solicitada) ficam ausentes.
     */
    static BigDecimal value(String raw) {
        String value = raw.strip();
        return switch (value) {
            case "Tr", "NA" -> BigDecimal.ZERO;
            case "*", "" -> null;
            default -> new BigDecimal(value);
        };
    }

    /**
     * Na TACO o carboidrato é calculado por diferença (100 menos umidade, proteína, lipídeos e cinzas); o erro analítico
     * deixa alguns itens um pouco abaixo de zero (até -0,05 g). Carboidrato por diferença negativo vale 0.
     */
    static BigDecimal carbsByDifference(BigDecimal carbs) {
        return carbs != null && carbs.signum() < 0 ? BigDecimal.ZERO : carbs;
    }

    /** Campos de uma linha CSV, com aspas duplas em volta do campo que tem vírgula (e "" para a aspa). */
    static List<String> fields(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);
            if (quoted && character == '"' && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                current.append('"');
                index++;
            } else if (character == '"') {
                quoted = !quoted;
            } else if (character == ',' && !quoted) {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(character);
            }
        }
        fields.add(current.toString());
        return fields;
    }
}
