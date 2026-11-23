package com.fithub.client.application;

import com.fithub.client.domain.professional.ProfessionalDirectory;
import com.fithub.client.domain.professional.ProfessionalType;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Mantém a projeção de profissionais a partir do {@code ProfissionalCadastrado} (DEV-0035). */
@Service
public class RecordProfessional {

    private final ProfessionalDirectory professionals;

    public RecordProfessional(ProfessionalDirectory professionals) {
        this.professionals = professionals;
    }

    public void registered(UUID professionalId, ProfessionalType type, String name, Instant occurredAt) {
        professionals.register(professionalId, type, name, occurredAt);
    }
}
