package com.fithub.client.domain.link;

import com.fithub.client.domain.invitation.SharedData;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Termo de consentimento do vínculo (DEV-0036, FR-11, NFR-01). Versionado no próprio serviço: o aceite exige a
 * versão que a tela mostrou, e mudar o texto exige uma versão nova.
 *
 * <p>Decisão do Agent na ausência do Tito, para revisão: começa em {@code v1}, com o escopo do BR-04, e não se
 * grava IP nem aparelho (minimização).
 */
public final class ConsentTerms {

    public static final String CURRENT_VERSION = "v1";

    /** Escopo do BR-04: o profissional vinculado vê o perfil, a saúde, as avaliações e as fotos. */
    public static final Set<SharedData> SCOPE = Collections.unmodifiableSet(EnumSet.allOf(SharedData.class));

    public static final String CURRENT_TEXT = """
            Ao aceitar, você autoriza este profissional a ver, enquanto o vínculo estiver ativo: seu perfil (nome, \
            data de nascimento, gênero, altura e objetivo), seu perfil de saúde, suas avaliações corporais e suas \
            fotos de evolução. Você pode encerrar o vínculo a qualquer momento; o profissional perde o acesso na \
            hora. Você continua dono dos seus dados.""";

    private ConsentTerms() {}

    public static boolean isCurrent(String version) {
        return CURRENT_VERSION.equals(version);
    }
}
