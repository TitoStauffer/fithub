package com.fithub.training.infrastructure.wger;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Importação da base do wger ({@code fithub.exercises.import.*}, DEV-0054). Desligada por padrão: liga com
 * {@code fithub.exercises.import.enabled=true} e roda na subida do serviço.
 *
 * @param baseUrl raiz da API, por exemplo {@code https://wger.de/api/v2}
 * @param pageSize itens por página pedida
 * @param maxPages trava contra uma paginação que não acaba
 * @param timeout tempo de conexão e de leitura de cada chamada
 */
@ConfigurationProperties("fithub.exercises.import")
record WgerSettings(boolean enabled, String baseUrl, int pageSize, int maxPages, Duration timeout) {}
