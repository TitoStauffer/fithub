package com.fithub.nutrition.domain.account;

import java.util.List;
import java.util.UUID;

/** Leitura do que o nutrition tem do cliente, para a exportação dos dados dele (porta do domínio, DEV-0167). */
public interface NutritionExportReader {

    /** Ids dos planos alimentares do cliente que já foram entregues (não rascunho), do mais novo para o mais antigo. */
    List<UUID> deliveredPlanIdsOf(UUID clientId);

    /** Ids dos registros de refeição do cliente, do mais novo para o mais antigo. */
    List<UUID> logIdsOf(UUID clientId);
}
