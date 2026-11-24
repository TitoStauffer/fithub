package com.fithub.training.infrastructure.wger;

import com.fithub.training.application.ExerciseSourceUnavailableException;
import com.fithub.training.application.ImportExercises;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Dispara a importação da base do wger na subida do serviço, quando ligada (DEV-0054). Roda numa thread à parte,
 * depois que o serviço já está no ar: a falha da base não impede a subida nem apaga o que foi importado antes.
 */
@Component
class ExerciseImportJob {

    private static final Logger log = LoggerFactory.getLogger(ExerciseImportJob.class);

    private final ImportExercises importExercises;
    private final WgerSettings settings;

    ExerciseImportJob(ImportExercises importExercises, WgerSettings settings) {
        this.importExercises = importExercises;
        this.settings = settings;
    }

    @EventListener(ApplicationReadyEvent.class)
    void onReady() {
        if (settings.enabled()) {
            Thread.ofVirtual().name("exercise-import").start(this::runSafely);
        }
    }

    /** Nunca lança: a falha vai para o log, só com o tipo do erro e a mensagem nossa. */
    boolean runSafely() {
        try {
            ImportExercises.Result result = importExercises.run();
            log.info(
                    "Importação de exercícios concluída: {} novos, {} atualizados, {} sem mudança, {} pulados.",
                    result.created(),
                    result.updated(),
                    result.unchanged(),
                    result.skipped());
            return true;
        } catch (RuntimeException failure) {
            log.warn(
                    "Importação de exercícios interrompida ({}): {}. O que já estava importado continua.",
                    failure.getClass().getSimpleName(),
                    // Só a mensagem que nós escrevemos; a de um erro de banco pode trazer dados da linha.
                    failure instanceof ExerciseSourceUnavailableException ? failure.getMessage() : "erro inesperado");
            return false;
        }
    }
}
