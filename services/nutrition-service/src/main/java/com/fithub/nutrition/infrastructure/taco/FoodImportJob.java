package com.fithub.nutrition.infrastructure.taco;

import com.fithub.nutrition.application.ImportFoods;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Dispara a importação da TACO na subida do serviço, quando ligada (DEV-0075). Roda numa thread à parte, depois que o
 * serviço já está no ar: uma falha não impede a subida nem apaga o que foi importado antes.
 */
@Component
class FoodImportJob {

    private static final Logger log = LoggerFactory.getLogger(FoodImportJob.class);

    private final ImportFoods importFoods;
    private final TacoSettings settings;

    FoodImportJob(ImportFoods importFoods, TacoSettings settings) {
        this.importFoods = importFoods;
        this.settings = settings;
    }

    @EventListener(ApplicationReadyEvent.class)
    void onReady() {
        if (settings.enabled()) {
            Thread.ofVirtual().name("food-import").start(this::runSafely);
        }
    }

    /** Nunca lança: a falha vai para o log, só com o tipo do erro. */
    boolean runSafely() {
        try {
            ImportFoods.Result result = importFoods.run();
            log.info(
                    "Importação da TACO concluída: {} novos, {} atualizados, {} sem mudança, {} pulados {}.",
                    result.created(),
                    result.updated(),
                    result.unchanged(),
                    result.skippedIds().size(),
                    result.skippedIds());
            return true;
        } catch (RuntimeException failure) {
            // Sem a mensagem: a de um erro de banco pode trazer dados da linha.
            log.warn(
                    "Importação da TACO interrompida ({}). O que já estava importado continua.",
                    failure.getClass().getSimpleName());
            return false;
        }
    }
}
