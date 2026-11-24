package com.fithub.nutrition.infrastructure.taco;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Importação da Tabela TACO ({@code fithub.foods.import.*}, DEV-0075). Desligada por padrão: liga com
 * {@code fithub.foods.import.enabled=true} e roda na subida do serviço.
 *
 * @param resource o CSV versionado, por exemplo {@code classpath:taco/taco-4a-edicao.csv}
 */
@ConfigurationProperties("fithub.foods.import")
record TacoSettings(boolean enabled, String resource) {}
