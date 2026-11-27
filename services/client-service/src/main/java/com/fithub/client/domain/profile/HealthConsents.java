package com.fithub.client.domain.profile;

import java.time.Instant;
import java.util.List;

/** Aceites do termo do perfil de saúde (DEV-0179), um por conta e versão (porta do domínio). */
public interface HealthConsents {

    /** Se a conta já aceitou esta versão do termo. */
    boolean accepted(String accountId, String termsVersion);

    /** Um aceite gravado: a versão do termo e o momento. */
    record Accepted(String termsVersion, Instant grantedAt) {}

    /** Os aceites da conta, do mais antigo ao mais novo, para a exportação (DEV-0179). */
    List<Accepted> acceptedBy(String accountId);

    /** Grava o aceite; aceitar de novo a mesma versão não duplica. */
    void accept(String accountId, String termsVersion, Instant grantedAt);
}
