package com.fithub.client.application;

import com.fithub.client.domain.link.ProfessionalAccessAudit;
import java.time.Clock;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;

/**
 * Prazo do registro de leituras de dados sensíveis pelos profissionais (DEV-0106, ADR-012, item 6): 6 meses, o mínimo
 * do Marco Civil da Internet (art. 15) para registros de acesso a aplicação. Depois disso, o registro sai.
 */
@Service
public class AccessLogRetention {

    /** Meses que o registro fica guardado. */
    static final int RETENTION_MONTHS = 6;

    private final ProfessionalAccessAudit audit;
    private final Clock clock;

    public AccessLogRetention(ProfessionalAccessAudit audit, Clock clock) {
        this.audit = audit;
        this.clock = clock;
    }

    /** Tira o que passou do prazo; devolve quantos registros saíram. */
    public int purge() {
        return audit.purgeBefore(clock.instant()
                .atZone(ZoneOffset.UTC)
                .minusMonths(RETENTION_MONTHS)
                .toInstant());
    }
}
